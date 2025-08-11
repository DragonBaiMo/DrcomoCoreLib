package cn.drcomo.corelib.hook.placeholder;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.BiFunction;

/**
 * 并发注册占位符的简单测试。
 */
public class PlaceholderAPIUtilConcurrentTest {

    @SuppressWarnings("unchecked")
    @Test
    public void concurrentRegister() throws Exception {
        // 使用 Unsafe 分配对象以绕过构造函数
        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        Unsafe unsafe = (Unsafe) f.get(null);
        PlaceholderAPIUtil util = (PlaceholderAPIUtil) unsafe.allocateInstance(PlaceholderAPIUtil.class);

        // 初始化 handlers 为并发映射
        Field handlersField = PlaceholderAPIUtil.class.getDeclaredField("handlers");
        handlersField.setAccessible(true);
        Map<String, BiFunction<Player, String, String>> handlers = new ConcurrentHashMap<>();
        handlersField.set(util, handlers);

        int threadCount = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            pool.execute(() -> {
                util.register("k" + idx, (p, r) -> String.valueOf(idx));
                latch.countDown();
            });
        }
        latch.await(3, TimeUnit.SECONDS);
        pool.shutdownNow();
        // 验证所有占位符均成功注册
        Assertions.assertEquals(threadCount, handlers.size());
    }
}
