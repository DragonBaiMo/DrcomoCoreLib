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

    /**
     * 构造函数
     *
     * @param plugin   插件实例
     * @param logger   DebugUtil 实例，用于日志输出
     */
    public YamlUtil(Plugin plugin, DebugUtil logger) {
        this.plugin = plugin;
        this.logger = logger;
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
     * 从插件 JAR 的资源文件夹复制所有 .yml 到数据文件夹
     *
     * @param resourceFolder 资源文件夹路径（JAR 内）
     * @param relativePath   目标相对路径（数据文件夹内）
     */
    public void copyDefaults(String resourceFolder, String relativePath) {
        ensureDirectory(relativePath);
        try (JarFile jar = new JarFile(getJarFilePath())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (isYmlResource(entry, resourceFolder)) {
                    String resourceName = entry.getName();
                    String fileName = getFileNameFromResource(resourceName, resourceFolder);
                    File dest = new File(plugin.getDataFolder(), relativePath + File.separator + fileName);
                    if (!dest.exists()) {
                        logger.debug("复制默认配置: " + resourceName + " -> " + dest.getPath());
                        copyResourceToFile(resourceName, dest);
                    } else {
                        logger.debug("跳过已存在文件: " + dest.getPath());
                    }
                }
            }
        } catch (IOException e) {
            logger.error("复制默认文件失败: " + resourceFolder, e);
        } catch (Exception e) {
            logger.error("访问 JAR 失败: copyDefaults", e);
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
        File file = getConfigFile(fileName);
        try {
            cfg.save(file);
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

    /**
     * 从配置获取字符串，若无则写入默认并保存
     */
    public String getString(String fileName, String path, String def) {
        YamlConfiguration cfg = getConfig(fileName);
        setDefaultIfAbsent(cfg, fileName, path, def);
        return cfg.getString(path, def);
    }

    /**
     * 从配置获取整数，若无则写入默认并保存
     */
    public int getInt(String fileName, String path, int def) {
        YamlConfiguration cfg = getConfig(fileName);
        setDefaultIfAbsent(cfg, fileName, path, def);
        return cfg.getInt(path, def);
    }

    /**
     * 从配置获取布尔值，若无则写入默认并保存
     */
    public boolean getBoolean(String fileName, String path, boolean def) {
        YamlConfiguration cfg = getConfig(fileName);
        setDefaultIfAbsent(cfg, fileName, path, def);
        return cfg.getBoolean(path, def);
    }

    /**
     * 从配置获取双精度值，若无则写入默认并保存
     */
    public double getDouble(String fileName, String path, double def) {
        YamlConfiguration cfg = getConfig(fileName);
        setDefaultIfAbsent(cfg, fileName, path, def);
        return cfg.getDouble(path, def);
    }

    /**
     * 从配置获取长整数，若无则写入默认并保存
     */
    public long getLong(String fileName, String path, long def) {
        YamlConfiguration cfg = getConfig(fileName);
        setDefaultIfAbsent(cfg, fileName, path, def);
        return cfg.getLong(path, def);
    }

    /**
     * 从配置获取字符串列表，若无则写入默认并保存
     */
    public List<String> getStringList(String fileName, String path, List<String> def) {
        YamlConfiguration cfg = getConfig(fileName);
        setDefaultIfAbsent(cfg, fileName, path, def);
        return cfg.getStringList(path);
    }

    /**
     * 设置某路径的值并保存
     */
    public void setValue(String fileName, String path, Object value) {
        YamlConfiguration cfg = getConfig(fileName);
        cfg.set(path, value);
        saveConfig(fileName);
        logger.debug("Set value: " + path + " = " + value + " in " + fileName);
    }

    /**
     * 检查配置是否包含某路径
     */
    public boolean contains(String fileName, String path) {
        boolean exists = getConfig(fileName).contains(path);
        logger.debug("Contains check: " + path + " in " + fileName + " = " + exists);
        return exists;
    }

    /**
     * 获取指定路径下的所有键
     */
    public Set<String> getKeys(String fileName, String path) {
        ConfigurationSection sec = getConfig(fileName).getConfigurationSection(path);
        Set<String> keys = (sec != null) ? sec.getKeys(false) : new HashSet<>();
        logger.debug("Keys retrieved: " + keys.size() + " in " + path + " of " + fileName);
        return keys;
    }

    /**
     * 获取指定路径的配置节
     */
    public ConfigurationSection getSection(String fileName, String path) {
        ConfigurationSection sec = getConfig(fileName).getConfigurationSection(path);
        logger.debug("Section retrieved: " + path + " exists=" + (sec != null));
        return sec;
    }

    // ---------------- private 辅助方法 ----------------

    /**
     * 生成配置文件对象
     */
    private File getConfigFile(String fileName) {
        return new File(plugin.getDataFolder(), fileName + ".yml");
    }

    /**
     * 若配置不包含指定路径，则写入默认值并保存
     */
    private void setDefaultIfAbsent(YamlConfiguration cfg, String fileName, String path, Object def) {
        if (!cfg.contains(path)) {
            cfg.set(path, def);
            saveConfig(fileName);
            logger.debug("Set default value: " + path + " = " + def + " in " + fileName);
        }
    }

    /**
     * 判断 JarEntry 是否为指定资源文件夹下的 .yml 文件
     */
    private boolean isYmlResource(JarEntry entry, String folder) {
        String name = entry.getName();
        return name.startsWith(folder) && name.endsWith(".yml") && !entry.isDirectory();
    }

    /**
     * 从资源路径中提取文件名（包含前导斜杠）
     */
    private String getFileNameFromResource(String resourceName, String folder) {
        return resourceName.substring(folder.length());
    }

    /**
     * 将单个资源复制到指定目标文件
     */
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

    /**
     * 获取当前插件的 JAR 文件路径
     */
    private String getJarFilePath() throws Exception {
        return plugin.getClass()
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI()
                .getPath();
    }

}
