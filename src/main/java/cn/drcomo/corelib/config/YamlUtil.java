package cn.drcomo.corelib.config;

import org.bukkit.plugin.Plugin;
import cn.drcomo.corelib.util.DebugUtil;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 工具类：管理 Bukkit 插件的 YAML 配置文件。
 * 提供目录创建、JAR 内资源复制、配置加载/重载/保存、以及带默认值写入的读取方法，附加详细日志。
 */
public class YamlUtil {

    private final Plugin plugin;
    private final DebugUtil logger;
    private final Map<String, YamlConfiguration> configs = new HashMap<>();
    private final Set<String> dirtyConfigs = new HashSet<>(); // 记录被修改过的配置
    private final String jarPath;
    /** 已创建的配置文件监听器 */
    // private final Set<ConfigWatchHandle> watchHandles = new HashSet<>(); // 被新的监听机制取代
    /** 共享的配置文件监听服务 */
    private WatchService sharedWatcher;
    /** 共享的监听服务后台线程 */
    private Thread sharedWatcherThread;
    /** 监听key与目录路径的映射 */
    private final Map<WatchKey, Path> watchKeyMap = new HashMap<>();
    /** 被监听的文件路径 -> 配置文件名 的映射 */
    private final Map<Path, String> watchedFileMap = new HashMap<>();
    /** 配置文件名 -> 回调 的映射 */
    private final Map<String, Consumer<YamlConfiguration>> callbackMap = new HashMap<>();
    /** JAR 条目缓存：记录各目录下的条目列表 */
    private final Map<String, List<JarEntry>> jarEntryCache = new HashMap<>();
    /** 默认配置文件名 */
    private static final String DEFAULT_FILE = "config";

