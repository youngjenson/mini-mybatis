package cn.jens.mybatis;

import cn.jens.demo.entity.User;
import cn.jens.mybatis.exception.PersistenceException;
import cn.jens.mybatis.scripting.BoundSql;
import cn.jens.mybatis.scripting.ParameterMapping;
import cn.jens.mybatis.scripting.SqlParser;
import cn.jens.mybatis.type.JdbcType;
import cn.jens.mybatis.type.handler.StringTypeHandler;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlParserTest {

    @Test
    void shouldParseNamedMapParametersInOrder() {
        BoundSql boundSql = SqlParser.parse(
                "select * from user where name = #{name} and age = #{age}",
                Map.of("name", "Alice", "age", 20)
        );

        assertEquals("select * from user where name = ? and age = ?", boundSql.getSql());
        assertEquals("Alice", boundSql.getParameterMappings().get(0).value());
        assertEquals(20, boundSql.getParameterMappings().get(1).value());
    }

    @Test
    void shouldReadBeanProperty() {
        User user = new User();
        user.setId(7);

        BoundSql boundSql = SqlParser.parse(
                "select * from user where id = #{id}",
                user
        );

        assertEquals(7, boundSql.getParameterMappings().getFirst().value());
    }

    @Test
    void shouldParseTypeHandlerOptions() {
        BoundSql boundSql = SqlParser.parse(
                "select * from user where age = #{age, javaType=integer, jdbcType=INTEGER} "
                        + "and name = #{name, typeHandler="
                        + StringTypeHandler.class.getName() + "}",
                Map.of("age", 20, "name", "Alice")
        );

        ParameterMapping age = boundSql.getParameterMappings().get(0);
        ParameterMapping name = boundSql.getParameterMappings().get(1);
        assertEquals(Integer.class, age.javaType());
        assertEquals(JdbcType.INTEGER, age.jdbcType());
        assertInstanceOf(StringTypeHandler.class, name.typeHandler());
    }

    @Test
    void shouldRejectUnknownTypeHandlerOption() {
        assertThrows(
                PersistenceException.class,
                () -> SqlParser.parse(
                        "select * from user where id = #{id, unknown=value}",
                        Map.of("id", 1)
                )
        );
    }
}
