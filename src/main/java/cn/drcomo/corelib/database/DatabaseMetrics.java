package cn.drcomo.corelib.database;

/**
 * 数据库运行统计信息，记录连接借出次数、语句执行总数、累计耗时以及缓存命中次数。
 */
public class DatabaseMetrics {
    private final long borrowedConnections;
    private final long executedStatements;
    private final long totalExecutionTimeMillis;
    private final long statementCacheHits;

    public DatabaseMetrics(long borrowedConnections, long executedStatements, long totalExecutionTimeMillis, long statementCacheHits) {
        this.borrowedConnections = borrowedConnections;
        this.executedStatements = executedStatements;
        this.totalExecutionTimeMillis = totalExecutionTimeMillis;
        this.statementCacheHits = statementCacheHits;
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
     * 获取预编译语句缓存命中次数。
     *
     * @return 缓存命中次数
     */
    public long getStatementCacheHits() {
        return statementCacheHits;
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
