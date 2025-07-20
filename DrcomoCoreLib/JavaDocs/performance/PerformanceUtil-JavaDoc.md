### `PerformanceUtil.java`

**1. 概述 (Overview)**

* **完整路径:** `cn.drcomo.corelib.performance.PerformanceUtil`
* **核心职责:** 提供方便的接口获取服务器运行时的性能指标，包括 TPS、CPU 使用率、内存占用以及 GC 统计信息。

**2. 初始化 (Initialization)**

```java
Plugin plugin = ...;
DebugUtil logger = new DebugUtil(plugin, DebugUtil.LogLevel.INFO);
PerformanceUtil perf = new PerformanceUtil(plugin, logger);
```

**3. 获取性能数据 (Collect metrics)**

* 调用 `snapshot()` 将即刻从服务器与 JVM 收集数据，返回 `PerformanceSnapshot` 记录：
  * `tps` - 服务器当前 TPS
  * `cpuUsage` - 当前进程 CPU 使用率 (0-1)
  * `usedMemory` - 已使用内存字节数
  * `maxMemory` - 最大可用内存字节数
  * `gcCount` - 垃圾回收总次数
  * `gcTime` - 垃圾回收总耗时（毫秒）

```java
PerformanceSnapshot snap = perf.snapshot();
logger.info("TPS: " + snap.tps());
```

**4. 使用场景示例 (Example usage)**

在定时任务中周期性调用 `snapshot()`，将结果写入日志或发送给管理员，实现轻量级性能监控。
