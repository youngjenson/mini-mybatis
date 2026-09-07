package cn.jens.mybatis;

import cn.jens.demo.type.EmailAddress;
import cn.jens.demo.typehandler.EmailAddressTypeHandler;
import cn.jens.mybatis.config.Configuration;
import cn.jens.mybatis.config.XmlConfigBuilder;
import cn.jens.mybatis.datasource.PooledDataSource;
import cn.jens.mybatis.datasource.SimpleDataSource;
import cn.jens.mybatis.exception.PersistenceException;
import cn.jens.mybatis.type.JdbcType;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class XmlConfigBuilderTest {

    @Test
    void shouldRegisterTypeHandlerAndJdbcTypeForNull() {
        String xml = """
                <configuration>
                    <settings>
                        <setting name="jdbcTypeForNull" value="NULL"/>
                    </settings>
                    <typeHandlers>
                        <typeHandler handler="%s"/>
                    </typeHandlers>
                    <dataSource>
                        <property name="driver" value="com.mysql.cj.jdbc.Driver"/>
                        <property name="url" value="jdbc:mysql://127.0.0.1:3306/test"/>
                        <property name="username" value="root"/>
                        <property name="password" value="root"/>
                    </dataSource>
                </configuration>
                """.formatted(EmailAddressTypeHandler.class.getName());

        Configuration configuration = new XmlConfigBuilder().parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))
        );

        assertEquals(JdbcType.NULL, configuration.getJdbcTypeForNull());
        assertInstanceOf(SimpleDataSource.class, configuration.getDataSource());
        assertInstanceOf(
                EmailAddressTypeHandler.class,
                configuration.getTypeHandlerRegistry().getTypeHandler(
                        EmailAddress.class,
                        JdbcType.VARCHAR
                )
        );
    }

    @Test
    void shouldCreatePooledDataSourceAndReadPoolOptions() {
        String xml = """
                <configuration>
                    <dataSource type="POOLED">
                        <property name="url" value="jdbc:unknown:test"/>
                        <property name="poolMaximumActiveConnections" value="4"/>
                        <property name="poolMaximumIdleConnections" value="2"/>
                        <property name="poolMaximumCheckoutTime" value="3000"/>
                        <property name="poolTimeToWait" value="500"/>
                        <property name="poolMaximumLocalBadConnectionTolerance" value="1"/>
                        <property name="poolPingEnabled" value="true"/>
                        <property name="poolPingQuery" value="SELECT 1"/>
                        <property name="poolPingConnectionsNotUsedFor" value="1000"/>
                    </dataSource>
                </configuration>
                """;

        Configuration configuration = parse(xml);
        PooledDataSource dataSource = assertInstanceOf(
                PooledDataSource.class,
                configuration.getDataSource()
        );

        assertEquals(4, dataSource.getOptions().maximumActiveConnections());
        assertEquals(2, dataSource.getOptions().maximumIdleConnections());
        assertEquals(3000L, dataSource.getOptions().maximumCheckoutTimeMillis());
        assertEquals(500L, dataSource.getOptions().timeToWaitMillis());
        assertEquals(1, dataSource.getOptions().maximumLocalBadConnectionTolerance());
        assertEquals("SELECT 1", dataSource.getOptions().pingQuery());
        dataSource.close();
    }

    @Test
    void shouldRejectUnsupportedDataSourceType() {
        String xml = """
                <configuration>
                    <dataSource type="UNKNOWN">
                        <property name="url" value="jdbc:unknown:test"/>
                    </dataSource>
                </configuration>
                """;

        PersistenceException exception = assertThrows(
                PersistenceException.class,
                () -> parse(xml)
        );

        assertEquals("Unsupported dataSource type: UNKNOWN", exception.getMessage());
    }

    @Test
    void shouldRejectInvalidPoolProperty() {
        String xml = """
                <configuration>
                    <dataSource type="POOLED">
                        <property name="url" value="jdbc:unknown:test"/>
                        <property name="poolMaximumActiveConnections" value="many"/>
                    </dataSource>
                </configuration>
                """;

        assertThrows(PersistenceException.class, () -> parse(xml));
    }

    private Configuration parse(String xml) {
        return new XmlConfigBuilder().parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))
        );
    }
}
