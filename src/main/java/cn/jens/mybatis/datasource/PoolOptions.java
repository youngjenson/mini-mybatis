package cn.jens.mybatis.datasource;

/**
 * 连接池配置。
 *
 * @param maximumActiveConnections 最大活跃连接数
 * @param maximumIdleConnections 最大空闲连接数
 * @param maximumCheckoutTimeMillis 连接允许被借出的最长时间
 * @param timeToWaitMillis 获取连接时的最长等待时间
 * @param maximumLocalBadConnectionTolerance 在空闲连接数基线之外额外容忍的坏连接数
 * @param pingEnabled 是否在复用前执行探活 SQL
 * @param pingQuery 探活 SQL
 * @param pingConnectionsNotUsedForMillis 空闲多久后才需要探活
 * @author YumJens
 */
public record PoolOptions(
        int maximumActiveConnections,
        int maximumIdleConnections,
        long maximumCheckoutTimeMillis,
        long timeToWaitMillis,
        int maximumLocalBadConnectionTolerance,
        boolean pingEnabled,
        String pingQuery,
        long pingConnectionsNotUsedForMillis) {

    public static final int DEFAULT_MAXIMUM_ACTIVE_CONNECTIONS = 10;

    public static final int DEFAULT_MAXIMUM_IDLE_CONNECTIONS = 5;

    public static final long DEFAULT_MAXIMUM_CHECKOUT_TIME_MILLIS = 20_000L;

    public static final long DEFAULT_TIME_TO_WAIT_MILLIS = 20_000L;

    public static final int DEFAULT_MAXIMUM_LOCAL_BAD_CONNECTION_TOLERANCE = 3;

    public static final String DEFAULT_PING_QUERY = "SELECT 1";

    public PoolOptions {
        if (maximumActiveConnections <= 0) {
            throw new IllegalArgumentException("maximumActiveConnections must be greater than 0");
        }
        if (maximumIdleConnections < 0
                || maximumIdleConnections > maximumActiveConnections) {
            throw new IllegalArgumentException(
                    "maximumIdleConnections must be between 0 and maximumActiveConnections"
            );
        }
        if (maximumCheckoutTimeMillis < 0) {
            throw new IllegalArgumentException(
                    "maximumCheckoutTimeMillis must not be negative"
            );
        }
        if (timeToWaitMillis <= 0) {
            throw new IllegalArgumentException("timeToWaitMillis must be greater than 0");
        }
        if (maximumLocalBadConnectionTolerance < 0) {
            throw new IllegalArgumentException(
                    "maximumLocalBadConnectionTolerance must not be negative"
            );
        }
        if (pingConnectionsNotUsedForMillis < 0) {
            throw new IllegalArgumentException(
                    "pingConnectionsNotUsedForMillis must not be negative"
            );
        }
        if (pingQuery == null || pingQuery.isBlank()) {
            if (pingEnabled) {
                throw new IllegalArgumentException("pingQuery must not be blank when ping is enabled");
            }
            pingQuery = DEFAULT_PING_QUERY;
        } else {
            pingQuery = pingQuery.trim();
        }
    }

    public static PoolOptions defaults() {
        return new PoolOptions(
                DEFAULT_MAXIMUM_ACTIVE_CONNECTIONS,
                DEFAULT_MAXIMUM_IDLE_CONNECTIONS,
                DEFAULT_MAXIMUM_CHECKOUT_TIME_MILLIS,
                DEFAULT_TIME_TO_WAIT_MILLIS,
                DEFAULT_MAXIMUM_LOCAL_BAD_CONNECTION_TOLERANCE,
                false,
                DEFAULT_PING_QUERY,
                0L
        );
    }
}
