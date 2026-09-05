package cn.jens.mybatis.datasource;

import cn.jens.mybatis.exception.PersistenceException;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * 根据 XML 属性创建数据源。
 *
 * @author YumJens
 */
public class DataSourceFactory {

    public DataSource create(Properties properties) {
        String driver = properties.getProperty("driver");
        if (driver != null && !driver.isBlank()) {
            loadDriver(driver);
        }
        return new SimpleDataSource(
                require(properties, "url"),
                properties.getProperty("username", ""),
                properties.getProperty("password", "")
        );
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
}
