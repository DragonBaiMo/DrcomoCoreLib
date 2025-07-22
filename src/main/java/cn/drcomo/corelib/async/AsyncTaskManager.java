package cn.drcomo.corelib.async;

import cn.drcomo.corelib.util.DebugUtil;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 异步任务管理器。
 * <p>
 * 内部维护普通和定时两种线程池，用于执行和调度异步任务。
 * 所有回调中的异常都会被捕获并记录到提供的 {@link DebugUtil} 实例。
 * </p>
 * 在插件关闭时应调用 {@link #close()} 以释放线程资源。
 */
public class AsyncTaskManager implements AutoCloseable {

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(1);

    private final Plugin plugin;
    private final DebugUtil logger;
    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;

    /**
     * 构造异步任务管理器，使用默认线程池实现。
     *
     * @param plugin 插件实例
     * @param logger DebugUtil 日志工具
     */
    public AsyncTaskManager(Plugin plugin, DebugUtil logger) {
        this(plugin, logger, createDefaultExecutor(plugin, 0, null), createDefaultScheduler(plugin, 1, null));
    }

    private AsyncTaskManager(Plugin plugin, DebugUtil logger,
                             ExecutorService executor, ScheduledExecutorService scheduler) {
        this.plugin = plugin;
        this.logger = logger;
        this.executor = executor;
        this.scheduler = scheduler;
    }

    /**
     * 提交一个异步任务。
     *
     * @param task 需要执行的任务
     * @return 代表任务的 Future
     */
    public Future<?> submitAsync(Runnable task) {
        return executor.submit(wrap(task));
    }

    /**
     * 提交一个异步任务并返回结果。
     *
     * @param task 可返回结果的任务
     * @param <T>  返回值类型
     * @return 代表任务的 Future
     */
    public <T> Future<T> submitAsync(Callable<T> task) {
        return executor.submit(wrap(task));
    }

    /**
     * 延迟执行异步任务。
     *
     * @param task  任务
     * @param delay 延迟时间
     * @param unit  时间单位
     * @return 调度结果句柄
     */
    public ScheduledFuture<?> scheduleAsync(Runnable task, long delay, TimeUnit unit) {
        return scheduler.schedule(wrap(task), delay, unit);
    }

    /**
     * 以固定频率执行任务。
     *
     * @param task         任务
     * @param initialDelay 首次执行前的延迟
     * @param period       间隔时间
     * @param unit         时间单位
     * @return 调度结果句柄
     */
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        return scheduler.scheduleAtFixedRate(wrap(task), initialDelay, period, unit);
    }

    /**
     * 批量提交多个任务。
     *
     * @param tasks 任务集合
     * @return 每个任务对应的 Future 列表
     */
    public List<Future<?>> submitBatch(Collection<? extends Runnable> tasks) {
        List<Future<?>> futures = new ArrayList<>();
        if (tasks != null) {
            for (Runnable r : tasks) {
                futures.add(submitAsync(r));
            }
        }
        return futures;
    }

    /**
     * 关闭内部线程池，停止接受新任务。
     */
    public void shutdown() {
        executor.shutdown();
        scheduler.shutdown();
    }

    /**
     * 与 {@link #shutdown()} 功能相同，方便 try-with-resources
     * 或在插件 {@code onDisable()} 中调用。
     */
    @Override
    public void close() {
        shutdown();
    }

    private Runnable wrap(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Exception e) {
                logger.error("异步任务执行异常", e);
            }
        };
    }

    private <T> Callable<T> wrap(Callable<T> task) {
        return () -> {
            try {
                return task.call();
            } catch (Exception e) {
                logger.error("异步任务执行异常", e);
                throw e;
            }
        };
    }

    private static ExecutorService createDefaultExecutor(Plugin plugin, int poolSize, ThreadFactory factory) {
        ThreadFactory useFactory = factory != null ? factory : r -> {
            Thread t = new Thread(r, plugin.getName() + "-Async-" + THREAD_COUNTER.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
        if (poolSize > 0) {
            return Executors.newFixedThreadPool(poolSize, useFactory);
        }
        return Executors.newCachedThreadPool(useFactory);
    }

    private static ScheduledExecutorService createDefaultScheduler(Plugin plugin, int poolSize, ThreadFactory factory) {
        ThreadFactory useFactory = factory != null ? factory : r -> {
            Thread t = new Thread(r, plugin.getName() + "-Scheduler-" + THREAD_COUNTER.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
        if (poolSize > 1) {
            return Executors.newScheduledThreadPool(poolSize, useFactory);
        }
        return Executors.newSingleThreadScheduledExecutor(useFactory);
    }

    /**
     * 创建 Builder 以自定义线程池。
     */
    public static Builder newBuilder(Plugin plugin, DebugUtil logger) {
        return new Builder(plugin, logger);
    }

    /**
     * 构建器，用于自定义执行器实现。
     */
    public static class Builder {
        private final Plugin plugin;
        private final DebugUtil logger;
        private ExecutorService executor;
        private ScheduledExecutorService scheduler;
        private int poolSize;
        private int schedulerSize = 1;
        private ThreadFactory threadFactory;
        private ThreadFactory schedulerFactory;

        private Builder(Plugin plugin, DebugUtil logger) {
            this.plugin = plugin;
            this.logger = logger;
        }

        /** 设置自定义执行线程池 */
        public Builder executor(ExecutorService executor) {
            this.executor = executor;
            return this;
        }

        /** 设置默认线程池的大小 */
        public Builder poolSize(int size) {
            this.poolSize = Math.max(0, size);
            return this;
        }

        /** 设置线程工厂 */
        public Builder threadFactory(ThreadFactory factory) {
            this.threadFactory = factory;
            return this;
        }

        /** 设置自定义调度线程池 */
        public Builder scheduler(ScheduledExecutorService scheduler) {
            this.scheduler = scheduler;
            return this;
        }

        /** 设置调度线程数量 */
        public Builder schedulerSize(int size) {
            this.schedulerSize = Math.max(1, size);
            return this;
        }

        /** 设置调度线程工厂 */
        public Builder schedulerFactory(ThreadFactory factory) {
            this.schedulerFactory = factory;
            return this;
        }

        /** 构建 AsyncTaskManager 实例 */
        public AsyncTaskManager build() {
            ExecutorService ex = executor != null ? executor : createDefaultExecutor(plugin, poolSize, threadFactory);
            ScheduledExecutorService sch = scheduler != null ? scheduler : createDefaultScheduler(plugin, schedulerSize, schedulerFactory);
            return new AsyncTaskManager(plugin, logger, ex, sch);
        }
    }
}