    /**
     * 构造函数
     *
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

    /**
     * 确保插件数据文件夹下的指定目录存在
     *
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
     * 从插件 JAR 内指定资源文件夹（含其子目录）复制所有 .yml 文件到数据文件夹下的目标目录，仅在目标文件不存在时才复制。
     *
     * @param resourceFolder 资源文件夹相对于 JAR 根的路径，如 "config" 或 "" 表示根目录
     * @param relativePath   数据文件夹内的目标相对路径，可为空字符串
     */
    public void copyDefaults(String resourceFolder, String relativePath) {
        String folder = normalizeFolder(resourceFolder);
        ensureDirectory(relativePath);
        try {
            traverseJar(folder, (entry, jar) -> {
                if (entry.getName().endsWith(".yml") && !entry.isDirectory()) {
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
     * 复制插件 JAR 内指定的单个 yml 文件到数据文件夹的相对目录，若目标文件已存在则跳过。
     *
     * @param resourcePath 资源文件的完整路径（JAR 内），例如 "config/example.yml"
     * @param relativePath 数据文件夹内的目标目录，相对插件根目录
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
     * 加载配置文件到缓存
     *
     * @param fileName 文件名（不含 .yml）
     */
    public void loadConfig(String fileName) {
        ensureDirectory("");
        File file = getConfigFile(fileName);
        if (!file.exists()) {
            logger.debug("未找到配置，将从 JAR 中复制默认文件: " + fileName + ".yml");
            plugin.saveResource(fileName + ".yml", false);
        }
        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            configs.put(fileName, cfg);
            logger.debug("Loaded config: " + fileName);
        } catch (Exception e) {
            logger.error("加载配置失败: " + fileName, e);
        }
    }

    /**
     * 扫描指定目录下的所有 {@code .yml} 文件并全部加载。
     * 加载的配置会放入内部缓存，以文件名（不含副档名）为键。
     *
     * @param folderPath 相对于插件数据文件夹的目录路径
     * @return 以文件名为键、配置对象为值的映射
     */
    public Map<String, YamlConfiguration> loadAllConfigsInFolder(String folderPath) {
        Map<String, YamlConfiguration> map = new HashMap<>();
        File dir = new File(plugin.getDataFolder(), folderPath);
        if (!dir.exists() || !dir.isDirectory()) {
            logger.warn("目录不存在: " + dir.getPath());
            return map;
        }
        File[] files = dir.listFiles((d, name) -> name.endsWith(".yml"));
        if (files == null) return map;
        for (File file : files) {
            String name = file.getName().replaceFirst("\\.yml$", "");
            try {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                configs.put(name, cfg);
                map.put(name, cfg);
                logger.debug("Loaded config: " + file.getPath());
            } catch (Exception e) {
                logger.error("加载配置失败: " + file.getPath(), e);
            }
        }
        return map;
    }

    /**
     * 重载配置文件
     *
     * @param fileName 文件名（不含 .yml）
     */
    public void reloadConfig(String fileName) {
        logger.info("重载配置开始: " + fileName);
        File file = getConfigFile(fileName);
        if (file.exists()) {
            try {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                configs.put(fileName, cfg);
                logger.info("重载配置完成: " + fileName);
            } catch (Exception e) {
                logger.error("重载配置失败: " + fileName, e);
            }
        } else {
            logger.warn("重载时未找到配置文件: " + fileName);
        }
    }

    /**
     * 保存缓存中的配置到磁盘（如果它被标记为“脏”）。
     *
     * @param fileName 文件名（不含 .yml）
     */
    public void saveConfig(String fileName) {
        saveConfig(fileName, false);
    }

    /**
     * 保存缓存中的配置到磁盘。
     *
     * @param fileName 文件名（不含 .yml）
     * @param force    是否强制保存，即使未被标记为“脏”
     */
    public void saveConfig(String fileName, boolean force) {
        if (!force && !dirtyConfigs.contains(fileName)) {
            return; // 未修改，不保存
        }

        YamlConfiguration cfg = configs.get(fileName);
        if (cfg == null) {
            logger.warn("无可保存配置: " + fileName);
            return;
        }
        try {
            cfg.save(getConfigFile(fileName));
            dirtyConfigs.remove(fileName); // 保存后移除脏标记
            logger.debug("Saved config: " + fileName);
        } catch (IOException e) {
            logger.error("保存配置失败: " + fileName, e);
        }
    }

    /**
     * 保存所有被标记为“脏”的配置文件。
     * 建议在插件 onDisable 时调用。
     */
    public void saveAllDirtyConfigs() {
        if (dirtyConfigs.isEmpty()) {
            return;
        }
        logger.info("正在保存 " + dirtyConfigs.size() + " 个已修改的配置文件...");
        // 创建副本以避免在迭代时修改
        Set<String> dirtySnapshot = new HashSet<>(dirtyConfigs);
        for (String fileName : dirtySnapshot) {
            saveConfig(fileName, true); // 强制保存
        }
        logger.info("所有已修改的配置文件保存完毕。");
    }

    /**
     * 获取配置，若未加载则先加载
     *
     * @param fileName 文件名（不含 .yml）
     * @return YamlConfiguration 实例
     */
    public YamlConfiguration getConfig(String fileName) {
        if (!configs.containsKey(fileName)) {
            loadConfig(fileName);
        }
        return configs.get(fileName);
    }

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

    public void setValue(String fileName, String path, Object value) {
        YamlConfiguration cfg = getConfig(fileName);
        cfg.set(path, value);
        dirtyConfigs.add(fileName); // 标记为脏
        logger.debug("Set value: " + path + " = " + value + " in " + fileName);
    }

    public boolean contains(String fileName, String path) {
        boolean exists = getConfig(fileName).contains(path);
        logger.debug("Contains check: " + path + " in " + fileName + " = " + exists);
        return exists;
    }

    public Set<String> getKeys(String fileName, String path) {
        ConfigurationSection sec = getConfig(fileName).getConfigurationSection(path);
        Set<String> keys = (sec != null) ? sec.getKeys(false) : new HashSet<>();
        logger.debug("Keys retrieved: " + keys.size() + " in " + path + " of " + fileName);
        return keys;
    }

    public ConfigurationSection getSection(String fileName, String path) {
        ConfigurationSection sec = getConfig(fileName).getConfigurationSection(path);
        logger.debug("Section retrieved: " + path + " exists=" + (sec != null));
        return sec;
    }

    /**
     * 从默认 config.yml 中以类型安全的方式读取配置。
     * 若路径不存在或类型不符，则写入并返回默认值。
     *
     * @param path         配置路径
     * @param type         期望的类型，例如 {@code String.class}
     * @param defaultValue 默认值
     * @return 读取到的值
     */
    public <T> T getValue(String path, Class<T> type, T defaultValue) {
        YamlConfiguration cfg = getConfig(DEFAULT_FILE);
        Object val = cfg.get(path);
        if (val == null || !type.isInstance(val)) {
            cfg.set(path, defaultValue);
            dirtyConfigs.add(DEFAULT_FILE); // 标记为脏
            logger.debug("Set typed default: " + path + " = " + defaultValue);
            return defaultValue;
        }
        return type.cast(val);
    }

    /**
     * 若目标目录不存在，则创建并从资源中一次性复制全部文件（含子目录、所有文件）。
     *
     * @param resourceFolder 资源文件夹相对于 JAR 根的路径，如 "templates" 或 "assets/lang"
     * @param relativePath   数据文件夹内的目标目录，相对插件根目录
     */
    public void ensureFolderAndCopyDefaults(String resourceFolder, String relativePath) {
        File targetDir = new File(plugin.getDataFolder(), relativePath);
        if (targetDir.exists()) {
            logger.debug("目标目录已存在，跳过初始化: " + targetDir.getPath());
            return;
        }
        ensureDirectory(relativePath);
        String folder = normalizeFolder(resourceFolder);
        try {
            traverseJar(folder, (entry, jar) -> {
                if (!entry.isDirectory()) {
                    String subPath = entry.getName().substring(folder.length());
                    File dest = new File(targetDir, subPath.replace("/", File.separator));
                    ensureParentDir(dest);
                    if (!dest.exists()) {
                        logger.debug("初始化复制: " + entry.getName() + " -> " + dest.getPath());
                        copyResourceToFile(entry.getName(), dest);
                    }
                }
            });
        } catch (Exception e) {
            logger.error("初始化复制文件夹失败: " + resourceFolder, e);
        }
    }

    /**
     * 监听指定配置文件，并允许自定义执行器和监听的事件类型。
     *
     * @param configName 配置文件名（不含 .yml）
     * @param onChange   文件变更后的回调，提供最新的 YamlConfiguration
     * @param executor   执行监听任务的线程池（此参数在新实现中被忽略）。
     * @param kinds      要监听的事件类型（此参数在新实现中被忽略）。
     * @return 监听句柄，可通过 {@link ConfigWatchHandle#close()} 停止监听。
     * @deprecated 此方法为每个监听器创建一个新线程，效率低下。请改用 {@link #watchConfig(String, Consumer)}，它使用一个共享的、资源高效的监听线程。
     */
    @Deprecated
    public ConfigWatchHandle watchConfig(String configName, Consumer<YamlConfiguration> onChange,
                                         ExecutorService executor, WatchEvent.Kind<?>... kinds) {
        logger.warn("正在调用已弃用的 watchConfig 方法。executor 和 kinds 参数将被忽略。请迁移到新的 watchConfig(String, Consumer) 方法。");
        return watchConfig(configName, onChange);
    }

    /**
     * 监听指定配置文件，一旦文件被修改则自动重载并触发回调。
     * 使用共享的后台线程，避免为每个监听器创建新线程。
     *
     * @param configName 配置文件名（不含 .yml）
     * @param onChange   文件变更后的回调，提供最新的 YamlConfiguration
     * @return 监听句柄，可通过 {@link ConfigWatchHandle#close()} 停止监听
     */
    public ConfigWatchHandle watchConfig(String configName, Consumer<YamlConfiguration> onChange) {
        try {
            startWatcherThread(); // 确保监听线程已启动
        } catch (IOException e) {
            logger.error("无法初始化文件监听服务", e);
            return null;
        }

        File configFile = getConfigFile(configName);
        Path filePath = configFile.toPath();
        Path dirPath = filePath.getParent();

        // 如果目录未被监听，则注册
        if (watchKeyMap.values().stream().noneMatch(p -> p.equals(dirPath))) {
            try {
                WatchKey key = dirPath.register(sharedWatcher, StandardWatchEventKinds.ENTRY_MODIFY);
                watchKeyMap.put(key, dirPath);
                logger.info("开始监听目录: " + dirPath);
            } catch (IOException e) {
                logger.error("监听目录失败: " + dirPath, e);
                return null;
            }
        }

        // 存储文件和回调的映射关系
        watchedFileMap.put(filePath, configName);
        callbackMap.put(configName, onChange);

        logger.info("已设置对 " + configFile.getName() + " 的修改监听。");
        return new ConfigWatchHandle(this, configName);
    }

    /**
     * 停止监听指定的配置文件。
     * @param configName 要停止监听的配置文件名
     */
    public void stopWatching(String configName) {
        callbackMap.remove(configName);
        Path toRemove = null;
        for (Map.Entry<Path, String> entry : watchedFileMap.entrySet()) {
            if (entry.getValue().equals(configName)) {
                toRemove = entry.getKey();
                break;
            }
        }
        if (toRemove != null) {
            watchedFileMap.remove(toRemove);
            logger.info("已停止监听配置文件: " + configName);
        }
        // 注意：为简化，即使一个目录下的所有文件都不再被监听，我们也不注销目录的 WatchKey。
    }

    /**
     * 停止并关闭所有正在监听的配置文件。
     */
    public void stopAllWatches() {
        if (sharedWatcherThread != null) {
            sharedWatcherThread.interrupt();
            sharedWatcherThread = null;
        }
        if (sharedWatcher != null) {
            try {
                sharedWatcher.close();
            } catch (IOException e) {
                logger.error("关闭 WatchService 失败", e);
            }
            sharedWatcher = null;
        }
        watchKeyMap.clear();
        watchedFileMap.clear();
        callbackMap.clear();
        clearJarCache();
        logger.info("所有文件监听已停止。");
    }

    /**
     * 清空 JAR 内条目缓存，释放内存。
     */
    public void clearJarCache() {
        jarEntryCache.clear();
    }

    /**
     * 监听句柄，用于停止对单个配置文件的监听。
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

    private void startWatcherThread() throws IOException {
        if (sharedWatcherThread != null && sharedWatcherThread.isAlive()) {
            return; // 已经启动
        }

        if (sharedWatcher == null) {
            sharedWatcher = FileSystems.getDefault().newWatchService();
        }

        this.sharedWatcherThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key;
                try {
                    key = sharedWatcher.take();
                } catch (InterruptedException | ClosedWatchServiceException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                Path dir = watchKeyMap.get(key);
                if (dir == null) continue;

                // 延迟一小段时间，避免短时间内多次修改（如编辑器保存）触发多次重载
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() != StandardWatchEventKinds.ENTRY_MODIFY) {
                        continue;
                    }

                    Path changedFile = dir.resolve((Path) event.context());
                    String configName = watchedFileMap.get(changedFile);

                    if (configName != null) {
                        Consumer<YamlConfiguration> callback = callbackMap.get(configName);
                        if (callback != null) {
                            logger.info("检测到配置文件修改，正在重载: " + configName);
                            // 在 Bukkit 主线程中执行重载和回调，以确保线程安全
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                reloadConfig(configName);
                                callback.accept(getConfig(configName));
                            });
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

        this.sharedWatcherThread.setDaemon(true);
        this.sharedWatcherThread.start();
    }

    // ---------------- private 辅助方法 ----------------

    private File getConfigFile(String fileName) {
        return new File(plugin.getDataFolder(), fileName + ".yml");
    }

    private void setDefaultIfAbsent(YamlConfiguration cfg, String fileName, String path, Object def) {
        if (!cfg.contains(path)) {
            cfg.set(path, def);
            dirtyConfigs.add(fileName); // 标记为脏
            logger.debug("Set default value: " + path + " = " + def + " in " + fileName);
        }
    }

    private String normalizeFolder(String resourceFolder) {
        if (resourceFolder == null || resourceFolder.isEmpty()) return "";
        return resourceFolder.endsWith("/") ? resourceFolder : resourceFolder + "/";
    }

    private void ensureParentDir(File dest) {
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists() && parent.mkdirs()) {
            logger.debug("创建父目录: " + parent.getPath());
        }
    }

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
