package cn.drcomo.corelib.async;

/**
 * 任务优先级枚举，数值越小优先级越高。
 */
public enum TaskPriority {
    /** 高优先级任务，优先执行 */
    HIGH(0),
    /** 普通优先级任务 */
    NORMAL(1),
    /** 低优先级任务，最后执行 */
    LOW(2);

    private final int level;

    TaskPriority(int level) {
        this.level = level;
    }

    /**
     * 获取内部优先级数值。
     *
     * @return 数值，越小越高
     */
    public int getLevel() {
        return level;
    }
}
