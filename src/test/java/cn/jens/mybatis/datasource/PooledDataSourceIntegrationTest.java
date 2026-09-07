package cn.jens.mybatis.datasource;

import cn.jens.mybatis.annotation.Select;
import cn.jens.mybatis.config.Configuration;
import cn.jens.mybatis.config.XmlConfigBuilder;
import cn.jens.mybatis.session.DefaultSqlSessionFactory;
import cn.jens.mybatis.session.SqlSession;
import cn.jens.mybatis.session.SqlSessionFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock("mysql-test-pool-table")
class PooledDataSourceIntegrationTest {

    private PooledDataSource dataSource;

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        Configuration configuration;
        try (InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream("mini-mybatis-test-config.xml")) {
            configuration = new XmlConfigBuilder().parse(inputStream);
        }
        dataSource = assertInstanceOf(
                PooledDataSource.class,
                configuration.getDataSource()
        );
        configuration.addMapper(PoolProbeMapper.class);
        sqlSessionFactory = new DefaultSqlSessionFactory(configuration);

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("drop table if exists pooled_connection_test");
            statement.execute(
                    "create table pooled_connection_test "
                            + "(id int primary key, name varchar(64))"
            );
            statement.execute(
                    "insert into pooled_connection_test (id, name) values (1, 'Alice')"
            );
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (dataSource == null) {
            return;
        }
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("drop table if exists pooled_connection_test");
        } finally {
            dataSource.close();
        }
    }

    @Test
    void shouldReuseOnePhysicalConnectionAcrossSqlSessions() {
        long requestsBeforeTest = dataSource.getPoolState().requestCount();
        long firstConnectionId;
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            firstConnectionId = session.getMapper(PoolProbeMapper.class).connectionId();
        }

        assertEquals(0, dataSource.getPoolState().activeConnectionCount());
        assertEquals(1, dataSource.getPoolState().idleConnectionCount());

        long secondConnectionId;
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            secondConnectionId = session.getMapper(PoolProbeMapper.class).connectionId();
        }

        assertEquals(firstConnectionId, secondConnectionId);
        assertEquals(requestsBeforeTest + 2, dataSource.getPoolState().requestCount());
        assertEquals(1, dataSource.getPoolState().idleConnectionCount());
    }

    @Test
    void shouldRollbackAndResetConnectionBeforeReusingIt() throws Exception {
        Connection first = dataSource.getConnection();
        long firstConnectionId = connectionId(first);
        first.setAutoCommit(false);
        try (PreparedStatement statement = first.prepareStatement(
                "update pooled_connection_test set name = ? where id = ?"
        )) {
            statement.setString(1, "Changed but not committed");
            statement.setInt(2, 1);
            assertEquals(1, statement.executeUpdate());
        }
        first.close();

        Connection second = dataSource.getConnection();
        try {
            assertNotSame(first, second);
            assertEquals(firstConnectionId, connectionId(second));
            assertTrue(second.getAutoCommit());
            assertEquals("Alice", queryName(second));
        } finally {
            second.close();
        }
    }

    private long connectionId(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select connection_id()")) {
            assertTrue(resultSet.next());
            return resultSet.getLong(1);
        }
    }

    private String queryName(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "select name from pooled_connection_test where id = ?"
        )) {
            statement.setInt(1, 1);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getString(1);
            }
        }
    }

    private interface PoolProbeMapper {

        @Select("select connection_id()")
        long connectionId();
    }
}
