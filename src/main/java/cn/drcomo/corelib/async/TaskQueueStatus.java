package cn.drcomo.corelib.async;

/**
 * 任务队列当前状态。
 */
public class TaskQueueStatus {

    private final int highCount;
    private final int normalCount;
    private final int lowCount;

    /**
     * 构造队列状态。
     *
     * @param highCount   高优先级任务数量
     * @param normalCount 普通优先级任务数量
     * @param lowCount    低优先级任务数量
     */
    public TaskQueueStatus(int highCount, int normalCount, int lowCount) {
        this.highCount = highCount;
        this.normalCount = normalCount;
        this.lowCount = lowCount;
    }

    /**
     * 获取高优先级任务数量。
     */
    public int getHighCount() {
        return highCount;
    }

    /**
     * 获取普通优先级任务数量。
     */
    public int getNormalCount() {
        return normalCount;
    }

    /**
     * 获取低优先级任务数量。
     */
    public int getLowCount() {
        return lowCount;
    }

    /**
     * 获取队列总任务数。
     */
    public int getTotal() {
        return highCount + normalCount + lowCount;
    }

    @Override
    public String toString() {
        return "TaskQueueStatus{" +
                "高优先级=" + highCount +
                ", 普通优先级=" + normalCount +
                ", 低优先级=" + lowCount +
                '}';
    }
}
