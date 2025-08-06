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
 *
 * 内部维护普通和定时两种线程池，用于执行和调度异步任务。
 * 所有回调中的异常都会被捕获并记录到提供的 {@link DebugUtil} 实例。
 * 插件关闭时应调用 {@link #close()} 以释放线程资源。
 */
public class AsyncTaskManager implements AutoCloseable {

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(1);

    private final Plugin plugin;
    private final DebugUtil logger;
    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;

    /**
     * 使用默认线程池构造异步任务管理器。
     *
     * @param plugin 插件实例
     * @param logger 日志工具
     */
    public AsyncTaskManager(Plugin plugin, DebugUtil logger) {
        this(plugin, logger,
             createDefaultExecutor(plugin, 0, null),
             createDefaultScheduler(plugin, 1, null));
    }

    private AsyncTaskManager(Plugin plugin, DebugUtil logger,
                             ExecutorService executor,
                             ScheduledExecutorService scheduler) {
        this.plugin = plugin;
        this.logger = logger;
        this.executor = executor;
        this.scheduler = scheduler;
    }

    //================ public API =================

    /**
     * 获取内部执行线程池。
     *
     * @return ExecutorService 实例
     */
    public ExecutorService getExecutor() {
        return executor;
    }

    /**
     * 获取内部调度线程池。
     *
     * @return ScheduledExecutorService 实例
     */
    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    /**
     * 提交一个异步任务。
     *
     * @param task 任务
     * @return Future 句柄
     */
    public Future<?> submitAsync(Runnable task) {
        return executor.submit(wrapRunnable(task));
    }

    /**
     * 提交一个可返回结果的异步任务。
     *
     * @param task 任务
     * @param <T>  返回类型
     * @return Future 句柄
     */
    public <T> Future<T> submitAsync(Callable<T> task) {
        return executor.submit(wrapCallable(task));
    }

    /**
     * 延迟执行异步任务。
     *
     * @param task  任务
     * @param delay 延迟时长
     * @param unit  时间单位
     * @return ScheduledFuture 句柄
     */
    public ScheduledFuture<?> scheduleAsync(Runnable task, long delay, TimeUnit unit) {
        return scheduler.schedule(wrapRunnable(task), delay, unit);
    }

    /**
     * 以固定频率执行任务。
     *
     * @param task         任务
     * @param initialDelay 首次延迟
     * @param period       重复间隔
     * @param unit         时间单位
     * @return ScheduledFuture 句柄
     */
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task,
                                                  long initialDelay,
                                                  long period,
                                                  TimeUnit unit) {
        return scheduler.scheduleAtFixedRate(wrapRunnable(task),
                                             initialDelay, period, unit);
    }

    /**
     * 批量提交多个 Runnable 任务。
     *
     * @param tasks 任务集合
     * @return 对应的 Future 列表
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
     * 关闭线程池，停止接受新任务。
     */
    public void shutdown() {
        executor.shutdown();
        scheduler.shutdown();
    }

    /**
     * 与 {@link #shutdown()} 功能相同，方便 try-with-resources 或 onDisable 调用。
     */
    @Override
    public void close() {
        shutdown();
    }

    //================ private helpers =================

    /**
     * 包装 Runnable，使其在执行异常时记录日志。
     */
    private Runnable wrapRunnable(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Exception e) {
                logger.error("异步任务执行异常", e);
            }
        };
    }

    /**
     * 包装 Callable，使其在执行异常时记录日志并重新抛出。
     */
    private <T> Callable<T> wrapCallable(Callable<T> task) {
        return () -> {
            try {
                return task.call();
            } catch (Exception e) {
                logger.error("异步任务执行异常", e);
                throw e;
            }
        };
    }

    /**
     * 创建默认线程池。
     *
     * @param plugin      插件实例
     * @param poolSize    固定大小；若 <= 0 则使用缓存线程池
     * @param customFac   自定义线程工厂（可为 null）
     * @return ExecutorService
     */
    private static ExecutorService createDefaultExecutor(Plugin plugin,
                                                         int poolSize,
                                                         ThreadFactory customFac) {
        ThreadFactory factory = customFac != null
                ? customFac
                : createThreadFactory(plugin, "-Async-");
        if (poolSize > 0) {
            return Executors.newFixedThreadPool(poolSize, factory);
        }
        return Executors.newCachedThreadPool(factory);
    }

    /**
     * 创建默认调度线程池。
     *
     * @param plugin        插件实例
     * @param schedulerSize 固定大小；若 <= 1 则使用单线程调度
     * @param customFac     自定义线程工厂（可为 null）
     * @return ScheduledExecutorService
     */
    private static ScheduledExecutorService createDefaultScheduler(Plugin plugin,
                                                                   int schedulerSize,
                                                                   ThreadFactory customFac) {
        ThreadFactory factory = customFac != null
                ? customFac
                : createThreadFactory(plugin, "-Scheduler-");
        if (schedulerSize > 1) {
            return Executors.newScheduledThreadPool(schedulerSize, factory);
        }
        return Executors.newSingleThreadScheduledExecutor(factory);
    }

    /**
     * 创建一个命名格式化的线程工厂，线程名形如：<插件名>{prefix}{序号}。
     *
     * @param plugin 插件实例
     * @param prefix 名称前缀（如 "-Async-"、"-Scheduler-"）
     * @return ThreadFactory
     */
    private static ThreadFactory createThreadFactory(Plugin plugin,
                                                     String prefix) {
        return runnable -> {
            String name = plugin.getName()
                    + prefix
                    + THREAD_COUNTER.getAndIncrement();
            Thread t = new Thread(runnable, name);
            t.setDaemon(true);
            return t;
        };
    }

    //================ Builder =================

    /**
     * 创建 Builder 以自定义线程池参数。
     */
    public static Builder newBuilder(Plugin plugin, DebugUtil logger) {
        return new Builder(plugin, logger);
    }

    /**
     * Builder 用于自定义 ExecutorService 与 ScheduledExecutorService。
     */
    public static class Builder {
        private final Plugin plugin;
        private final DebugUtil logger;
        private ExecutorService executor;
        private ScheduledExecutorService scheduler;
        private int poolSize = 0;
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

        /** 设置默认执行线程池大小 */
        public Builder poolSize(int size) {
            this.poolSize = Math.max(0, size);
            return this;
        }

        /** 设置执行线程工厂 */
        public Builder threadFactory(ThreadFactory factory) {
            this.threadFactory = factory;
            return this;
        }

        /** 设置自定义调度线程池 */
        public Builder scheduler(ScheduledExecutorService scheduler) {
            this.scheduler = scheduler;
            return this;
        }

        /** 设置调度线程池大小 */
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
            ExecutorService ex = executor != null
                    ? executor
                    : createDefaultExecutor(plugin, poolSize, threadFactory);
            ScheduledExecutorService sch = scheduler != null
                    ? scheduler
                    : createDefaultScheduler(plugin, schedulerSize, schedulerFactory);
            return new AsyncTaskManager(plugin, logger, ex, sch);
        }
    }

    // ================= 未调用的内容（如有）可在此处注释隐藏 =================
    // （目前所有方法均被调用，无需额外隐藏）
}
