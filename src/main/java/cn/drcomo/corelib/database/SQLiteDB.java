package cn.drcomo.corelib.database;

import org.bukkit.plugin.Plugin;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * SQLite 数据库工具，管理连接、初始化表结构以及基础 CRUD 操作。
 * <p>自 1.1 起内部使用 HikariCP 维护连接池，确保在多线程环境下安全获取连接。
 * 同时提供一系列异步方法，便于在后台执行数据库操作。</p>
 */
public class SQLiteDB {

    private final Plugin plugin;
    private final String dbFilePath;
    private final List<String> initScripts;
    private HikariDataSource dataSource;
    private final ThreadLocal<Connection> txConnection = new ThreadLocal<>();
    private final SQLiteDBConfig config = new SQLiteDBConfig();

    /**
     * 构造方法。
     *
     * @param plugin       Bukkit 插件实例，由调用者提供
     * @param relativePath 数据库文件相对于插件数据目录的路径
     * @param scripts      初始化或升级表结构的 SQL 脚本路径列表
     */
    public SQLiteDB(Plugin plugin, String relativePath, List<String> scripts) {
        this.plugin = plugin;
        this.dbFilePath = new File(plugin.getDataFolder(), relativePath).getAbsolutePath();
        this.initScripts = scripts != null ? scripts : new ArrayList<>();
    }

    /**
     * 获取连接池配置实例，可在调用 {@link #connect()} 前调整参数。
     *
     * @return 当前配置实例
     */
    public SQLiteDBConfig getConfig() {
        return config;
    }

    /**
     * 打开数据库连接。若所在目录不存在将会自动创建。
     *
     * @throws SQLException 连接或连接配置失败时抛出
     */
    public void connect() throws SQLException {
        ensureDirectory();
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl("jdbc:sqlite:" + dbFilePath);
        hikari.setPoolName(plugin.getName() + "-SQLitePool");
        hikari.setMaximumPoolSize(config.getMaximumPoolSize());
        hikari.setConnectionTestQuery(config.getConnectionTestQuery());
        this.dataSource = new HikariDataSource(hikari);
    }

    /**
     * 关闭并释放连接池资源。
     * 如果已关闭则忽略。
     */
    /**
     * 从连接池获取一个新的数据库连接。调用方使用完毕后应自行关闭。
     *
     * @return Connection 连接对象
     * @throws SQLException 获取失败时抛出
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("数据源未初始化，请先调用 connect()。");
        }
        return dataSource.getConnection();
    }

    public void disconnect() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    /**
     * 执行构造时传入的 SQL 脚本，用于初始化或升级 schema。
     * 不存在的脚本将被跳过，并在最后提交事务（如果手动提交模式）。
     *
     * @throws SQLException 执行 SQL 语句时出现错误
     * @throws IOException  脚本读取失败时抛出
     */
    public void initializeSchema() throws SQLException, IOException {
        Connection conn = borrowConnection();
        try {
            for (String path : initScripts) {
                try (InputStream in = plugin.getResource(path)) {
                    if (in != null) {
                        executeSqlScript(conn, in);
                    }
                }
            }
            commitIfNeeded(conn);
        } finally {
            returnConnection(conn);
        }
    }

