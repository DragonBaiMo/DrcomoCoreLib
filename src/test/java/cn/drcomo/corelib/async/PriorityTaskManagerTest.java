package cn.drcomo.corelib.async;

import cn.drcomo.corelib.util.DebugUtil;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 测试优先级任务执行顺序。
 */
public class PriorityTaskManagerTest {

    private Plugin createPlugin() {
        return (Plugin) Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class[]{Plugin.class},
                (proxy, method, args) -> {
                    if ("getName".equals(method.getName())) {
                        return "TestPlugin";
                    } else if ("getLogger".equals(method.getName())) {
                        return Logger.getLogger("TestPlugin");
                    } else if (method.getReturnType().equals(boolean.class)) {
                        return false;
                    }
                    return null;
                });
    }

    @Test
    void priorityOrder() throws Exception {
        Plugin plugin = createPlugin();
        DebugUtil logger = new DebugUtil(plugin, DebugUtil.LogLevel.ERROR);
        try (AsyncTaskManager manager = new AsyncTaskManager(plugin, logger)) {
            List<String> order = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch latch = new CountDownLatch(3);

            manager.runWithPriority(() -> { order.add("low"); latch.countDown(); }, TaskPriority.LOW);
            manager.runWithPriority(() -> { order.add("high"); latch.countDown(); }, TaskPriority.HIGH);
            manager.runWithPriority(() -> { order.add("normal"); latch.countDown(); }, TaskPriority.NORMAL);

            latch.await(2, TimeUnit.SECONDS);
            assertEquals(List.of("high", "normal", "low"), order);
            assertEquals(0, manager.getQueueStatus().getTotal());

            // 验证 CompletableFuture 接口
            assertEquals(1, manager.supplyAsync(() -> 1).get().intValue());
            manager.runAsync(() -> order.add("async")).get();
        }
    }
}
