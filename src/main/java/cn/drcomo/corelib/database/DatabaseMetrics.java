package cn.drcomo.corelib.database;

/**
 * 数据库运行统计信息。
 */
public class DatabaseMetrics {
    private final long borrowedConnections;
    private final long executedStatements;
    private final long totalExecutionTimeMillis;

    public DatabaseMetrics(long borrowedConnections, long executedStatements, long totalExecutionTimeMillis) {
        this.borrowedConnections = borrowedConnections;
        this.executedStatements = executedStatements;
        this.totalExecutionTimeMillis = totalExecutionTimeMillis;
    }

    public long getBorrowedConnections() {
        return borrowedConnections;
    }

    public long getExecutedStatements() {
        return executedStatements;
    }

    public long getTotalExecutionTimeMillis() {
        return totalExecutionTimeMillis;
    }

    /**
     * 获取平均单条语句执行耗时。
     *
     * @return 平均耗时，单位毫秒
     */
    public long getAverageExecutionTimeMillis() {
        return executedStatements == 0 ? 0 : totalExecutionTimeMillis / executedStatements;
    }
}
