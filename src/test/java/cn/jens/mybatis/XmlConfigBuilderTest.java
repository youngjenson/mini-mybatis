package cn.jens.mybatis;

import cn.jens.demo.type.EmailAddress;
import cn.jens.demo.typehandler.EmailAddressTypeHandler;
import cn.jens.mybatis.config.Configuration;
import cn.jens.mybatis.config.XmlConfigBuilder;
import cn.jens.mybatis.type.JdbcType;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

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
        assertInstanceOf(
                EmailAddressTypeHandler.class,
                configuration.getTypeHandlerRegistry().getTypeHandler(
                        EmailAddress.class,
                        JdbcType.VARCHAR
                )
        );
    }
}
