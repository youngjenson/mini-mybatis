package cn.jens.mybatis;

import cn.jens.mybatis.config.XmlConfigBuilder;
import cn.jens.mybatis.datasource.PooledDataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 使用原生 JDBC 建立真实 MySQL 行为基线。 */
@ResourceLock("mysql-test-user-table")
class TestJdbc {

    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        try (InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream("mini-mybatis-test-config.xml")) {
            dataSource = new XmlConfigBuilder().parse(inputStream).getDataSource();
        }
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("drop table if exists user");
            statement.execute(
                    "create table user (id int primary key, name varchar(64), age int)"
            );
            statement.execute("insert into user values (1, 'Alice', 20), (2, 'Bob', 25)");
        }
    }

    @AfterEach
    void tearDown() {
        if (dataSource instanceof PooledDataSource pooledDataSource) {
            pooledDataSource.close();
        }
    }

    @Test
    void shouldQueryRealRowsWithPreparedStatement() throws Exception {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select id, name, age from user where id = ?"
             )) {
            statement.setInt(1, 1);

            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals(1, resultSet.getInt("id"));
                assertEquals("Alice", resultSet.getString("name"));
                assertEquals(20, resultSet.getInt("age"));
                assertFalse(resultSet.next());
            }
        }
    }

    @Test
    void shouldTreatInjectionTextAsAPlainParameter() throws Exception {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select count(*) from user where name = ?"
             )) {
            statement.setString(1, "Alice' or 1 = 1 --");

            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals(0, resultSet.getInt(1));
            }
        }
    }

    private Connection openConnection() throws SQLException {
        return dataSource.getConnection();
    }
}
