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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final String jarPath;
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
            logger.debug("未找到配置，复制默认: " + fileName);
            copyDefaults("", "");
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
     * 保存缓存中的配置到磁盘
     *
     * @param fileName 文件名（不含 .yml）
     */
    public void saveConfig(String fileName) {
        YamlConfiguration cfg = configs.get(fileName);
        if (cfg == null) {
            logger.warn("无可保存配置: " + fileName);
            return;
        }
        try {
            cfg.save(getConfigFile(fileName));
            logger.debug("Saved config: " + fileName);
        } catch (IOException e) {
            logger.error("保存配置失败: " + fileName, e);
        }
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
        saveConfig(fileName);
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
            saveConfig(DEFAULT_FILE);
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

    // ---------------- private 辅助方法 ----------------

    private File getConfigFile(String fileName) {
        return new File(plugin.getDataFolder(), fileName + ".yml");
    }

    private void setDefaultIfAbsent(YamlConfiguration cfg, String fileName, String path, Object def) {
        if (!cfg.contains(path)) {
            cfg.set(path, def);
            saveConfig(fileName);
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
        try (JarFile jar = new JarFile(jarPath)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().startsWith(folder)) {
                    consumer.accept(entry, jar);
                }
            }
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

    // 保留供可能的扩展使用

}
