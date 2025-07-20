package cn.drcomo.corelib.performance;

/**
 * 单次性能快照记录。
 *
 * @param tps       服务器当前 TPS
 * @param cpuUsage  当前进程 CPU 使用率 (0-1)
 * @param usedMemory 已使用内存字节数
 * @param maxMemory  最大可用内存字节数
 * @param gcCount   GC 总次数
 * @param gcTime    GC 总耗时（毫秒）
 */
public record PerformanceSnapshot(
        double tps,
        double cpuUsage,
        long usedMemory,
        long maxMemory,
        long gcCount,
        long gcTime) {
}
