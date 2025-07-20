package cn.drcomo.corelib.performance;

import cn.drcomo.corelib.util.DebugUtil;
import org.bukkit.plugin.Plugin;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

/**
 * 性能采集工具，提供获取服务器 TPS、CPU 使用率、内存以及 GC 情况的方法。
 */
public class PerformanceUtil {

    private final Plugin plugin;
    private final DebugUtil logger;

    /**
     * 通过构造函数注入依赖。
     *
     * @param plugin Bukkit 插件实例
     * @param logger DebugUtil 实例
     */
    public PerformanceUtil(Plugin plugin, DebugUtil logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    /**
     * 获取一次性能快照。
     *
     * @return {@link PerformanceSnapshot}
     */
    public PerformanceSnapshot snapshot() {
        double tps = getCurrentTps();
        double cpu = getProcessCpuLoad();
        long[] mem = getMemoryUsage();
        long[] gc = getGarbageCollectionInfo();

        return new PerformanceSnapshot(tps, cpu, mem[0], mem[1], gc[0], gc[1]);
    }

    private double getCurrentTps() {
        try {
            return plugin.getServer().getTPS()[0];
        } catch (Throwable t) {
            logger.warn("获取 TPS 失败: " + t.getMessage());
            return -1;
        }
    }

    private double getProcessCpuLoad() {
        var os = ManagementFactory.getOperatingSystemMXBean();
        if (os instanceof com.sun.management.OperatingSystemMXBean bean) {
            double load = bean.getProcessCpuLoad();
            return load < 0 ? 0 : load;
        }
        return 0;
    }

    private long[] getMemoryUsage() {
        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        long max = rt.maxMemory();
        return new long[]{used, max};
    }

    private long[] getGarbageCollectionInfo() {
        long count = 0;
        long time = 0;
        List<GarbageCollectorMXBean> list = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean b : list) {
            long c = b.getCollectionCount();
            long t = b.getCollectionTime();
            if (c > 0) count += c;
            if (t > 0) time += t;
        }
        return new long[]{count, time};
    }
}
