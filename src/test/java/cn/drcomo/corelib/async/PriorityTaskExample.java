package cn.drcomo.corelib.async;

import cn.drcomo.corelib.util.DebugUtil;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Proxy;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * 优先级任务队列使用示例。
 */
public class PriorityTaskExample {

    public static void main(String[] args) throws Exception {
        Plugin plugin = (Plugin) Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class[]{Plugin.class},
                (proxy, method, methodArgs) -> {
                    if ("getName".equals(method.getName())) {
                        return "ExamplePlugin";
                    } else if ("getLogger".equals(method.getName())) {
                        return Logger.getLogger("ExamplePlugin");
                    } else if (method.getReturnType().equals(boolean.class)) {
                        return false;
                    }
                    return null;
                });
        DebugUtil logger = new DebugUtil(plugin, DebugUtil.LogLevel.DEBUG);
        try (AsyncTaskManager manager = new AsyncTaskManager(plugin, logger)) {
            manager.runWithPriority(() -> logger.info("低优先级任务"), TaskPriority.LOW);
            manager.runWithPriority(() -> logger.info("高优先级任务"), TaskPriority.HIGH);
            // 等待任务完成
            TimeUnit.MILLISECONDS.sleep(500);
        }
    }
}
