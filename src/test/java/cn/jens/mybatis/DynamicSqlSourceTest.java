package cn.jens.mybatis;

import cn.jens.demo.entity.User;
import cn.jens.mybatis.config.Configuration;
import cn.jens.mybatis.config.XmlMapperBuilder;
import cn.jens.mybatis.exception.PersistenceException;
import cn.jens.mybatis.mapping.MappedStatement;
import cn.jens.mybatis.scripting.BoundSql;
import cn.jens.mybatis.scripting.ParameterMapping;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DynamicSqlSourceTest {

    @Test
    void shouldRenderIfAndRemoveLeadingWhereConnector() {
        Configuration configuration = parse("""
                <mapper namespace="%s">
                    <select id="find" resultType="integer">
                        select id from user
                        <where>
                            <if test="name != null and name != ''">
                                name = #{name}
                            </if>
                            <if test="minAge != null and minAge >= 18">
                                AND age >= #{minAge}
                            </if>
                        </where>
                        order by id
                    </select>
                </mapper>
                """.formatted(DynamicMapper.class.getName()));
        MappedStatement statement = configuration.getMappedStatement(
                DynamicMapper.class.getName() + ".find"
        );
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("name", "Bob");
        parameters.put("minAge", 21);

        BoundSql boundSql = statement.getBoundSql(parameters);

        assertEquals(
                "select id from user WHERE name = ? AND age >= ? order by id",
                normalizeSql(boundSql.getSql())
        );
        assertEquals(List.of("Bob", 21), parameterValues(boundSql));
    }

    @Test
    void shouldOmitWhereWhenEveryConditionIsFalse() {
        Configuration configuration = parse("""
                <mapper namespace="%s">
                    <select id="find" resultType="integer">
                        select id from user
                        <where>
                            <if test="name != null">AND name = #{name}</if>
                        </where>
                        order by id
                    </select>
                </mapper>
                """.formatted(DynamicMapper.class.getName()));
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("name", null);

        BoundSql boundSql = configuration.getMappedStatement(
                DynamicMapper.class.getName() + ".find"
        ).getBoundSql(parameters);

        assertEquals("select id from user order by id", normalizeSql(boundSql.getSql()));
        assertEquals(List.of(), parameterValues(boundSql));
    }

    @Test
    void shouldExpandForeachIntoUniquePreparedStatementParameters() {
        Configuration configuration = parse("""
                <mapper namespace="%s">
                    <select id="findByIds" resultType="integer">
                        select id from user where
                        <foreach collection="ids" item="id" index="position"
                                 open="id in (" separator="," close=")">
                            #{id}
                        </foreach>
                    </select>
                </mapper>
                """.formatted(DynamicMapper.class.getName()));

        BoundSql boundSql = configuration.getMappedStatement(
                DynamicMapper.class.getName() + ".findByIds"
        ).getBoundSql(Map.of("ids", List.of(3, 1, 2)));

        assertEquals(
                "select id from user where id in (?,?,?)",
                normalizeSql(boundSql.getSql())
        );
        assertEquals(List.of(3, 1, 2), parameterValues(boundSql));
        assertEquals(
                List.of("__frch_id_0", "__frch_id_1", "__frch_id_2"),
                boundSql.getParameterMappings().stream()
                        .map(ParameterMapping::property)
                        .toList()
        );
    }

    @Test
    void shouldRejectNullForeachCollectionUnlessNullable() {
        Configuration configuration = parse("""
                <mapper namespace="%s">
                    <select id="findByIds" resultType="integer">
                        select id from user where
                        <foreach collection="ids" item="id"
                                 open="id in (" separator="," close=")">
                            #{id}
                        </foreach>
                    </select>
                </mapper>
                """.formatted(DynamicMapper.class.getName()));
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("ids", null);

        assertThrows(
                PersistenceException.class,
                () -> configuration.getMappedStatement(
                        DynamicMapper.class.getName() + ".findByIds"
                ).getBoundSql(parameters)
        );
    }

    @Test
    void shouldResolveNestedPropertiesForEachIteration() {
        Configuration configuration = parse("""
                <mapper namespace="%s">
                    <insert id="batchInsert">
                        insert into user (id, name) values
                        <foreach collection="users" item="user" separator=",">
                            (#{user.id}, #{user.name})
                        </foreach>
                    </insert>
                </mapper>
                """.formatted(DynamicMapper.class.getName()));

        BoundSql boundSql = configuration.getMappedStatement(
                DynamicMapper.class.getName() + ".batchInsert"
        ).getBoundSql(Map.of("users", List.of(
                user(3, "Carol"),
                user(4, "David")
        )));

        assertEquals(
                "insert into user (id, name) values (?, ?),(?, ?)",
                normalizeSql(boundSql.getSql())
        );
        assertEquals(List.of(3, "Carol", 4, "David"), parameterValues(boundSql));
    }

    @Test
    void shouldRejectUnsupportedDynamicElement() {
        String xml = """
                <mapper namespace="%s">
                    <select id="invalid" resultType="integer">
                        select id from user
                        <choose/>
                    </select>
                </mapper>
                """.formatted(DynamicMapper.class.getName());

        assertThrows(PersistenceException.class, () -> parse(xml));
    }

    private List<Object> parameterValues(BoundSql boundSql) {
        return boundSql.getParameterMappings().stream()
                .map(ParameterMapping::value)
                .toList();
    }

    private String normalizeSql(String sql) {
        return sql.replaceAll("\\s+", " ").strip();
    }

    private User user(int id, String name) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        return user;
    }

    private Configuration parse(String xml) {
        Configuration configuration = new Configuration();
        try (var inputStream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            new XmlMapperBuilder(configuration).parse(inputStream, "dynamic-test-mapper.xml");
        } catch (Exception e) {
            if (e instanceof PersistenceException persistenceException) {
                throw persistenceException;
            }
            throw new IllegalStateException(e);
        }
        return configuration;
    }

    private interface DynamicMapper {
    }
}
