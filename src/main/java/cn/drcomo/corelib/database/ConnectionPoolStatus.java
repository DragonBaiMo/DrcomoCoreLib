package cn.drcomo.corelib.database;

/**
 * 连接池状态信息。
 */
public class ConnectionPoolStatus {
    private final int totalConnections;
    private final int activeConnections;
    private final int idleConnections;

    public ConnectionPoolStatus(int totalConnections, int activeConnections, int idleConnections) {
        this.totalConnections = totalConnections;
        this.activeConnections = activeConnections;
        this.idleConnections = idleConnections;
    }

    public int getTotalConnections() {
        return totalConnections;
    }

    public int getActiveConnections() {
        return activeConnections;
    }

    public int getIdleConnections() {
        return idleConnections;
    }
}
