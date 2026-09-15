package cn.jens.mybatis;

import cn.jens.mybatis.config.Configuration;
import cn.jens.mybatis.config.XmlConfigBuilder;
import cn.jens.mybatis.config.XmlMapperBuilder;
import cn.jens.mybatis.datasource.PooledDataSource;
import cn.jens.mybatis.exception.PersistenceException;
import cn.jens.mybatis.executor.CachingExecutor;
import cn.jens.mybatis.executor.SimpleExecutor;
import cn.jens.mybatis.executor.statement.StatementHandler;
import cn.jens.mybatis.plugin.Intercepts;
import cn.jens.mybatis.plugin.Interceptor;
import cn.jens.mybatis.plugin.Invocation;
import cn.jens.mybatis.plugin.Signature;
import cn.jens.mybatis.session.DefaultSqlSessionFactory;
import cn.jens.mybatis.session.LocalCacheScope;
import cn.jens.mybatis.session.SqlSessionFactory;
import cn.jens.mybatis.transaction.JdbcTransaction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 真实 JDBC 查询，使用常量 SELECT 验证阻塞与事务，不修改数据库表。 */
class BlockingCacheIntegrationTest {

    private static final String QUERY = ProbeMapper.class.getName() + ".query";

    private DataSource dataSource;

    private SqlSessionFactory factory;

    private Configuration configuration;

    private QueryCounter counter;

    @BeforeEach
    void setUp() throws Exception {
        try (var input = getClass().getClassLoader().getResourceAsStream("mini-mybatis-test-config.xml")) {
            dataSource = new XmlConfigBuilder().parse(input).getDataSource();
        }
        configuration = new Configuration();
        configuration.setDataSource(dataSource);
        configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
        counter = new QueryCounter();
        configuration.addInterceptor(counter);
        String xml = """
                <mapper namespace="%s">
                    <cache blocking="true" size="2">
                        <property name="timeout" value="2000"/>
                    </cache>
                    <select id="query" resultType="integer">select 42</select>
                    <select id="refresh" resultType="integer" flushCache="true">select 43</select>
                    <select id="failure" resultType="integer">select mini_mybatis_missing_column</select>
                </mapper>
                """.formatted(ProbeMapper.class.getName());
        try (var input = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            new XmlMapperBuilder(configuration).parse(input, "blocking-inline-mapper.xml");
        }
        factory = new DefaultSqlSessionFactory(configuration);
    }

    @AfterEach
    void tearDown() {
        if (dataSource instanceof PooledDataSource pooled) {
            pooled.close();
        }
    }

    @Test
    void shouldQueryDatabaseOnceForConcurrentMisses() throws Exception {
        try (var owner = factory.openSession(); var executor = Executors.newSingleThreadExecutor()) {
            assertEquals(42, owner.<Integer>selectOne(QUERY, null));
            CountDownLatch started = new CountDownLatch(1);
            var reader = executor.submit(() -> {
                try (var session = factory.openSession()) {
                    started.countDown();
                    return session.<Integer>selectOne(QUERY, null);
                }
            });
            try {
                assertTrue(started.await(1, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> reader.get(100, TimeUnit.MILLISECONDS));
                assertEquals(1, counter.queries.get());
                owner.commit();
                assertEquals(42, reader.get(1, TimeUnit.SECONDS));
                assertEquals(1, counter.queries.get());
            } finally {
                owner.rollback();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldReleaseUncommittedMissOnRollbackOrClose(boolean rollback) {
        try (var owner = factory.openSession()) {
            assertEquals(42, owner.<Integer>selectOne(QUERY, null));
            if (rollback) {
                owner.rollback();
            }
        }
        try (var reader = factory.openSession(true)) {
            assertEquals(42, reader.<Integer>selectOne(QUERY, null));
        }
        assertEquals(2, counter.queries.get());
    }

    @Test
    void shouldAllowRepeatedMissAndFlushWithinTransaction() {
        try (var session = factory.openSession()) {
            assertEquals(42, session.<Integer>selectOne(QUERY, null));
            session.clearCache();
            assertEquals(42, session.<Integer>selectOne(QUERY, null));
            assertEquals(43, session.<Integer>selectOne(ProbeMapper.class.getName() + ".refresh", null));
            session.commit();
        }
        try (var session = factory.openSession(true)) {
            assertEquals(42, session.<Integer>selectOne(QUERY, null));
            assertEquals(42, session.<Integer>selectOne(QUERY, null));
        }
        assertEquals(4, counter.queries.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldReleaseMissAfterSqlFailure(boolean autoCommit) {
        String failure = ProbeMapper.class.getName() + ".failure";
        for (int attempt = 0; attempt < 2; attempt++) {
            try (var session = factory.openSession(autoCommit)) {
                assertThrows(PersistenceException.class, () -> session.selectOne(failure, null));
                if (autoCommit) {
                    // 同一自动提交会话可重试，前一次 SQL 失败必须已经解锁。
                    assertThrows(PersistenceException.class, () -> session.selectOne(failure, null));
                }
            }
        }
        assertEquals(autoCommit ? 4 : 2, counter.queries.get());
    }

    @Test
    void shouldReleaseMissImmediatelyWhenJdbcCommitFails() {
        JdbcTransaction transaction = new JdbcTransaction(dataSource, false) {
            @Override
            public void commit() {
                throw new PersistenceException("Simulated transaction commit failure");
            }
        };
        CachingExecutor executor = new CachingExecutor(
                new SimpleExecutor(transaction, configuration), configuration, false
        );
        try {
            executor.query(configuration.getMappedStatement(QUERY), null);
            assertThrows(PersistenceException.class, executor::commit);
            try (var next = factory.openSession(true)) {
                assertEquals(42, next.<Integer>selectOne(QUERY, null));
            }
            assertEquals(2, counter.queries.get());
        } finally {
            executor.close();
        }
    }

    private interface ProbeMapper {
    }

    @Intercepts(@Signature(type = StatementHandler.class, method = "query", args = {PreparedStatement.class}))
    public static class QueryCounter implements Interceptor {

        private final AtomicInteger queries = new AtomicInteger();

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            queries.incrementAndGet();
            return invocation.proceed();
        }
    }
}
