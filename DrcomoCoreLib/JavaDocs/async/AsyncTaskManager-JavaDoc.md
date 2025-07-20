### `AsyncTaskManager.java`

**1. 概述 (Overview)**

  * **完整路径:** `cn.drcomo.corelib.async.AsyncTaskManager`
  * **核心职责:** 提供基于线程池的异步任务提交與調度能力，並在執行過程中自動捕獲並記錄例外。

**2. 如何实例化 (Initialization)**

  * **核心思想:** 每个子插件根据自身需求创建独立实例，并在插件关闭时调用 `shutdown()` 释放线程资源。
  * **构造函数:** `public AsyncTaskManager(Plugin plugin, DebugUtil logger)`
  * **代码示例:**
    ```java
    Plugin plugin = this;
    DebugUtil logger = new DebugUtil(plugin, DebugUtil.LogLevel.INFO);
    AsyncTaskManager manager = new AsyncTaskManager(plugin, logger);

    manager.submitAsync(() -> logger.info("run"));
    ```

**3. 公共API方法 (Public API Methods)**

  * #### `Future<?> submitAsync(Runnable task)`
      * **返回类型:** `Future<?>`
      * **功能描述:** 在内部线程池中异步执行一个 `Runnable`。
  * #### `<T> Future<T> submitAsync(Callable<T> task)`
      * **返回类型:** `Future<T>`
      * **功能描述:** 在内部线程池中执行可返回结果的任务。
  * #### `ScheduledFuture<?> scheduleAsync(Runnable task, long delay, TimeUnit unit)`
      * **返回类型:** `ScheduledFuture<?>`
      * **功能描述:** 延迟指定时间后执行任务。
  * #### `ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit)`
      * **返回类型:** `ScheduledFuture<?>`
      * **功能描述:** 以固定间隔重复执行任务。
  * #### `List<Future<?>> submitBatch(Collection<? extends Runnable> tasks)`
      * **返回类型:** `List<Future<?>>`
      * **功能描述:** 批量提交多个 `Runnable` 任务。
  * #### `void shutdown()`
      * **返回类型:** `void`
      * **功能描述:** 关闭内部线程池，停止接受新任务。
