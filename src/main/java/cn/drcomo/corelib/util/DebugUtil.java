package cn.drcomo.corelib.util;

import java.util.logging.Level;
import java.util.logging.Logger;

import cn.drcomo.corelib.color.ColorUtil;
import org.bukkit.plugin.Plugin;

/**
 * 通用调试日志工具
 * <p>功能：</p>
 * <ul>
 *   <li>按等级输出：DEBUG、INFO、WARN、ERROR</li>
 *   <li>基于 Bukkit Logger，自动带上插件名前缀</li>
 *   <li>可在运行时动态调整输出级别</li>
 * </ul>
 */
public class DebugUtil {
    public enum LogLevel {
        DEBUG(0), INFO(1), WARN(2), ERROR(3), NONE(4);

        private final int level;
        LogLevel(int lvl) { this.level = lvl; }
        public int getLevel() { return level; }
        public static LogLevel fromString(String s, LogLevel def) {
            if (s == null) return def;
            try { return LogLevel.valueOf(s.trim().toUpperCase()); }
            catch (IllegalArgumentException e) { return def; }
        }
    }

    private final Logger logger;
    private final String prefix;
    private LogLevel configuredLevel;

    /**
     * 构造一个 DebugUtil
     * @param plugin   任何实现了 org.bukkit.plugin.Plugin 的实例
     * @param level    初始日志级别
     */
    public DebugUtil(Plugin plugin, LogLevel level) {
        this.logger = plugin.getLogger();
        // 前缀示例：[YourPluginName]
        this.prefix = ColorUtil.translateColors("&f[&a" + plugin.getName() + "&f]&r ");
        this.configuredLevel = level;
    }

    public void setLevel(LogLevel level) {
        this.configuredLevel = level;
        log(LogLevel.INFO, "日志级别已设置为: " + level);
    }

    public LogLevel getLevel() {
        return configuredLevel;
    }

    // 简化方法
    public void debug(String msg)    { log(LogLevel.DEBUG, msg); }
    public void info(String msg)     { log(LogLevel.INFO,  msg); }
    public void warn(String msg)     { log(LogLevel.WARN,  msg); }
    public void error(String msg)    { log(LogLevel.ERROR, msg); }
    public void error(String msg, Throwable t) { log(LogLevel.ERROR, msg, t); }

    /**
     * 按级别输出日志
     */
    public void log(LogLevel level, String message) {
        if (configuredLevel == LogLevel.NONE || level.getLevel() < configuredLevel.getLevel()) {
            return;
        }
        String formatted = prefix + message;
        switch (level) {
            case DEBUG:
                // DEBUG 用灰色，并加 [DEBUG] 标签
                logger.log(Level.INFO, ColorUtil.stripColorCodes( "&7[DEBUG] " + message));
                break;
            case INFO:
                logger.log(Level.INFO, formatted);
                break;
            case WARN:
                logger.log(Level.WARNING, formatted);
                break;
            case ERROR:
                logger.log(Level.SEVERE, formatted);
                break;
            default:
                break;
        }
    }

    /**
     * 带异常的日志
     */
    public void log(LogLevel level, String message, Throwable t) {
        if (configuredLevel == LogLevel.NONE || level.getLevel() < configuredLevel.getLevel()) {
            return;
        }
        String formatted = prefix + message;
        switch (level) {
            case DEBUG:
                logger.log(Level.INFO, ColorUtil.stripColorCodes("&7[DEBUG] " + message), t);
                break;
            case INFO:
                logger.log(Level.INFO, formatted, t);
                break;
            case WARN:
                logger.log(Level.WARNING, formatted, t);
                break;
            case ERROR:
                logger.log(Level.SEVERE, formatted, t);
                break;
            default:
                break;
        }
    }
}
