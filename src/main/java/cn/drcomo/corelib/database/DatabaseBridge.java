package cn.drcomo.corelib.database;

import cn.drcomo.corelib.util.DebugUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

/**
 * MySQL 同步桥接工具。
 * <p>统一封装数据源创建与批量写入逻辑，便于插件在不重复配置 HikariCP 的情况下完成远端同步。</p>
 */
public class DatabaseBridge {

    private final DebugUtil debugUtil;
    private HikariDataSource dataSource;

    /**
     * 使用指定的日志工具创建数据库桥接器。
     *
     * @param debugUtil 用于输出调试信息的日志工具
     */
    public DatabaseBridge(DebugUtil debugUtil) {
        this.debugUtil = Objects.requireNonNull(debugUtil, "日志工具不能为空");
    }

    /**
     * 根据配置创建或替换 MySQL 数据源。
     *
     * @param config 包含连接信息与连接池参数的配置
     * @return 新建的 {@link HikariDataSource} 实例
     */
    public synchronized HikariDataSource createMysqlDataSource(YamlConfiguration config) {
        Objects.requireNonNull(config, "配置对象不能为空");
        String host = requireNonBlank(config.getString("host"), "host");
        int port = config.getInt("port");
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("port 配置不合法，请在 1-65535 范围内指定端口");
        }
        String database = requireNonBlank(config.getString("database"), "database");
        String username = requireNonBlank(config.getString("username"), "username");
        String password = config.getString("password");
        ConfigurationSection jdbcParams = config.getConfigurationSection("jdbc-parameters");
        if (jdbcParams == null) {
            jdbcParams = config.getConfigurationSection("jdbcParameters");
        }
        String jdbcUrl = buildJdbcUrl(host, port, database, jdbcParams);

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(username);
        if (password != null) {
            hikariConfig.setPassword(password);
        }
        String poolName = config.getString("pool-name");
        if (poolName == null) {
            poolName = config.getString("poolName");
        }
        if (poolName != null && !poolName.isBlank()) {
            hikariConfig.setPoolName(poolName.trim());
        }

        applyHikariOptions(hikariConfig, config.getConfigurationSection("hikari"));
        applyDataSourceProperties(hikariConfig, config.getConfigurationSection("data-source-properties"));
        applyDataSourceProperties(hikariConfig, config.getConfigurationSection("dataSourceProperties"));

