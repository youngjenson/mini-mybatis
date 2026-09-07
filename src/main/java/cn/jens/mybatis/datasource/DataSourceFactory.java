package cn.jens.mybatis.datasource;

import cn.jens.mybatis.exception.PersistenceException;

import javax.sql.DataSource;
import java.util.Locale;
import java.util.Properties;

/**
 * 根据 XML 属性创建数据源。
 *
 * @author YumJens
 */
public class DataSourceFactory {

    public DataSource create(Properties properties) {
        return create("UNPOOLED", properties);
    }

    public DataSource create(String type, Properties properties) {
        if (properties == null) {
            throw new PersistenceException("DataSource properties must not be null");
        }
        String driver = properties.getProperty("driver");
        if (driver != null && !driver.isBlank()) {
            loadDriver(driver);
        }
        DataSource unpooledDataSource = new SimpleDataSource(
                require(properties, "url"),
                properties.getProperty("username", ""),
                properties.getProperty("password", "")
        );
        String normalizedType = type == null || type.isBlank()
                ? "UNPOOLED"
                : type.trim().toUpperCase(Locale.ROOT);
        return switch (normalizedType) {
            case "UNPOOLED" -> unpooledDataSource;
            case "POOLED" -> new PooledDataSource(
                    unpooledDataSource,
                    parsePoolOptions(properties)
            );
            default -> throw new PersistenceException("Unsupported dataSource type: " + type);
        };
    }

    private void loadDriver(String driver) {
        try {
            Class.forName(driver);
        } catch (ClassNotFoundException e) {
            throw new PersistenceException("JDBC driver not found: " + driver, e);
        }
    }

    private String require(Properties properties, String name) {
        String value = properties.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new PersistenceException("Missing dataSource property: " + name);
        }
        return value;
    }

    private PoolOptions parsePoolOptions(Properties properties) {
        PoolOptions defaults = PoolOptions.defaults();
        try {
            return new PoolOptions(
                    intProperty(
                            properties,
                            "poolMaximumActiveConnections",
                            defaults.maximumActiveConnections()
                    ),
                    intProperty(
                            properties,
                            "poolMaximumIdleConnections",
                            defaults.maximumIdleConnections()
                    ),
                    longProperty(
                            properties,
                            "poolMaximumCheckoutTime",
                            defaults.maximumCheckoutTimeMillis()
                    ),
                    longProperty(
                            properties,
                            "poolTimeToWait",
                            defaults.timeToWaitMillis()
                    ),
                    intProperty(
                            properties,
                            "poolMaximumLocalBadConnectionTolerance",
                            defaults.maximumLocalBadConnectionTolerance()
                    ),
                    booleanProperty(
                            properties,
                            "poolPingEnabled",
                            defaults.pingEnabled()
                    ),
                    properties.getProperty("poolPingQuery", defaults.pingQuery()),
                    longProperty(
                            properties,
                            "poolPingConnectionsNotUsedFor",
                            defaults.pingConnectionsNotUsedForMillis()
                    )
            );
        } catch (IllegalArgumentException e) {
            throw new PersistenceException("Invalid pooled dataSource configuration", e);
        }
    }

    private int intProperty(Properties properties, String name, int defaultValue) {
        String value = properties.getProperty(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new PersistenceException(
                    "Invalid integer dataSource property " + name + ": " + value,
                    e
            );
        }
    }

    private long longProperty(Properties properties, String name, long defaultValue) {
        String value = properties.getProperty(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new PersistenceException(
                    "Invalid long dataSource property " + name + ": " + value,
                    e
            );
        }
    }

    private boolean booleanProperty(
            Properties properties,
            String name,
            boolean defaultValue) {
        String value = properties.getProperty(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String normalizedValue = value.trim();
        if (!"true".equalsIgnoreCase(normalizedValue)
                && !"false".equalsIgnoreCase(normalizedValue)) {
            throw new PersistenceException(
                    "Invalid boolean dataSource property " + name + ": " + value
            );
        }
        return Boolean.parseBoolean(normalizedValue);
    }
}
