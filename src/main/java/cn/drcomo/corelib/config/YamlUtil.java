package cn.drcomo.corelib.config;

import org.bukkit.plugin.Plugin;
import cn.drcomo.corelib.util.DebugUtil;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.ClosedWatchServiceException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 工具类：管理 Bukkit 插件的 YAML 配置文件。
 * 提供目录创建、JAR 内资源复制、配置加载/重载/保存、读取带默认值的方法，以及文件变动监听。
 *
 * <h3>配置自动补全机制</h3>
 * 当调用 {@link #loadConfig(String)}、{@link #reloadConfig(String)} 或 {@link #loadAllConfigsInFolder(String)} 时，
 * 会自动将用户配置文件与 JAR 内的默认配置进行比对：
 * <ul>
 *   <li>若发现用户配置缺失某些键，会自动补全并输出 WARN 级别日志</li>
 *   <li>补全后的配置会被标记为 dirty，可通过 {@link #saveAllDirtyConfigs()} 持久化</li>
 *   <li>适用于插件版本更新后新增配置键的场景，服务器管理员可在控制台看到哪些配置被自动添加</li>
 * </ul>
 *
 * <h3>与消息颜色预解析的协同</h3>
 * · 若语言/文本模板中使用了 &lt;gradient:...&gt;、&lt;color:...&gt;、&amp;#RRGGBB 等标签，建议在配置加载或重载完成后，
 *   由业务侧遍历相关键并调用 ColorUtil.translateColors 进行一次性"颜色预解析"，将结果缓存起来（例如 Map&lt;String,String&gt; precolored）。
 * · 在 {@link #reloadConfig(String)} 或 {@link #watchConfig(String, java.util.function.Consumer)} 回调中触发上述预解析，
 *   可避免在每次消息发送阶段重复进行正则匹配与逐字符渐变插色，降低运行期开销。
 */
public class YamlUtil {
    private final Plugin plugin;
    private final DebugUtil logger;
    private final Map<String, YamlConfiguration> configs = new HashMap<>();
    private final Set<String> dirtyConfigs = new HashSet<>(); // 记录被修改过的配置
    private final String jarPath;

    /**
     * configKey 到实际文件相对路径的映射表。
     * <p>用于支持 loadAllConfigsInFolder 加载子目录文件时，保持 API 返回文件名的同时，
     * 内部正确记录完整路径以便 saveConfig 时能定位到正确的文件。</p>
     * <p>例如：{"zh_CN" -> "languages/zh_CN", "config" -> "config"}</p>
     */
    private final Map<String, String> configKeyToPath = new HashMap<>();

    /** 共享的文件监听服务与线程，以及相关映射 */
    private WatchService sharedWatcher;
    private Thread sharedWatcherThread;
    private final Map<WatchKey, Path> watchKeyMap = new HashMap<>();
    private final Map<Path, String> watchedFileMap = new HashMap<>();
    private final Map<String, Consumer<YamlConfiguration>> callbackMap = new HashMap<>();

    /** JAR 条目缓存：记录各目录下的条目列表 */
    private final Map<String, List<JarEntry>> jarEntryCache = new HashMap<>();

    /** 默认配置文件名 */
    private static final String DEFAULT_FILE = "config";

    /**
     * 构造函数
     * @param plugin 插件实例
     * @param logger DebugUtil 实例，用于日志输出
     */
    public YamlUtil(Plugin plugin, DebugUtil logger) {
        this.plugin = plugin;
        this.logger = logger;
        String path;
        try {
            path = plugin.getClass()
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()
                    .getPath();
        } catch (Exception e) {
            path = "";
            logger.error("获取 JAR 路径失败", e);
        }
        this.jarPath = path;
    }

    // ======================= 目录与资源复制 =======================

    /**
     * 确保插件数据文件夹下的指定目录存在
     * @param relativePath 相对路径
     */
    public void ensureDirectory(String relativePath) {
        File dir = new File(plugin.getDataFolder(), relativePath);
        if (!dir.exists()) {
            logger.info("创建目录: " + dir.getPath());
            if (dir.mkdirs()) {
                logger.info("目录创建成功: " + dir.getPath());
            } else {
                logger.error("目录创建失败: " + dir.getPath());
            }
        } else {
            logger.debug("目录已存在: " + dir.getPath());
        }
    }

    /**
     * 从插件 JAR 内指定资源文件夹复制所有 .yml 文件到数据文件夹，仅在目标文件不存在时才复制
     * @param resourceFolder 资源文件夹路径，如 "config" 或 ""
     * @param relativePath   目标相对路径
     */
    public void copyDefaults(String resourceFolder, String relativePath) {
        String folder = normalizeFolder(resourceFolder);
        ensureDirectory(relativePath);
        try {
            traverseJar(folder, (entry, jar) -> {
                if (!entry.isDirectory() && entry.getName().endsWith(".yml")) {
                    String subPath = entry.getName().substring(folder.length());
                    File dest = new File(plugin.getDataFolder(),
                            relativePath + File.separator + subPath.replace("/", File.separator));
                    ensureParentDir(dest);
                    if (!dest.exists()) {
                        logger.debug("复制默认配置: " + entry.getName() + " -> " + dest.getPath());
                        copyResourceToFile(entry.getName(), dest);
                    } else {
                        logger.debug("跳过已存在文件: " + dest.getPath());
                    }
                }
            });
        } catch (Exception e) {
            logger.error("复制默认文件失败: " + resourceFolder, e);
        }
    }

    /**
     * 复制插件 JAR 内指定单个 .yml 文件到数据文件夹相对目录，若目标已存在则跳过
     * @param resourcePath JAR 内资源完整路径，如 "config/example.yml"
     * @param relativePath 目标目录相对路径
     */
    public void copyYamlFile(String resourcePath, String relativePath) {
        if (resourcePath == null || !resourcePath.endsWith(".yml")) {
            logger.warn("资源路径无效或非 yml 文件: " + resourcePath);
            return;
        }
        ensureDirectory(relativePath);
        String fileName = new File(resourcePath).getName();
        File dest = new File(plugin.getDataFolder(), relativePath + File.separator + fileName);
        if (dest.exists()) {
            logger.debug("跳过已有文件: " + dest.getPath());
        } else {
            logger.debug("复制单个配置: " + resourcePath + " -> " + dest.getPath());
            ensureParentDir(dest);
            copyResourceToFile(resourcePath, dest);
        }
    }

    /**
     * 确保目录存在并从 JAR 复制默认文件，支持排除指定文件
     * @param resourceFolder JAR 内资源文件夹路径
     * @param relativePath   目标目录相对路径
     * @param excludedNames  排除的文件名列表（支持通配符）
     */
    public void ensureFolderAndCopyDefaults(String resourceFolder, String relativePath, String... excludedNames) {
        File targetDir = new File(plugin.getDataFolder(), relativePath);
        if (targetDir.exists()) {
            logger.debug("目标目录已存在，跳过初始化: " + targetDir.getPath());
            return;
        }
        ensureDirectory(relativePath);
        String folder = normalizeFolder(resourceFolder);

        // 构建排除列表
        Set<String> excludeSet = new HashSet<>();
        excludeSet.add("plugin.yml");
        excludeSet.add("*.sql");
        if (excludedNames != null) {
            for (String ex : excludedNames) {
                if (ex != null && !ex.trim().isEmpty()) {
                    excludeSet.add(ex.trim());
                }
            }
        }

        try {
            traverseJar(folder, (entry, jar) -> {
                if (!entry.isDirectory()) {
                    String entryName = entry.getName();
                    String fileName = entryName.substring(entryName.lastIndexOf('/') + 1);
                    // 检查排除
                    boolean shouldExclude = excludeSet.stream().anyMatch(pattern -> {
                        if (pattern.contains("*") || pattern.contains("?")) {
                            String regex = pattern.replace("*", ".*").replace("?", ".");
                            return fileName.matches(regex);
                        } else {
                            return fileName.equals(pattern);
                        }
                    });
                    if (!shouldExclude) {
                        String subPath = entryName.substring(folder.length());
                        File dest = new File(targetDir, subPath.replace("/", File.separator));
                        ensureParentDir(dest);
                        if (!dest.exists()) {
                            logger.debug("初始化复制: " + entryName + " -> " + dest.getPath());
                            copyResourceToFile(entryName, dest);
                        }
                    } else {
                        logger.debug("跳过排除文件: " + entryName);
                    }
                }
            });
        } catch (Exception e) {
            logger.error("初始化复制文件夹失败: " + resourceFolder, e);
        }
    }

    /**
     * @deprecated 此方法为每个监听器创建新线程，效率低下。请迁移到 {@link #ensureFolderAndCopyDefaults(String, String, String...)}
     */
    @Deprecated
    public void ensureFolderAndCopyDefaults(String resourceFolder, String relativePath) {
        // 调用新方法，功能等同
        ensureFolderAndCopyDefaults(resourceFolder, relativePath, new String[0]);
    }

    // ======================= 配置加载与保存 =======================

    /**
     * 加载指定配置文件到缓存，并与 JAR 内默认配置比对，自动补全缺失的键
     * <p>支持以下调用方式：
     * <ul>
     *   <li>{@code loadConfig("config")} - 加载根目录下的 config.yml</li>
     *   <li>{@code loadConfig("languages/zh_CN")} - 加载子目录下的 zh_CN.yml</li>
     * </ul>
     * </p>
     * @param fileName 文件名或相对路径（不含 .yml），如 "config" 或 "languages/zh_CN"
     */
    public void loadConfig(String fileName) {
        // 规范化路径分隔符
        String normalizedName = normalizeConfigKey(fileName);
        if (normalizedName.isEmpty()) {
            logger.warn("loadConfig 收到空文件名，已跳过");
            return;
        }
        
        // 确保父目录存在
        int lastSlash = normalizedName.lastIndexOf('/');
        if (lastSlash > 0) {
            ensureDirectory(normalizedName.substring(0, lastSlash));
        } else {
            ensureDirectory("");
        }
        
        File file = getConfigFile(normalizedName);
        if (!file.exists()) {
            logger.debug("未找到配置，将从 JAR 中复制默认文件: " + normalizedName + ".yml");
            // saveResource 需要使用 / 分隔符
            plugin.saveResource(normalizedName + ".yml", false);
        }
        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

            // 与 JAR 内默认配置比对，补全缺失键
            boolean hasNewKeys = mergeDefaultsFromJar(cfg, normalizedName);
            if (hasNewKeys) {
                dirtyConfigs.add(normalizedName);
            }

            configs.put(normalizedName, cfg);
            // 注册路径映射（自身映射到自身，确保 getConfigFile 能正确处理）
            configKeyToPath.put(normalizedName, normalizedName);
            logger.debug("Loaded config: " + normalizedName);
        } catch (Exception e) {
            logger.error("加载配置失败: " + normalizedName, e);
        }
    }

    /**
     * 从 JAR 内读取默认配置，与用户配置比对，补全缺失的键并输出警告
     * @param userCfg 用户配置
     * @param fileName 文件名（不含 .yml），可包含路径如 "languages/zh_CN"
     * @return 是否有新键被补全
     */
    private boolean mergeDefaultsFromJar(YamlConfiguration userCfg, String fileName) {
        // 确保资源路径始终使用 '/'，即使 fileName 可能来自 Windows 风格的输入
        String resourcePath = normalizeConfigKey(fileName) + ".yml";
        try (InputStream defaultStream = plugin.getResource(resourcePath)) {
            if (defaultStream == null) {
                logger.debug("JAR 内未找到默认配置: " + resourcePath + "，跳过比对");
                return false;
            }

            YamlConfiguration defaultCfg = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultStream, StandardCharsets.UTF_8)
            );

            boolean hasChanges = false;
            for (String key : defaultCfg.getKeys(true)) {
                // 跳过 ConfigurationSection，只处理叶子节点
                if (defaultCfg.get(key) instanceof ConfigurationSection) {
                    continue;
                }

                if (!userCfg.contains(key)) {
                    Object value = defaultCfg.get(key);
                    userCfg.set(key, value);
                    logger.warn("配置键缺失已自动补全: [" + resourcePath + "] " + key + " = " + value);
                    hasChanges = true;
                }
            }

            return hasChanges;
        } catch (IOException e) {
            logger.error("读取 JAR 内默认配置失败: " + resourcePath, e);
            return false;
        }
    }

    /**
     * 扫描目录并加载所有 .yml 文件，并与 JAR 内默认配置比对，自动补全缺失的键
     * <p>
     * 返回的 Map 以<b>文件名</b>为键（如 {@code "zh_CN"}），保持向后兼容。
     * 内部会自动记录文件的完整路径，后续调用 {@link #saveConfig(String)} 时会自动定位到正确的文件。
     * </p>
     * <p><b>注意</b>：如果不同子目录下存在同名文件，后加载的会覆盖先加载的。
     * 若需要区分，请使用 {@link #loadConfig(String)} 并传入完整相对路径（如 {@code "languages/zh_CN"}）。</p>
     *
     * @param folderPath 相对数据文件夹的目录路径
     * @return 文件名 -&gt; 配置对象映射
     */
    public Map<String, YamlConfiguration> loadAllConfigsInFolder(String folderPath) {
        Map<String, YamlConfiguration> map = new HashMap<>();
        // 规范化路径分隔符为正斜杠，确保内部路径格式统一
        String normalizedFolder = normalizeConfigKey(folderPath);
        File dir = new File(plugin.getDataFolder(), normalizedFolder);
        if (!dir.exists() || !dir.isDirectory()) {
            logger.warn("目录不存在: " + dir.getPath());
            return map;
        }
        File[] files = dir.listFiles((d, name) -> name.endsWith(".yml"));
        if (files == null) return map;
        for (File file : files) {
            String name = file.getName().replaceFirst("\\.yml$", "");
            // 计算完整相对路径，用于内部存储和 JAR 资源比对
            String fullPath = normalizedFolder.isEmpty() ? name : normalizedFolder + "/" + name;
            try {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

                // 与 JAR 内默认配置比对，补全缺失键
                boolean hasNewKeys = mergeDefaultsFromJar(cfg, fullPath);
                if (hasNewKeys) {
                    dirtyConfigs.add(name);
                }

                // 使用文件名作为 configKey（保持向后兼容），但记录完整路径用于保存
                configs.put(name, cfg);
                configKeyToPath.put(name, fullPath);
                map.put(name, cfg);
                logger.debug("Loaded config: " + file.getPath() + " (key=" + name + ", path=" + fullPath + ")");
            } catch (Exception e) {
                logger.error("加载配置失败: " + file.getPath(), e);
            }
        }
        return map;
    }

    /**
     * 重载指定配置文件，并与 JAR 内默认配置比对，自动补全缺失的键
     * <p>支持传入文件名或完整相对路径（如 "zh_CN" 或 "languages/zh_CN"）。
     * 如果传入的是通过 {@link #loadAllConfigsInFolder(String)} 加载的文件名，
     * 会自动解析为正确的文件路径。</p>
     * @param fileName 文件名或相对路径（不含 .yml）
     */
    public void reloadConfig(String fileName) {
        String key = normalizeConfigKey(fileName);
        logger.info("重载配置开始: " + key);
        // 获取实际路径用于 JAR 资源比对
        String actualPath = configKeyToPath.getOrDefault(key, key);
        File file = getConfigFile(key);
        if (file.exists()) {
            try {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

                // 与 JAR 内默认配置比对，补全缺失键（使用实际路径）
                boolean hasNewKeys = mergeDefaultsFromJar(cfg, actualPath);
                if (hasNewKeys) {
                    dirtyConfigs.add(key);
                }

                configs.put(key, cfg);
                logger.info("重载配置完成: " + key);
                // 提示：如该配置承载了消息模板（含颜色标签），此处重载后应在业务层触发一次颜色预解析与缓存，
                // 以减少后续消息发送阶段的解析成本。
            } catch (Exception e) {
                logger.error("重载配置失败: " + key, e);
            }
        } else {
            logger.warn("重载时未找到配置文件: " + key + " (实际路径: " + file.getPath() + ")");
        }
    }

    /**
     * 保存指定配置文件（若标记为脏或强制保存）
     * @param fileName 文件名（不含 .yml）
     */
    public void saveConfig(String fileName) {
        saveConfig(fileName, false);
    }

    /**
     * 保存指定配置文件
     * @param fileName 文件名（不含 .yml）
     * @param force    是否强制保存
     */
    public void saveConfig(String fileName, boolean force) {
        String key = normalizeConfigKey(fileName);
        if (!force && !dirtyConfigs.contains(key)) {
            return;
        }
        YamlConfiguration cfg = configs.get(key);
        if (cfg == null) {
            logger.warn("无可保存配置: " + key);
            return;
        }
        try {
            cfg.save(getConfigFile(key));
            dirtyConfigs.remove(key);
            logger.debug("Saved config: " + key);
        } catch (IOException e) {
            logger.error("保存配置失败: " + key, e);
        }
    }

    /**
     * 保存所有已修改的配置文件，建议在插件 onDisable 时调用
     */
    public void saveAllDirtyConfigs() {
        if (dirtyConfigs.isEmpty()) {
            return;
        }
        logger.info("正在保存 " + dirtyConfigs.size() + " 个已修改的配置文件...");
        for (String fileName : new HashSet<>(dirtyConfigs)) {
            saveConfig(fileName, true);
        }
        logger.info("所有已修改的配置文件保存完毕。");
    }

    /**
     * 获取配置实例，若未加载则先加载
     * @param fileName 文件名（不含 .yml）
     * @return YamlConfiguration 对象
     */
    public YamlConfiguration getConfig(String fileName) {
        String key = normalizeConfigKey(fileName);
        if (!configs.containsKey(key)) {
            loadConfig(key);
        }
        return configs.get(key);
    }

    // ======================= 配置读取与写入 =======================

    public String getString(String fileName, String path, String def) {
        YamlConfiguration cfg = getConfig(fileName);
        setDefaultIfAbsent(cfg, fileName, path, def);
        return cfg.getString(path, def);
    }

    public int getInt(String fileName, String path, int def) {
        YamlConfiguration cfg = getConfig(fileName);
        setDefaultIfAbsent(cfg, fileName, path, def);
        return cfg.getInt(path, def);
    }

    public boolean getBoolean(String fileName, String path, boolean def) {
        YamlConfiguration cfg = getConfig(fileName);
        setDefaultIfAbsent(cfg, fileName, path, def);
        return cfg.getBoolean(path, def);
    }

    public double getDouble(String fileName, String path, double def) {
        YamlConfiguration cfg = getConfig(fileName);
        setDefaultIfAbsent(cfg, fileName, path, def);
        return cfg.getDouble(path, def);
    }

    public long getLong(String fileName, String path, long def) {
        YamlConfiguration cfg = getConfig(fileName);
        setDefaultIfAbsent(cfg, fileName, path, def);
        return cfg.getLong(path, def);
    }

    public List<String> getStringList(String fileName, String path, List<String> def) {
        YamlConfiguration cfg = getConfig(fileName);
        setDefaultIfAbsent(cfg, fileName, path, def);
        return cfg.getStringList(path);
    }

    public <T> T getValue(String path, Class<T> type, T defaultValue) {
        YamlConfiguration cfg = getConfig(DEFAULT_FILE);
        Object val = cfg.get(path);
        if (val == null || !type.isInstance(val)) {
            cfg.set(path, defaultValue);
            dirtyConfigs.add(DEFAULT_FILE);
            logger.debug("Set typed default: " + path + " = " + defaultValue);
            return defaultValue;
        }
        return type.cast(val);
    }

    public void setValue(String fileName, String path, Object value) {
        String key = normalizeConfigKey(fileName);
        YamlConfiguration cfg = getConfig(key);
        cfg.set(path, value);
        dirtyConfigs.add(key);
        logger.debug("Set value: " + path + " = " + value + " in " + key);
    }

    public boolean contains(String fileName, String path) {
        boolean exists = getConfig(fileName).contains(path);
        logger.debug("Contains check: " + path + " in " + fileName + " = " + exists);
        return exists;
    }

    public Set<String> getKeys(String fileName, String path) {
        ConfigurationSection sec = getConfig(fileName).getConfigurationSection(path);
        Set<String> keys = sec != null ? sec.getKeys(false) : new HashSet<>();
        logger.debug("Keys retrieved: " + keys.size() + " in " + path + " of " + fileName);
        return keys;
    }

    public ConfigurationSection getSection(String fileName, String path) {
        ConfigurationSection sec = getConfig(fileName).getConfigurationSection(path);
        logger.debug("Section retrieved: " + path + " exists=" + (sec != null));
        return sec;
    }

    public void setDefaults(String configKey, Map<String, Object> defaults) {
        if (defaults == null || defaults.isEmpty()) {
            return;
        }
        YamlConfiguration cfg = getConfig(configKey);
        for (Map.Entry<String, Object> e : defaults.entrySet()) {
            setDefaultIfAbsent(cfg, configKey, e.getKey(), e.getValue());
        }
    }

    public ValidationResult validateConfig(String configKey, ConfigSchema schema) {
        ConfigValidator validator = new ConfigValidator(this, logger);
        schema.configure(validator);
        return validator.validate(getConfig(configKey));
    }

    // ======================= 文件监听 =======================

    /**
     * @deprecated 此方法为每个监听器创建新线程，效率低下。请迁移到 {@link #watchConfig(String, Consumer)}
     */
    @Deprecated
    public ConfigWatchHandle watchConfig(String configName,
                                         Consumer<YamlConfiguration> onChange,
                                         ExecutorService executor,
                                         WatchEvent.Kind<?>... kinds) {
        logger.warn("正在调用已弃用的 watchConfig 方法。executor 和 kinds 参数将被忽略。请迁移到新的 watchConfig(String, Consumer) 方法。");
        return watchConfig(configName, onChange);
    }

    /**
     * 监听指定配置文件，一旦修改则自动重载并触发回调
     * @param configName 配置文件名（不含 .yml）
     * @param onChange   回调函数，参数为最新的 YamlConfiguration
     * @return ConfigWatchHandle，用于停止监听
     */
    public ConfigWatchHandle watchConfig(String configName, Consumer<YamlConfiguration> onChange) {
        String key = normalizeConfigKey(configName);
        try {
            startWatcherThread();
        } catch (IOException e) {
            logger.error("无法初始化文件监听服务", e);
            return null;
        }

        File configFile = getConfigFile(key);
        Path filePath = configFile.toPath();
        Path dirPath = filePath.getParent();

        // 注册目录监听
        try {
            registerWatchDirectory(dirPath);
        } catch (IOException e) {
            logger.error("监听目录失败: " + dirPath, e);
            return null;
        }

        watchedFileMap.put(filePath, key);
        callbackMap.put(key, onChange);
        logger.info("已设置对 " + configFile.getName() + " 的修改监听。");
        return new ConfigWatchHandle(this, key);
    }

    /**
     * 停止监听指定配置文件
     * @param configName 配置文件名（不含 .yml）
     */
    public void stopWatching(String configName) {
        String key = normalizeConfigKey(configName);
        callbackMap.remove(key);
        watchedFileMap.entrySet().removeIf(e -> e.getValue().equals(key));
        logger.info("已停止监听配置文件: " + key);
    }

    /**
     * 开启文件变动监听，触发自定义 FileChangeListener
     */
    public void enableFileWatcher(String configKey, FileChangeListener listener) {
        String key = normalizeConfigKey(configKey);
        watchConfig(key, cfg -> {
            if (listener != null) {
                listener.onChange(key, FileChangeType.MODIFY, cfg);
            }
        });
    }

    /**
     * 关闭文件变动监听
     */
    public void disableFileWatcher(String configKey) {
        stopWatching(configKey);
    }

    /**
     * 停止并关闭所有文件监听
     *
     * @deprecated 请改用 {@link #close()}，其会额外等待监听线程结束
     */
    @Deprecated
    public void stopAllWatches() {
        close();
    }

    /**
     * 关闭所有由 YamlUtil 创建的资源
     * <p>必须在插件卸载时调用，以防止文件监听线程与 WatchService 未释放造成资源泄漏。</p>
     */
    public void close() {
        if (sharedWatcherThread != null) {
            sharedWatcherThread.interrupt();
            try {
                sharedWatcherThread.join();
                logger.debug("文件监听线程已结束");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("等待监听线程结束时被中断", e);
            }
            sharedWatcherThread = null;
        }
        if (sharedWatcher != null) {
            try {
                sharedWatcher.close();
                logger.debug("WatchService 已关闭");
            } catch (IOException e) {
                logger.error("关闭 WatchService 失败", e);
            }
            sharedWatcher = null;
        }
        watchKeyMap.clear();
        watchedFileMap.clear();
        callbackMap.clear();
        clearJarCache();
        logger.info("YamlUtil 资源已关闭");
    }

    /**
     * 清空 JAR 条目缓存，释放内存
     */
    public void clearJarCache() {
        jarEntryCache.clear();
    }

    // ======================= 内部类 =======================

    /**
     * 监听句柄，用于停止对单个配置文件的监听
     */
    public class ConfigWatchHandle implements AutoCloseable {
        private final YamlUtil self;
        private final String configName;
        private boolean closed = false;

        private ConfigWatchHandle(YamlUtil self, String configName) {
            this.self = self;
            this.configName = configName;
        }

        @Override
        public void close() {
            if (!closed) {
                self.stopWatching(configName);
                closed = true;
            }
        }
    }

    // ======================= 私有辅助方法 =======================

    // 注册目录到 WatchService
    private void registerWatchDirectory(Path dirPath) throws IOException {
        if (watchKeyMap.values().stream().noneMatch(p -> p.equals(dirPath))) {
            WatchKey key = dirPath.register(sharedWatcher, StandardWatchEventKinds.ENTRY_MODIFY);
            watchKeyMap.put(key, dirPath);
            logger.info("开始监听目录: " + dirPath);
        }
    }

    // 启动共享监听线程
    private void startWatcherThread() throws IOException {
        if (sharedWatcherThread != null && sharedWatcherThread.isAlive()) {
            return;
        }
        if (sharedWatcher == null) {
            sharedWatcher = FileSystems.getDefault().newWatchService();
        }
        sharedWatcherThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key;
                try {
                    key = sharedWatcher.poll(50, TimeUnit.MILLISECONDS);
                } catch (InterruptedException | ClosedWatchServiceException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (key == null) {
                    continue;
                }
                Path dir = watchKeyMap.get(key);
                if (dir != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                            Path changed = dir.resolve((Path) event.context());
                            String name = watchedFileMap.get(changed);
                            if (name != null) {
                                Consumer<YamlConfiguration> cb = callbackMap.get(name);
                                if (cb != null) {
                                    logger.info("检测到配置文件修改，正在重载: " + name);
                                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                                        reloadConfig(name);
                                        // 回调中建议：对与该配置相关的消息模板执行颜色预解析与缓存，
                                        // 以避免运行期重复解析 <gradient>/<color>/HEX 颜色标签。
                                        cb.accept(getConfig(name));
                                    });
                                }
                            }
                        }
                    }
                }
                if (!key.reset()) {
                    watchKeyMap.remove(key);
                    if (watchKeyMap.isEmpty()) {
                        logger.info("所有监听目录均失效，监听线程退出。");
                        break;
                    }
                }
            }
            logger.info("文件监听线程已停止。");
        }, "YamlUtil-Shared-Watcher");
        sharedWatcherThread.setDaemon(true);
        sharedWatcherThread.start();
    }

    /**
     * 获取配置文件对象。
     * <p>自动查找 configKeyToPath 映射表，将 configKey 转换为实际文件路径。
     * 支持以下两种调用方式：
     * <ul>
     *   <li>{@code getConfigFile("zh_CN")} - 如果该 key 来自 loadAllConfigsInFolder，会自动解析为 "languages/zh_CN.yml"</li>
     *   <li>{@code getConfigFile("languages/zh_CN")} - 直接使用传入的路径</li>
     * </ul>
     * </p>
     */
    private File getConfigFile(String fileName) {
        // 优先查找路径映射表，获取实际的完整路径
        String actualPath = configKeyToPath.getOrDefault(fileName, fileName);
        // 将正斜杠统一转为系统路径分隔符，确保跨平台兼容
        String normalizedPath = actualPath.replace('/', File.separatorChar).replace('\\', File.separatorChar);
        return new File(plugin.getDataFolder(), normalizedPath + ".yml");
    }

    // 设置默认值（内部使用，fileName 应已规范化）
    private void setDefaultIfAbsent(YamlConfiguration cfg, String fileName, String path, Object def) {
        if (!cfg.contains(path)) {
            cfg.set(path, def);
            // 确保 key 规范化后再加入 dirtyConfigs
            String key = normalizeConfigKey(fileName);
            dirtyConfigs.add(key);
            logger.debug("Set default value: " + path + " = " + def + " in " + key);
        }
    }

    // 规范化文件夹路径
    private String normalizeFolder(String resourceFolder) {
        if (resourceFolder == null || resourceFolder.isEmpty()) {
            return "";
        }
        return resourceFolder.endsWith("/") ? resourceFolder : resourceFolder + "/";
    }

    /**
     * 规范化配置文件名/路径，统一使用正斜杠分隔符。
     * 用于确保 Map 的 Key 在跨平台环境下保持一致。
     */
    private String normalizeConfigKey(String fileName) {
        if (fileName == null) {
            return "";
        }
        return fileName.replace('\\', '/');
    }

    // 确保目标文件的父目录存在
    private void ensureParentDir(File dest) {
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists() && parent.mkdirs()) {
            logger.debug("创建父目录: " + parent.getPath());
        }
    }

    // 遍历 JAR 中指定目录下的条目并执行回调
    @FunctionalInterface
    private interface JarEntryConsumer {
        void accept(JarEntry entry, JarFile jar) throws IOException;
    }

    private void traverseJar(String folder, JarEntryConsumer consumer) throws Exception {
        List<JarEntry> cache = jarEntryCache.get(folder);
        if (cache != null) {
            for (JarEntry entry : cache) {
                consumer.accept(entry, null);
            }
            return;
        }
        try (JarFile jar = new JarFile(jarPath)) {
            Enumeration<JarEntry> entries = jar.entries();
            List<JarEntry> list = new ArrayList<>();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().startsWith(folder)) {
                    list.add(entry);
                    consumer.accept(entry, jar);
                }
            }
            jarEntryCache.put(folder, list);
        }
    }

    // 复制 JAR 内资源到文件
    private void copyResourceToFile(String resourcePath, File dest) {
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in != null) {
                Files.copy(in, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                logger.debug("Copied to: " + dest.getPath());
            } else {
                logger.warn("未找到资源: " + resourcePath);
            }
        } catch (IOException e) {
            logger.error("复制资源失败: " + resourcePath, e);
        }
    }
}
