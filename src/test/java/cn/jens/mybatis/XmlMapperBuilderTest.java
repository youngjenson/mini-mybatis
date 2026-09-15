package cn.jens.mybatis;

import cn.jens.demo.entity.User;
import cn.jens.mybatis.cache.Cache;
import cn.jens.mybatis.cache.CacheKey;
import cn.jens.mybatis.cache.BlockingCache;
import cn.jens.mybatis.cache.SynchronizedCache;
import cn.jens.mybatis.config.Configuration;
import cn.jens.mybatis.config.XmlMapperBuilder;
import cn.jens.mybatis.exception.PersistenceException;
import cn.jens.mybatis.mapping.MappedStatement;
import cn.jens.mybatis.mapping.ResultMapping;
import cn.jens.mybatis.mapping.SqlCommandType;
import cn.jens.mybatis.type.JdbcType;
import cn.jens.mybatis.type.handler.StringTypeHandler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
                        <result property="name" column="user_name" javaType="string"
                                jdbcType="VARCHAR" typeHandler="%s"/>
                    </resultMap>
                    <select id="selectUser" resultMap="userMap" flushCache="true">
                        select id as user_id, name as user_name from user
                    </select>
                    <select id="selectUserWithoutCache" resultMap="userMap" useCache="false">
                        select id as user_id, name as user_name from user
                    </select>
                </mapper>
                """.formatted(
                        SampleMapper.class.getName(),
                        User.class.getName(),
                        StringTypeHandler.class.getName()
                );

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
        ResultMapping nameMapping = statement.resultMap().resultMappings().get(1);
        assertEquals(String.class, nameMapping.javaType());
        assertEquals(JdbcType.VARCHAR, nameMapping.jdbcType());
        assertInstanceOf(StringTypeHandler.class, nameMapping.typeHandler());
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

    @Test
    void shouldDefaultToLruWith1024Entries() {
        Cache cache = parseCache("<cache/>");
        assertInstanceOf(SynchronizedCache.class, cache);
        for (int id = 0; id < 1024; id++) {
            cache.put(cacheKey(id), List.of(id));
        }
        assertEquals(List.of(0), cache.get(cacheKey(0)));
        cache.put(cacheKey(1024), List.of(1024));
        assertNull(cache.get(cacheKey(1)));
        assertEquals(List.of(0), cache.get(cacheKey(0)));
        assertEquals(List.of(1024), cache.get(cacheKey(1024)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"LRU", "FIFO"})
    void shouldApplyConfiguredEvictionAndSize(String eviction) {
        Cache cache = parseCache("<cache eviction=\"" + eviction + "\" size=\"2\"/>");
        cache.put(cacheKey(1), List.of(1));
        cache.put(cacheKey(2), List.of(2));
        cache.get(cacheKey(1));
        cache.put(cacheKey(3), List.of(3));

        assertNull(cache.get(cacheKey("LRU".equals(eviction) ? 2 : 1)));
        assertNotNull(cache.get(cacheKey("LRU".equals(eviction) ? 1 : 2)));
        assertEquals(List.of(3), cache.get(cacheKey(3)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "eviction=\"UNKNOWN\"", "eviction=\"\"", "size=\"0\"", "size=\"-1\"",
            "size=\"abc\"", "size=\"1.5\"", "size=\"2147483648\"", "size=\"\""
    })
    void shouldRejectInvalidCacheConfiguration(String attributes) {
        PersistenceException exception = assertThrows(PersistenceException.class,
                () -> parseCache("<cache " + attributes + "/>"));
        assertTrue(exception.getMessage().contains("inline-test-mapper.xml"));
        assertTrue(exception.getMessage().contains("Invalid <cache>"));
    }

    @Test
    void shouldParseBlockingCacheAndTimeout() {
        Cache cache = parseCache("""
                <cache eviction="FIFO" size="2" blocking="true">
                    <property name="timeout" value="20"/>
                </cache>
                """);
        assertInstanceOf(BlockingCache.class, cache);
        assertNull(cache.get(cacheKey(1)));
        try {
            assertThrows(PersistenceException.class, () -> cache.get(cacheKey(1)));
        } finally {
            cache.remove(cacheKey(1));
        }
        assertInstanceOf(SynchronizedCache.class, parseCache("<cache blocking=\"false\"/>"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<cache blocking=\"yes\"/>", "<cache blocking=\"\"/>",
            "<cache blocking=\"true\"><property name=\"timeout\" value=\"-1\"/></cache>",
            "<cache blocking=\"true\"><property name=\"timeout\" value=\"abc\"/></cache>",
            "<cache blocking=\"true\"><property name=\"timeout\" value=\"9223372036854775808\"/></cache>",
            "<cache blocking=\"true\"><property name=\"unknown\" value=\"1\"/></cache>",
            "<cache><property name=\"timeout\" value=\"1\"/></cache>",
            "<cache blocking=\"true\"><property name=\"timeout\" value=\"1\"/>"
                    + "<property name=\"timeout\" value=\"2\"/></cache>"
    })
    void shouldRejectInvalidBlockingConfiguration(String element) {
        PersistenceException error = assertThrows(PersistenceException.class, () -> parseCache(element));
        assertTrue(error.getMessage().contains("inline-test-mapper.xml"));
    }

    private Cache parseCache(String cacheElement) {
        return parse("<mapper namespace=\"" + SampleMapper.class.getName() + "\">"
                + cacheElement + "</mapper>").getCache(SampleMapper.class.getName());
    }

    private CacheKey cacheKey(int id) {
        return new CacheKey(SampleMapper.class.getName() + ".select", "select ?", List.of(id));
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
