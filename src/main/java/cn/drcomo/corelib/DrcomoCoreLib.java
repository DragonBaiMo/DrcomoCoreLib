package cn.drcomo.corelib;

import org.bukkit.plugin.java.JavaPlugin;
import cn.drcomo.corelib.util.DebugUtil;
import cn.drcomo.corelib.util.DebugUtil.LogLevel;
import cn.drcomo.corelib.message.MessageService;
import cn.drcomo.corelib.hook.placeholder.PlaceholderAPIUtil;

public final class DrcomoCoreLib extends JavaPlugin {

    // 移除单例字段，鼓励依赖注入
    private DebugUtil debug;

    @Override
    public void onEnable() {
        // 初始化仅供本库内部使用的 DebugUtil
        this.debug = new DebugUtil(this, LogLevel.INFO);

        // 打印启动信息，向服务器管理员说明本库的用途
        debug.info("====================================================");
        debug.info("DrcomoCoreLib 核心库已加载 (版本: " + getDescription().getVersion() + ")");
        debug.info("这是一个面向插件开发者的工具箱，提供 YamlUtil、SoundManager、MessageService 等高复用组件。");
        debug.info("它本身不执行任何业务逻辑，也不会注册事件监听或指令。");
        debug.info("使用方式：在你的子插件中，通过 new YamlUtil(...), new SoundManager(...) 等方式创建实例并注入依赖。");
        debug.info("详细使用示例请参考源码中的 showUsageExample() 方法上的 Javadoc。");
        debug.info("====================================================");
    }

    @Override
    public void onDisable() {
        // 本库不持有可关闭资源，仅输出日志以示关闭
        if (debug != null) {
            debug.info("DrcomoCoreLib 已卸载，感谢使用。");
        }
    }

    /**
     * ---- 使用范例：子插件应该如何使用本核心库 ----
     *
     * 以下代码应出现在你的「子插件」的 onEnable() 方法中。
     *
     * <pre>{@code
     *
     * import cn.drcomo.corelib.util.DebugUtil;
     * import cn.drcomo.corelib.config.YamlUtil;
     * import cn.drcomo.corelib.sound.SoundManager;
     * import cn.drcomo.corelib.message.MessageService;
     * // ... 其他导入
     *
     * public class MyAwesomePlugin extends JavaPlugin {
     *
     *     private DebugUtil myLogger;
     *     private YamlUtil myYamlUtil;
     *     private SoundManager mySoundManager;
     *     private MessageService myMessageService;
     *
     *     @Override
     *     public void onEnable() {
     *         // 1. 为你的插件创建独立的日志工具
     *         myLogger = new DebugUtil(this, DebugUtil.LogLevel.DEBUG);
     *
     *         // 2. 为你的插件创建独立的 Yaml 配置工具
     *         myYamlUtil = new YamlUtil(this, myLogger);
     *
     *         // 3. 实例化 SoundManager，并注入你自己的 YamlUtil
     *         //    你来决定配置文件名、音量、是否警告
     *         mySoundManager = new SoundManager(
     *                 this,
     *                 myYamlUtil,
     *                 myLogger,
     *                 "myPluginSounds",  // 配置文件名: myPluginSounds.yml
     *                 1.0f,              // 全局音量倍率
     *                 true               // 找不到Key时发出警告
     *         );
     *         mySoundManager.loadSounds(); // 手动加载
     *
     *         // 4. 创建 PlaceholderAPIUtil（如有占位符需求）
     *         PlaceholderAPIUtil papiUtil = new PlaceholderAPIUtil(this, "my_identifier");
     *
     *         myMessageService = new MessageService(
     *                 this,
     *                 myLogger,
     *                 myYamlUtil,
     *                 papiUtil,
     *                 "languages/lang_zh_cn", // 配置文件路径
     *                 "messages.myplugin."    // 消息键前缀
     *         );
     *
     *         myLogger.info("我的插件 MyAwesomePlugin 已成功加载，并配置好了核心库工具！");
     *     }
     * }
     *
     * }</pre>
     */
    private void showUsageExample() {
        // 此方法仅用于承载 Javadoc，不应被调用。
    }
}
