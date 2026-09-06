package cn.jens.mybatis;

import cn.jens.demo.entity.User;
import cn.jens.demo.mapper.UserMapper;
import cn.jens.demo.mapper.UserXmlMapper;
import cn.jens.mybatis.exception.PersistenceException;
import cn.jens.mybatis.plugin.InvocationCountingInterceptor;
import cn.jens.mybatis.session.SqlSession;
import cn.jens.mybatis.session.SqlSessionFactory;
import cn.jens.mybatis.session.SqlSessionFactoryBuilder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock("mysql-test-user-table")
class MiniMyBatisIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    private DataSource dataSource;

    /**
     * 准备工作
     */
    @BeforeEach
    void setUp() throws Exception {
        sqlSessionFactory = buildFactory("mini-mybatis-config.xml");
        try (SqlSession session = sqlSessionFactory.openSession()) {
            dataSource = session.getConfiguration().getDataSource();
        }

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("drop table if exists user");
            statement.execute("create table user (id int primary key, name varchar(64), age int)");
            statement.execute("insert into user values (1, 'Alice', 20), (2, 'Bob', 25)");
        }
    }

    /**
     * 端到端测试 Mapper 查询
     */
    @Test
    void shouldExecuteMapperQueriesEndToEnd() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            UserMapper mapper = session.getMapper(UserMapper.class);

            List<User> users = mapper.selectList();
            User alice = mapper.selectById(1);
            User bob = mapper.selectByNameAndAge("Bob", 25);

            assertEquals(2, users.size());
            assertEquals("Alice", users.getFirst().getName());
            assertEquals(20, alice.getAge());
            assertEquals(2, bob.getId());
            assertEquals(2, mapper.count());
            assertNull(mapper.selectById(99));
        }
    }

    /**
     * 提交插入、更新和删除操作
     */
    @Test
    void shouldCommitInsertUpdateAndDelete() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            UserMapper mapper = session.getMapper(UserMapper.class);
            assertEquals(1, mapper.insert(user(3, "Carol", 30)));
            assertTrue(mapper.update(user(3, "Caroline", 31)));
            assertEquals("Caroline", mapper.selectById(3).getName());
            assertEquals(1, mapper.deleteById(1));
            session.commit();
        }

        try (SqlSession session = sqlSessionFactory.openSession()) {
            UserMapper mapper = session.getMapper(UserMapper.class);
            assertEquals(2, mapper.count());
            assertEquals(31, mapper.selectById(3).getAge());
            assertNull(mapper.selectById(1));
            assertFalse(mapper.update(user(99, "Nobody", 0)));
        }

        assertEquals("Caroline", queryUserNameDirectly(3));
        assertEquals(0, countUsersByIdDirectly(1));
    }

    /**
     * 显式回滚
     */
    @Test
    void shouldRollbackExplicitly() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            UserMapper mapper = session.getMapper(UserMapper.class);
            mapper.insert(user(3, "Carol", 30));
            session.rollback();
        }

        assertUserDoesNotExist(3);
    }

    /**
     * 隐式回滚
     */
    @Test
    void shouldRollbackUncommittedChangesWhenClosing() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            session.getMapper(UserMapper.class).insert(user(3, "Carol", 30));
        }

        assertUserDoesNotExist(3);
    }

    /**
     * Mini MyBatis 默认禁用自动提交，因此需要显式调用 commit() 方法来提交事务。
     */
    @Test
    void shouldPersistImmediatelyWhenAutoCommitIsEnabled() {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            session.getMapper(UserMapper.class).insert(user(3, "Carol", 30));
        }

        try (SqlSession session = sqlSessionFactory.openSession()) {
            assertEquals("Carol", session.getMapper(UserMapper.class).selectById(3).getName());
        }
    }

    /**
     * 测试在会话关闭后是否拒绝操作。
     */
    @Test
    void shouldRejectOperationsAfterSessionIsClosed() {
        SqlSession session = sqlSessionFactory.openSession();
        session.close();

        assertThrows(PersistenceException.class, () -> session.selectList("unknown", null));
        session.close();
    }

    /**
     * 测试当语句返回多行时，selectOne() 方法是否抛出异常。
     */
    @Test
    void shouldRejectSelectOneWhenStatementReturnsMultipleRows() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PersistenceException exception = assertThrows(
                    PersistenceException.class,
                    () -> session.selectOne(UserMapper.class.getName() + ".selectList", null)
            );

            assertTrue(exception.getMessage().contains("Expected one result"));
        }
    }

    /**
     * 测试 SQL 注入文本是否被视为普通数据。
     */
    @Test
    void shouldBindSqlInjectionTextAsPlainData() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            UserMapper mapper = session.getMapper(UserMapper.class);

            assertNull(mapper.selectByNameAndAge("Bob' or 1 = 1 --", 25));
            assertEquals(2, mapper.count());
        }
    }

    /**
     * 测试在后续语句失败时回滚先前的写操作。
     * @throws Exception
     */
    @Test
    void shouldRollbackEarlierWritesWhenLaterStatementFails() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            UserMapper mapper = session.getMapper(UserMapper.class);
            assertEquals(1, mapper.insert(user(3, "Carol", 30)));

            PersistenceException exception = assertThrows(
                    PersistenceException.class,
                    () -> mapper.insert(user(1, "Duplicate", 99))
            );
            assertTrue(exception.getMessage().contains("UserMapper.insert"));
        }

        assertEquals(0, countUsersByIdDirectly(3));
        assertEquals("Alice", queryUserNameDirectly(1));
    }

    /**
     * 测试 XML 映射器。
     */
    @Test
    void shouldExecuteXmlMapperWithResultMap() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            UserXmlMapper mapper = session.getMapper(UserXmlMapper.class);

            assertEquals("Alice", mapper.selectList().getFirst().getName());
            assertEquals(25, mapper.selectById(2).getAge());
            assertEquals(2, mapper.count());
            assertEquals(1, mapper.insert(user(3, "Carol", 30)));
            assertTrue(mapper.update(user(3, "Caroline", 31)));
            assertEquals(1, mapper.deleteById(1));
            assertEquals(2, mapper.count());
            session.commit();
        }
    }

    @Test
    void shouldExecuteIfAndWhereDynamicSql() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            UserXmlMapper mapper = session.getMapper(UserXmlMapper.class);

            assertEquals(2, mapper.selectDynamic(null, null).size());
            assertEquals("Bob", mapper.selectDynamic("Bob", null).getFirst().getName());
            assertEquals("Bob", mapper.selectDynamic(null, 21).getFirst().getName());
            assertTrue(mapper.selectDynamic("Alice", 21).isEmpty());
        }
    }

    @Test
    void shouldExecuteForeachDynamicSql() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            UserXmlMapper mapper = session.getMapper(UserXmlMapper.class);

            assertEquals(
                    List.of(1, 2),
                    mapper.selectByIds(List.of(2, 1)).stream().map(User::getId).toList()
            );
            assertEquals("Bob", mapper.selectByIds(List.of(2)).getFirst().getName());
            assertTrue(mapper.selectByIds(List.of()).isEmpty());
            assertTrue(mapper.selectByIds(null).isEmpty());
        }
    }

    /**
     * 测试在会话中缓存相同的查询。
     * @throws Exception
     */
    @Test
    void shouldCacheIdenticalQueryWithinSession() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            UserMapper mapper = session.getMapper(UserMapper.class);
            User firstResult = mapper.selectById(1);

            updateUserNameDirectly(1, "Alicia");
            User cachedResult = mapper.selectById(1);

            assertSame(firstResult, cachedResult);
            assertEquals("Alice", cachedResult.getName());

            session.clearCache();
            User refreshedResult = mapper.selectById(1);
            assertNotSame(cachedResult, refreshedResult);
            assertEquals("Alicia", refreshedResult.getName());
        }
    }

    /**
     * 测试 DML 操作是否清除缓存。
     */
    @Test
    void shouldClearLocalCacheAfterDml() {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            UserMapper mapper = session.getMapper(UserMapper.class);
            User cachedResult = mapper.selectById(1);

            mapper.update(user(1, "Alicia", 21));
            User refreshedResult = mapper.selectById(1);

            assertNotSame(cachedResult, refreshedResult);
            assertEquals("Alicia", refreshedResult.getName());
            assertEquals(21, refreshedResult.getAge());
        }
    }

    /**
     * 测试提交和回滚是否清除缓存。
     * @throws Exception
     */
    @Test
    void shouldClearLocalCacheAfterCommitAndRollback() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            UserMapper mapper = session.getMapper(UserMapper.class);
            mapper.selectById(1);

            updateUserNameDirectly(1, "Alicia");
            session.commit();
            assertEquals("Alicia", mapper.selectById(1).getName());

            updateUserNameDirectly(1, "Ally");
            session.rollback();
            assertEquals("Ally", mapper.selectById(1).getName());
        }
    }

    /**
     * 测试缓存作用域是否为语句。
     * @throws Exception
     */
    @Test
    void shouldSkipCacheWhenScopeIsStatement() throws Exception {
        SqlSessionFactory statementScopeFactory = buildFactory(
                "mini-mybatis-statement-cache-test-config.xml"
        );
        try (SqlSession session = statementScopeFactory.openSession(true)) {
            UserMapper mapper = session.getMapper(UserMapper.class);
            User firstResult = mapper.selectById(1);

            updateUserNameDirectly(1, "Alicia");
            User refreshedResult = mapper.selectById(1);

            assertNotSame(firstResult, refreshedResult);
            assertEquals("Alicia", refreshedResult.getName());
        }
    }

    /**
     * 测试在配置的 `<select>` 语句之前是否刷新缓存。
     * @throws Exception
     */
    @Test
    void shouldFlushCacheBeforeConfiguredSelect() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            UserXmlMapper mapper = session.getMapper(UserXmlMapper.class);
            assertEquals("Alice", mapper.selectById(1).getName());

            updateUserNameDirectly(1, "Alicia");

            assertEquals("Alicia", mapper.selectFreshById(1).getName());
            assertEquals("Alicia", mapper.selectById(1).getName());
        }
    }

    /**
     * 测试二级缓存是否在提交后共享。
     * @throws Exception
     */
    @Test
    void shouldShareSecondLevelCacheAcrossCommittedSessions() throws Exception {
        User firstResult;
        try (SqlSession session = sqlSessionFactory.openSession()) {
            firstResult = session.getMapper(UserXmlMapper.class).selectById(1);
            session.commit();
        }

        updateUserNameDirectly(1, "Alicia");

        try (SqlSession session = sqlSessionFactory.openSession()) {
            User cachedResult = session.getMapper(UserXmlMapper.class).selectById(1);

            assertSame(firstResult, cachedResult);
            assertEquals("Alice", cachedResult.getName());
        }
    }

    /**
     * 测试二级缓存是否在未提交时发布。
     * @throws Exception
     */
    @Test
    void shouldNotPublishSecondLevelCacheWithoutCommit() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            assertEquals(
                    "Alice",
                    session.getMapper(UserXmlMapper.class).selectById(1).getName()
            );
        }

        updateUserNameDirectly(1, "Alicia");

        try (SqlSession session = sqlSessionFactory.openSession()) {
            assertEquals(
                    "Alicia",
                    session.getMapper(UserXmlMapper.class).selectById(1).getName()
            );
            session.commit();
        }
    }

    /**
     * 测试二级缓存是否在提交 DML 操作后失效。
     */
    @Test
    void shouldInvalidateSecondLevelCacheAfterCommittedDml() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            session.getMapper(UserXmlMapper.class).selectById(1);
            session.commit();
        }

        try (SqlSession session = sqlSessionFactory.openSession()) {
            assertTrue(session.getMapper(UserXmlMapper.class).update(user(1, "Alicia", 21)));
            session.commit();
        }

        try (SqlSession session = sqlSessionFactory.openSession()) {
            User refreshedResult = session.getMapper(UserXmlMapper.class).selectById(1);

            assertEquals("Alicia", refreshedResult.getName());
            assertEquals(21, refreshedResult.getAge());
        }
    }

    /**
     * 测试二级缓存是否在自动提交时立即发布。
     * @throws Exception
     */
    @Test
    void shouldPublishSecondLevelCacheImmediatelyWithAutoCommit() throws Exception {
        User firstResult;
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            firstResult = session.getMapper(UserXmlMapper.class).selectById(1);
        }

        updateUserNameDirectly(1, "Alicia");

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            User cachedResult = session.getMapper(UserXmlMapper.class).selectById(1);

            assertSame(firstResult, cachedResult);
            assertEquals("Alice", cachedResult.getName());
        }
    }

    /**
     * 测试二级缓存是否在回滚后保持不变。
     */
    @Test
    void shouldKeepSecondLevelCacheUnchangedAfterRollback() {
        User firstResult;
        try (SqlSession session = sqlSessionFactory.openSession()) {
            firstResult = session.getMapper(UserXmlMapper.class).selectById(1);
            session.commit();
        }

        try (SqlSession session = sqlSessionFactory.openSession()) {
            assertTrue(session.getMapper(UserXmlMapper.class).update(user(1, "Alicia", 21)));
            session.rollback();
        }

        try (SqlSession session = sqlSessionFactory.openSession()) {
            User cachedResult = session.getMapper(UserXmlMapper.class).selectById(1);

            assertSame(firstResult, cachedResult);
            assertEquals("Alice", cachedResult.getName());
        }
    }

    /**
     * 测试二级缓存是否被禁用。
     * @throws Exception
     */
    @Test
    void shouldBypassSecondLevelCacheWhenUseCacheIsFalse() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            session.getMapper(UserXmlMapper.class).selectById(1);
            session.commit();
        }

        updateUserNameDirectly(1, "Alicia");

        try (SqlSession session = sqlSessionFactory.openSession()) {
            UserXmlMapper mapper = session.getMapper(UserXmlMapper.class);

            assertEquals("Alicia", mapper.selectWithoutCacheById(1).getName());
            assertEquals("Alice", mapper.selectById(1).getName());
        }
    }

    /**
     * 测试从映射器 XML 文件中删除 `<cache>` 元素时，二级缓存是否被禁用。
     * @throws Exception
     */
    @Test
    void shouldDisableSecondLevelCacheGlobally() throws Exception {
        SqlSessionFactory uncachedFactory = buildFactory(
                "mini-mybatis-second-level-cache-disabled-test-config.xml"
        );
        try (SqlSession session = uncachedFactory.openSession()) {
            session.getMapper(UserXmlMapper.class).selectById(1);
            session.commit();
        }

        updateUserNameDirectly(1, "Alicia");

        try (SqlSession session = uncachedFactory.openSession()) {
            assertEquals(
                    "Alicia",
                    session.getMapper(UserXmlMapper.class).selectById(1).getName()
            );
        }
    }

    /**
     * 测试配置的插件是否跨执行管道应用。
     * @throws Exception
     */
    @Test
    void shouldApplyConfiguredPluginAcrossExecutionPipeline() throws Exception {
        InvocationCountingInterceptor.reset();
        SqlSessionFactory pluginFactory = buildFactory("mini-mybatis-plugin-test-config.xml");

        try (SqlSession session = pluginFactory.openSession(true)) {
            UserMapper mapper = session.getMapper(UserMapper.class);
            User firstResult = mapper.selectById(1);
            User cachedResult = mapper.selectById(1);

            assertSame(firstResult, cachedResult);
            assertTrue(mapper.update(user(2, "Bobby", 26)));
        }

        assertEquals("integration", InvocationCountingInterceptor.getConfiguredLabel());
        assertEquals(2, InvocationCountingInterceptor.count(
                InvocationCountingInterceptor.EXECUTOR_QUERY
        ));
        assertEquals(1, InvocationCountingInterceptor.count(
                InvocationCountingInterceptor.EXECUTOR_UPDATE
        ));
        assertEquals(2, InvocationCountingInterceptor.count(
                InvocationCountingInterceptor.STATEMENT_PREPARE
        ));
        assertEquals(2, InvocationCountingInterceptor.count(
                InvocationCountingInterceptor.STATEMENT_PARAMETERIZE
        ));
        assertEquals(1, InvocationCountingInterceptor.count(
                InvocationCountingInterceptor.STATEMENT_QUERY
        ));
        assertEquals(1, InvocationCountingInterceptor.count(
                InvocationCountingInterceptor.STATEMENT_UPDATE
        ));
        assertEquals(2, InvocationCountingInterceptor.count(
                InvocationCountingInterceptor.PARAMETER_SET
        ));
        assertEquals(1, InvocationCountingInterceptor.count(
                InvocationCountingInterceptor.RESULT_HANDLE
        ));
        assertEquals("Bobby", queryUserNameDirectly(2));
    }

    private void assertUserDoesNotExist(int id) throws Exception {
        assertEquals(0, countUsersByIdDirectly(id));
    }

    private User user(int id, String name, int age) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setAge(age);
        return user;
    }

    private SqlSessionFactory buildFactory(String resource) throws Exception {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resource)) {
            return new SqlSessionFactoryBuilder().build(inputStream);
        }
    }

    private void updateUserNameDirectly(int id, String name) throws Exception {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "update user set name = ? where id = ?"
             )) {
            statement.setString(1, name);
            statement.setInt(2, id);
            statement.executeUpdate();
        }
    }

    private int countUsersByIdDirectly(int id) throws Exception {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "select count(*) from user where id = ?"
             )) {
            statement.setInt(1, id);
            try (var resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getInt(1);
            }
        }
    }

    private String queryUserNameDirectly(int id) throws Exception {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "select name from user where id = ?"
             )) {
            statement.setInt(1, id);
            try (var resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getString(1);
            }
        }
    }
}
