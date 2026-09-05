package cn.jens.mybatis;

import cn.jens.demo.entity.User;
import cn.jens.mybatis.config.Configuration;
import cn.jens.mybatis.config.XmlMapperBuilder;
import cn.jens.mybatis.exception.PersistenceException;
import cn.jens.mybatis.mapping.MappedStatement;
import cn.jens.mybatis.mapping.SqlCommandType;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmlMapperBuilderTest {

    @Test
    void shouldParseResultMapAndSelectStatement() {
        String xml = """
                <mapper namespace="%s">
                    <cache/>
                    <resultMap id="userMap" type="%s">
                        <id property="id" column="user_id"/>
                        <result property="name" column="user_name"/>
                    </resultMap>
                    <select id="selectUser" resultMap="userMap" flushCache="true">
                        select id as user_id, name as user_name from user
                    </select>
                    <select id="selectUserWithoutCache" resultMap="userMap" useCache="false">
                        select id as user_id, name as user_name from user
                    </select>
                </mapper>
                """.formatted(SampleMapper.class.getName(), User.class.getName());

        Configuration configuration = parse(xml);
        String statementId = SampleMapper.class.getName() + ".selectUser";
        MappedStatement statement = configuration.getMappedStatement(statementId);

        assertEquals(SqlCommandType.SELECT, statement.sqlCommandType());
        assertEquals(User.class, statement.resultType());
        assertNotNull(statement.resultMap());
        assertTrue(statement.flushCacheRequired());
        assertTrue(statement.useCache());
        assertNotNull(configuration.getCache(SampleMapper.class.getName()));
        assertFalse(configuration.getMappedStatement(
                SampleMapper.class.getName() + ".selectUserWithoutCache"
        ).useCache());
        assertEquals("user_name", statement.resultMap().resultMappings().get(1).column());
    }

    @Test
    void shouldRejectSelectWithResultTypeAndResultMap() {
        String xml = """
                <mapper namespace="%s">
                    <resultMap id="userMap" type="%s">
                        <id property="id" column="id"/>
                    </resultMap>
                    <select id="invalid" resultMap="userMap" resultType="integer">
                        select id from user
                    </select>
                </mapper>
                """.formatted(SampleMapper.class.getName(), User.class.getName());

        assertThrows(PersistenceException.class, () -> parse(xml));
    }

    private Configuration parse(String xml) {
        Configuration configuration = new Configuration();
        try (var inputStream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            new XmlMapperBuilder(configuration).parse(inputStream, "inline-test-mapper.xml");
        } catch (Exception e) {
            if (e instanceof PersistenceException persistenceException) {
                throw persistenceException;
            }
            throw new IllegalStateException(e);
        }
        return configuration;
    }

    private interface SampleMapper {
    }
}