        if (this.dataSource != null) {
            this.dataSource.close();
        }
        this.dataSource = new HikariDataSource(hikariConfig);
        debugUtil.info("MySQL 数据源已创建: " + jdbcUrl);
        return this.dataSource;
    }

    /**
     * 批量执行 REPLACE INTO 语句，将多行数据写入远端 MySQL。
     *
     * @param table 目标数据表名称，可包含 schema 前缀
     * @param rows  需要写入的行集合，每一行使用列名到列值的映射表示
     * @return 受到影响的行数（忽略数据库返回未知信息的批次）
     * @throws SQLException 当连接获取或 SQL 执行失败时抛出
     */
    public int batchReplace(String table, List<Map<String, Object>> rows) throws SQLException {
        if (dataSource == null) {
            throw new SQLException("数据源未初始化，请先调用 createMysqlDataSource");
        }
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        String qualifiedTable = qualifyTableName(table);
        List<String> columns = collectColumns(rows);
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("列集合为空，无法构造批量写入语句");
        }
        ensureColumnConsistency(rows, columns);
        String sql = buildReplaceSql(qualifiedTable, columns);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Map<String, Object> row : rows) {
                if (row == null) {
                    throw new IllegalArgumentException("批量写入参数包含 null 行");
                }
                for (int i = 0; i < columns.size(); i++) {
                    Object value = row.get(columns.get(i));
                    statement.setObject(i + 1, value);
                }
                statement.addBatch();
            }
            int[] results = statement.executeBatch();
            int affected = 0;
            for (int count : results) {
                if (count > 0) {
                    affected += count;
                } else if (count == Statement.SUCCESS_NO_INFO) {
                    affected += 1;
                }
            }
            debugUtil.debug("批量写入完成，表: " + qualifiedTable + "，行数: " + rows.size());
            return affected;
        } catch (SQLException e) {
            debugUtil.error("执行批量 REPLACE INTO 失败: " + e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 关闭并释放数据源资源。
     */
    public synchronized void close() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
            debugUtil.info("MySQL 数据源已释放");
        }
    }

    private void ensureColumnConsistency(List<Map<String, Object>> rows, List<String> columns) {
        Set<String> expected = new LinkedHashSet<>(columns);
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            Set<String> missing = new LinkedHashSet<>(expected);
            missing.removeAll(row.keySet());
            if (!missing.isEmpty()) {
                throw new IllegalArgumentException("批量写入行缺少列: " + String.join(", ", missing));
            }
            Set<String> extras = new LinkedHashSet<>(row.keySet());
            extras.removeAll(expected);
            if (!extras.isEmpty()) {
                throw new IllegalArgumentException("批量写入行包含未声明的额外列: " + String.join(", ", extras));
            }
        }
    }

    /**
     * 获取当前数据源，主要用于需要直接访问 HikariCP 时。
     *
     * @return 当前持有的数据源，若尚未创建则返回 {@code null}
     */
    public HikariDataSource getDataSource() {
        return dataSource;
    }

    private void applyHikariOptions(HikariConfig config, ConfigurationSection section) {
        if (section == null) {
            return;
        }
        if (section.contains("maximumPoolSize")) {
            config.setMaximumPoolSize(section.getInt("maximumPoolSize"));
        }
        if (section.contains("minimumIdle")) {
            config.setMinimumIdle(section.getInt("minimumIdle"));
        }
        if (section.contains("connectionTimeout")) {
            config.setConnectionTimeout(section.getLong("connectionTimeout"));
        }
        if (section.contains("idleTimeout")) {
            config.setIdleTimeout(section.getLong("idleTimeout"));
        }
        if (section.contains("maxLifetime")) {
            config.setMaxLifetime(section.getLong("maxLifetime"));
        }
        if (section.contains("validationTimeout")) {
            config.setValidationTimeout(section.getLong("validationTimeout"));
        }
    }

    private void applyDataSourceProperties(HikariConfig config, ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            String value = section.getString(key);
            if (value != null) {
                config.addDataSourceProperty(key, value);
            }
        }
    }

    private String buildJdbcUrl(String host, int port, String database, ConfigurationSection parameters) {
        StringBuilder builder = new StringBuilder("jdbc:mysql://")
                .append(host.trim())
                .append(":")
                .append(port)
                .append("/")
                .append(database.trim());
        if (parameters != null && !parameters.getKeys(false).isEmpty()) {
            builder.append("?");
            StringJoiner joiner = new StringJoiner("&");
            for (String key : parameters.getKeys(false)) {
                String value = parameters.getString(key);
                if (value != null) {
                    joiner.add(URLEncoder.encode(key, StandardCharsets.UTF_8) + "="
                            + URLEncoder.encode(value, StandardCharsets.UTF_8));
                }
            }
            builder.append(joiner);
        }
        return builder.toString();
    }

    private List<String> collectColumns(List<Map<String, Object>> rows) {
        Set<String> columns = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            if (row != null) {
                for (String column : row.keySet()) {
                    columns.add(validateColumn(column));
                }
            }
        }
        return new ArrayList<>(columns);
    }

    private String buildReplaceSql(String table, List<String> columns) {
        StringBuilder builder = new StringBuilder("REPLACE INTO ")
                .append(table)
                .append(" (");
        StringJoiner columnJoiner = new StringJoiner(", ");
        StringJoiner placeholderJoiner = new StringJoiner(", ");
        for (String column : columns) {
            columnJoiner.add("`" + column + "`");
            placeholderJoiner.add("?");
        }
        builder.append(columnJoiner)
                .append(") VALUES (")
                .append(placeholderJoiner)
                .append(")");
        return builder.toString();
    }

    private String qualifyTableName(String table) {
        String name = requireNonBlank(table, "table");
        String[] segments = name.split("\\.");
        StringJoiner joiner = new StringJoiner(".");
        for (String segment : segments) {
            String trimmed = segment.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("表名格式非法: " + table);
            }
            joiner.add("`" + validateIdentifier(trimmed) + "`");
        }
        return joiner.toString();
    }

    private String validateColumn(String column) {
        return validateIdentifier(requireNonBlank(column, "column"));
    }

    private String validateIdentifier(String identifier) {
        if (!identifier.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("标识符仅允许字母、数字或下划线: " + identifier);
        }
        return identifier;
    }

    private String requireNonBlank(String value, String key) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(key + " 配置不能为空");
        }
        return value.trim();
    }
}
