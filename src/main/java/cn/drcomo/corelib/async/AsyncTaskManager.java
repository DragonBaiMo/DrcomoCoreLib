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
 */
public class AsyncTaskManager {

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(1);

    private final Plugin plugin;
    private final DebugUtil logger;
    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;

    /**
     * 构造异步任务管理器。
     *
     * @param plugin 插件实例
     * @param logger DebugUtil 日志工具
     */
    public AsyncTaskManager(Plugin plugin, DebugUtil logger) {
        this.plugin = plugin;
        this.logger = logger;
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, plugin.getName() + "-Async-" + THREAD_COUNTER.getAndIncrement());
            t.setDaemon(true);
            return t;
        });
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, plugin.getName() + "-Scheduler-" + THREAD_COUNTER.getAndIncrement());
            t.setDaemon(true);
            return t;
        });
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
}
