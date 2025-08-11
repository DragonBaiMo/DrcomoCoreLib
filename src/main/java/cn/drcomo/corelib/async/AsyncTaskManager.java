package cn.drcomo.corelib.async;

import cn.drcomo.corelib.util.DebugUtil;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

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
    private final ThreadPoolExecutor priorityExecutor;

    /**
     * 各优先级任务计数，使用原子变量保证并发环境下的可见性与原子性。
     */
    private final AtomicInteger highCount = new AtomicInteger();
    private final AtomicInteger normalCount = new AtomicInteger();
    private final AtomicInteger lowCount = new AtomicInteger();

    /**
     * 使用默认线程池构造异步任务管理器。
     *
     * @param plugin 插件实例
     * @param logger 日志工具
     */
    public AsyncTaskManager(Plugin plugin, DebugUtil logger) {
        this(plugin, logger,
             createDefaultExecutor(plugin, 0, null),
             createDefaultScheduler(plugin, 1, null),
             createPriorityExecutor(plugin));
    }

    private AsyncTaskManager(Plugin plugin, DebugUtil logger,
                             ExecutorService executor,
                             ScheduledExecutorService scheduler,
                             ThreadPoolExecutor priorityExecutor) {
        this.plugin = plugin;
        this.logger = logger;
        this.executor = executor;
        this.scheduler = scheduler;
        this.priorityExecutor = priorityExecutor;
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
     * 提交带优先级的 Supplier 任务。
     *
     * @param supplier 任务提供者
     * @param priority 优先级
     * @param <T>      返回类型
     * @return Future 句柄
     */
    public <T> Future<T> submitWithPriority(Supplier<T> supplier, TaskPriority priority) {
        incrementCounter(priority);
        PrioritizedFutureTask<T> future = new PrioritizedFutureTask<>(
                wrapCallable(() -> supplier.get()), priority);
        priorityExecutor.execute(future);
        return future;
    }

    /**
     * 提交带优先级的 Runnable 任务。
     *
     * @param task     任务
     * @param priority 优先级
     * @return Future 句柄
     */
    public Future<?> runWithPriority(Runnable task, TaskPriority priority) {
        incrementCounter(priority);
        PrioritizedFutureTask<?> future = new PrioritizedFutureTask<>(wrapRunnable(task), null, priority);
        priorityExecutor.execute(future);
        return future;
    }

    /**
     * 获取优先级队列状态。
     * <p>直接读取原子计数器，避免遍历队列造成的性能开销。</p>
     *
     * @return TaskQueueStatus
     */
    public TaskQueueStatus getQueueStatus() {
        return new TaskQueueStatus(highCount.get(), normalCount.get(), lowCount.get());
    }

    /**
     * 取消正在调度或执行中的任务。
     * <p>
     * 若任务尚未开始执行，则会从队列中移除；
     * 若任务正在运行，则根据语义尝试取消，但不强制中断正在执行的线程。
     * </p>
     *
     * @param future 任务句柄 {@link Future}
     * @return true 表示成功取消，false 表示任务已完成、已取消或句柄为空
     */
    public boolean cancelTask(Future<?> future) {
        return future != null && !future.isCancelled() && future.cancel(false);
    }

    /**
     * 使用 CompletableFuture 执行 Supplier 异步任务。
     *
     * @param supplier 任务提供者
     * @param <T>      返回类型
     * @return CompletableFuture
     */
    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (Exception e) {
                logger.error("异步任务执行异常", e);
                throw e;
            }
        }, executor);
    }

    /**
     * 使用 CompletableFuture 异步运行 Runnable 任务。
     *
     * @param task 任务
     * @return CompletableFuture
     */
    public CompletableFuture<Void> runAsync(Runnable task) {
        return CompletableFuture.runAsync(() -> {
            try {
                task.run();
            } catch (Exception e) {
                logger.error("异步任务执行异常", e);
                throw e;
            }
        }, executor);
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
        priorityExecutor.shutdown();
    }

    /**
     * 获取注入的插件实例（只读访问，便于外部进行诊断或名称标识）。
     */
    public Plugin getPlugin() {
        return plugin;
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
     * 根据优先级递增计数器。
     *
     * @param priority 任务优先级
     */
    private void incrementCounter(TaskPriority priority) {
        switch (priority) {
            case HIGH -> {
                int count = highCount.incrementAndGet();
                logger.debug("高优先级任务计数 +1，当前：" + count);
            }
            case NORMAL -> {
                int count = normalCount.incrementAndGet();
                logger.debug("普通优先级任务计数 +1，当前：" + count);
            }
            case LOW -> {
                int count = lowCount.incrementAndGet();
                logger.debug("低优先级任务计数 +1，当前：" + count);
            }
        }
    }

    /**
     * 根据优先级递减计数器。
     *
     * @param priority 任务优先级
     */
    private void decrementCounter(TaskPriority priority) {
        switch (priority) {
            case HIGH -> {
                int count = highCount.decrementAndGet();
                logger.debug("高优先级任务已完成，剩余：" + count);
            }
            case NORMAL -> {
                int count = normalCount.decrementAndGet();
                logger.debug("普通优先级任务已完成，剩余：" + count);
            }
            case LOW -> {
                int count = lowCount.decrementAndGet();
                logger.debug("低优先级任务已完成，剩余：" + count);
            }
        }
    }

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
        // 替换为带超时回收能力的可伸缩线程池，降低空闲线程占用
        ThreadPoolExecutor cached = new ThreadPoolExecutor(
                0,
                Math.max(2, Runtime.getRuntime().availableProcessors()),
                60L,
                TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                factory
        );
        cached.allowCoreThreadTimeOut(true);
        return cached;
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
            ScheduledThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(schedulerSize, factory);
            pool.setRemoveOnCancelPolicy(true);
            pool.setKeepAliveTime(60L, TimeUnit.SECONDS);
            pool.allowCoreThreadTimeOut(true);
            return pool;
        }
        ScheduledThreadPoolExecutor single = new ScheduledThreadPoolExecutor(1, factory);
        single.setRemoveOnCancelPolicy(true);
        single.setKeepAliveTime(60L, TimeUnit.SECONDS);
        single.allowCoreThreadTimeOut(true);
        return single;
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
            ThreadPoolExecutor pr = createPriorityExecutor(plugin);
            return new AsyncTaskManager(plugin, logger, ex, sch, pr);
        }
    }

    /** 创建带优先级的线程池 */
    private static ThreadPoolExecutor createPriorityExecutor(Plugin plugin) {
        return new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new PriorityBlockingQueue<>(),
                createThreadFactory(plugin, "-Priority-")
        );
    }

    /**
     * 带优先级的 FutureTask，用于 PriorityBlockingQueue 排序。
     */
    private class PrioritizedFutureTask<V> extends FutureTask<V>
            implements Comparable<PrioritizedFutureTask<?>> {

        private final TaskPriority priority;

        PrioritizedFutureTask(Callable<V> callable, TaskPriority priority) {
            super(callable);
            this.priority = priority;
        }

        PrioritizedFutureTask(Runnable runnable, V result, TaskPriority priority) {
            super(runnable, result);
            this.priority = priority;
        }

        TaskPriority getPriority() {
            return priority;
        }

        @Override
        protected void done() {
            decrementCounter(priority);
        }

        @Override
        public int compareTo(PrioritizedFutureTask<?> o) {
            return Integer.compare(this.priority.getLevel(), o.priority.getLevel());
        }
    }

    // ================= 未调用的内容（如有）可在此处注释隐藏 =================
    // （目前所有方法均被调用，无需额外隐藏）
}