    /**
     * 同步执行 INSERT/UPDATE/DELETE 语句。
     *
     * @param sql    带有 '?' 占位符的 SQL 语句
     * @param params 占位符对应的参数列表
     * @return 变更的行数
     * @throws SQLException 执行失败时抛出
     */
    public int executeUpdate(String sql, Object... params) throws SQLException {
        Connection conn = borrowConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setParams(ps, params);
            int rows = ps.executeUpdate();
            commitIfNeeded(conn);
            return rows;
        } finally {
            returnConnection(conn);
        }
    }

    /**
     * 基于指定 SQL 进行单行查询。
     *
     * @param sql     带占位符的 SQL 语句
     * @param handler 结果集解析回调
     * @param params  占位符参数
     * @param <T>     返回对象类型
     * @return 如果无结果则返回 {@code null}
     * @throws SQLException 执行期间失败时抛出
     */
    public <T> T queryOne(String sql, ResultSetHandler<T> handler, Object... params) throws SQLException {
        Connection conn = borrowConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? handler.handle(rs) : null;
            }
        } finally {
            returnConnection(conn);
        }
    }

    /**
     * 基于指定 SQL 查询多行数据。
     *
     * @param sql     带占位符的 SQL 语句
     * @param handler 结果集解析回调
     * @param params  占位符参数
     * @param <T>     返回实体类型
     * @return 查询结果列表，从不为 {@code null}
     * @throws SQLException 执行期间失败时抛出
     */
    public <T> List<T> queryList(String sql, ResultSetHandler<T> handler, Object... params) throws SQLException {
        List<T> list = new ArrayList<>();
        Connection conn = borrowConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(handler.handle(rs));
                }
            }
        } finally {
            returnConnection(conn);
        }
        return list;
    }

    /**
     * 在单个事务中执行多条数据库操作，失败时回滚。
     *
     * @param callback 事务逻辑回调
     * @throws SQLException 事务中出现错误时抛出
     */
    public void transaction(SQLRunnable callback) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            txConnection.set(conn);
            boolean oldAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                callback.run(this);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(oldAutoCommit);
                txConnection.remove();
            }
        }
    }

    /* 异步版本 */

    public CompletableFuture<Void> connectAsync() {
        return runAsync(() -> { connect(); return null; });
    }

    public CompletableFuture<Void> initializeSchemaAsync() {
        return runAsync(() -> { initializeSchema(); return null; });
    }

    public CompletableFuture<Integer> executeUpdateAsync(String sql, Object... params) {
        return runAsync(() -> executeUpdate(sql, params));
    }

    public <T> CompletableFuture<T> queryOneAsync(String sql, ResultSetHandler<T> handler, Object... params) {
        return runAsync(() -> queryOne(sql, handler, params));
    }

    public <T> CompletableFuture<List<T>> queryListAsync(String sql, ResultSetHandler<T> handler, Object... params) {
        return runAsync(() -> queryList(sql, handler, params));
    }

    public CompletableFuture<Void> transactionAsync(SQLRunnable callback) {
        return runAsync(() -> { transaction(callback); return null; });
    }

    /* ------- 私有方法 & 辅助类 ------- */

    /**
     * 通用异步执行封装，自动捕获异常并封装为 CompletionException。
     */
    private <T> CompletableFuture<T> runAsync(SqlCallable<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        });
    }

    /** 私有功能接口，用于异步方法封装 */
    private interface SqlCallable<T> {
        T call() throws Exception;
    }

    /**
     * 从输入流读取 SQL 脚本并逐条执行。
     *
     * @param conn 数据库连接
     * @param in   SQL 脚本输入流
     */
    private void executeSqlScript(Connection conn, InputStream in) throws IOException, SQLException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            String sql = reader.lines().collect(Collectors.joining("\n"));
            for (String stmt : sql.split(";")) {
                String trimmed = stmt.trim();
                if (!trimmed.isEmpty()) {
                    try (PreparedStatement ps = conn.prepareStatement(trimmed)) {
                        ps.executeUpdate();
                    }
                }
            }
        }
    }

    /** 为 PreparedStatement 设置参数 */
    private void setParams(PreparedStatement ps, Object... params) throws SQLException {
        if (params != null) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
        }
    }

    /** 获取连接：如果在事务中返回同一连接，否则从池中获取新连接 */
    private Connection borrowConnection() throws SQLException {
        Connection conn = txConnection.get();
        return (conn != null) ? conn : dataSource.getConnection();
    }

    /** 归还连接：若非事务连接则关闭 */
    private void returnConnection(Connection conn) throws SQLException {
        if (txConnection.get() == null && conn != null) {
            conn.close();
        }
    }

    /** 根据连接自动提交设置决定是否手动提交 */
    private void commitIfNeeded(Connection conn) throws SQLException {
        if (!conn.getAutoCommit()) {
            conn.commit();
        }
    }

    /** 确保数据库文件所在目录存在 */
    private void ensureDirectory() {
        File file = new File(dbFilePath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
    }

    /**
     * 连接池配置。可在调用 {@link #connect()} 前调整以自定义 HikariCP 行为。
     */
    public static class SQLiteDBConfig {
        private int maximumPoolSize = 10;
        private String connectionTestQuery = "SELECT 1";

        /** 设置连接池最大线程数 */
        public SQLiteDBConfig maximumPoolSize(int size) {
            this.maximumPoolSize = size;
            return this;
        }

        /** 设置测试连接的查询语句 */
        public SQLiteDBConfig connectionTestQuery(String query) {
            this.connectionTestQuery = query;
            return this;
        }

        public int getMaximumPoolSize() {
            return maximumPoolSize;
        }

        public String getConnectionTestQuery() {
            return connectionTestQuery;
        }
    }

    /**
     * 结果集处理器，用于将 ResultSet 行转换为实体。
     *
     * @param <T> 转换后实体类型
     */
    public interface ResultSetHandler<T> {
        T handle(ResultSet rs) throws SQLException;
    }

    /**
     * 事务回调，在单个事务中执行多条更新操作。
     */
    public interface SQLRunnable {
        void run(SQLiteDB db) throws SQLException;
    }
}
