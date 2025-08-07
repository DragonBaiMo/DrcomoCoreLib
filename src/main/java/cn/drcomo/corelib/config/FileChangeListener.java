package cn.drcomo.corelib.config;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 配置文件变更监听器。
 */
@FunctionalInterface
public interface FileChangeListener {
    /**
     * 当监听的配置文件发生变更时调用。
     *
     * @param configKey 配置文件名（不含 .yml）
     * @param type      变更类型
     * @param config    最新的配置对象
     */
    void onChange(String configKey, FileChangeType type, YamlConfiguration config);
}

