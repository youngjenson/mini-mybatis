package cn.jens.mybatis;

import cn.jens.demo.entity.User;
import cn.jens.demo.mapper.UserMapper;
import cn.jens.demo.mapper.UserXmlMapper;
import cn.jens.mybatis.exception.PersistenceException;
import cn.jens.mybatis.session.SqlSession;
import cn.jens.mybatis.session.SqlSessionFactory;
import cn.jens.mybatis.session.SqlSessionFactoryBuilder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniMyBatisIntegrationTest {

    private static final String URL =
            "jdbc:mysql://127.0.0.1:3306/test?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&verifyServerCertificate=false";

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        sqlSessionFactory = buildFactory("mini-mybatis-test-config.xml");

        try (Connection connection = DriverManager.getConnection(URL, "root", "root");
             Statement statement = connection.createStatement()) {
            statement.execute("drop table if exists user");
            statement.execute("create table user (id int primary key, name varchar(64), age int)");
            statement.execute("insert into user values (1, 'Alice', 20), (2, 'Bob', 25)");
        }
    }

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

    @Test
    void shouldCommitInsertUpdateAndDelete() {
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
    }

    @Test
    void shouldRollbackExplicitly() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            UserMapper mapper = session.getMapper(UserMapper.class);
            mapper.insert(user(3, "Carol", 30));
            session.rollback();
        }

        assertUserDoesNotExist(3);
    }

    @Test
    void shouldRollbackUncommittedChangesWhenClosing() {
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

    @Test
    void shouldRejectOperationsAfterSessionIsClosed() {
        SqlSession session = sqlSessionFactory.openSession();
        session.close();

        assertThrows(PersistenceException.class, () -> session.selectList("unknown", null));
        session.close();
    }

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

    private void assertUserDoesNotExist(int id) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            assertNull(session.getMapper(UserMapper.class).selectById(id));
        }
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
        try (Connection connection = DriverManager.getConnection(URL, "root", "root");
             var statement = connection.prepareStatement(
                     "update user set name = ? where id = ?"
             )) {
            statement.setString(1, name);
            statement.setInt(2, id);
            statement.executeUpdate();
        }
    }
}
