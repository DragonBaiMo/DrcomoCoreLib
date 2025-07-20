package cn.drcomo.corelib.performance;

import cn.drcomo.corelib.util.DebugUtil;
import org.bukkit.plugin.Plugin;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.List;

/**
 * 性能采集工具，提供获取服务器 TPS、CPU 使用率、内存以及 GC 情况的方法。
 * <p>支持 Paper 和 Spigot 服务器，自动检测 TPS 获取功能的可用性。</p>
 */
public class PerformanceUtil {

    private final Plugin plugin;
    private final DebugUtil logger;
    private final boolean tpsSupported;
    private final Method getTpsMethod;

    /**
     * 通过构造函数注入依赖，并检测 TPS 功能支持情况。
     *
     * @param plugin Bukkit 插件实例
     * @param logger DebugUtil 实例
     */
    public PerformanceUtil(Plugin plugin, DebugUtil logger) {
        this.plugin = plugin;
        this.logger = logger;
        
        // 检测 Paper 的 getTPS() 方法是否可用
        Method tpsMethod = null;
        boolean supported = false;
        try {
            tpsMethod = plugin.getServer().getClass().getMethod("getTPS");
            supported = true;
            logger.info("检测到 Paper 服务器，TPS 功能已启用");
        } catch (NoSuchMethodException e) {
            logger.info("检测到 Spigot 服务器，TPS 功能不可用");
        } catch (Exception e) {
            logger.warn("TPS 功能检测失败: " + e.getMessage());
        }
        
        this.getTpsMethod = tpsMethod;
        this.tpsSupported = supported;
    }

    /**
     * 检查当前服务器是否支持 TPS 获取功能。
     *
     * @return true 如果支持 Paper 的 getTPS() 方法，false 表示仅支持 Spigot
     */
    public boolean isTpsSupported() {
        return tpsSupported;
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
        if (!tpsSupported || getTpsMethod == null) {
            // Spigot 服务器不支持 TPS 获取，返回特殊值
            return -1.0;
        }
        
        try {
            // 使用反射调用 Paper 的 getTPS() 方法
            Object result = getTpsMethod.invoke(plugin.getServer());
            if (result instanceof double[] tpsArray && tpsArray.length > 0) {
                return tpsArray[0];
            }
            logger.warn("TPS 返回值格式异常");
            return -1.0;
        } catch (Exception e) {
            logger.warn("反射调用 getTPS() 失败: " + e.getMessage());
            return -1.0;
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
