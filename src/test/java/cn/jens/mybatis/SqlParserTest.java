package cn.jens.mybatis;

import cn.jens.demo.entity.User;
import cn.jens.mybatis.scripting.BoundSql;
import cn.jens.mybatis.scripting.SqlParser;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
