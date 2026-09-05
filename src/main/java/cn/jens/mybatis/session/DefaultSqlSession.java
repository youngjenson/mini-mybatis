package cn.jens.mybatis.session;

import cn.jens.mybatis.binding.MapperProxyFactory;
import cn.jens.mybatis.config.Configuration;
import cn.jens.mybatis.exception.PersistenceException;
import cn.jens.mybatis.executor.Executor;
import cn.jens.mybatis.mapping.MappedStatement;
import cn.jens.mybatis.mapping.SqlCommandType;

import java.util.List;

/** 默认 SqlSession 实现。 */
public class DefaultSqlSession implements SqlSession {

    private final Configuration configuration;

    private final Executor executor;

    private final boolean autoCommit;

    private boolean dirty;

    private boolean closed;

    public DefaultSqlSession(
            Configuration configuration,
            Executor executor,
            boolean autoCommit) {
        this.configuration = configuration;
        this.executor = executor;
        this.autoCommit = autoCommit;
    }

    @Override
    public <T> T selectOne(String statementId, Object parameter) {
        List<T> results = selectList(statementId, parameter);
        if (results.size() > 1) {
            throw new PersistenceException(
                    "Expected one result for " + statementId + ", but found " + results.size()
            );
        }
        return results.isEmpty() ? null : results.getFirst();
    }

    @Override
    public <T> List<T> selectList(String statementId, Object parameter) {
        checkOpen();
        MappedStatement mappedStatement = getStatement(statementId, SqlCommandType.SELECT);
        return executor.query(mappedStatement, parameter);
    }

    @Override
    public int insert(String statementId, Object parameter) {
        return executeUpdate(statementId, parameter, SqlCommandType.INSERT);
    }

    @Override
    public int update(String statementId, Object parameter) {
        return executeUpdate(statementId, parameter, SqlCommandType.UPDATE);
    }

    @Override
    public int delete(String statementId, Object parameter) {
        return executeUpdate(statementId, parameter, SqlCommandType.DELETE);
    }

    @Override
    public <T> T getMapper(Class<T> mapperClass) {
        checkOpen();
        return new MapperProxyFactory<>(mapperClass).newInstance(this);
    }

    @Override
    public Configuration getConfiguration() {
        checkOpen();
        return configuration;
    }

    @Override
    public void commit() {
        checkOpen();
        executor.commit();
        dirty = false;
    }

    @Override
    public void rollback() {
        checkOpen();
        executor.rollback();
        dirty = false;
    }

    @Override
    public void clearCache() {
        checkOpen();
        executor.clearLocalCache();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        try {
            if (dirty && !autoCommit) {
                executor.rollback();
            }
        } finally {
            try {
                executor.close();
            } finally {
                dirty = false;
                closed = true;
            }
        }
    }

    private int executeUpdate(
            String statementId,
            Object parameter,
            SqlCommandType expectedCommandType) {
        checkOpen();
        MappedStatement mappedStatement = getStatement(statementId, expectedCommandType);
        int affectedRows = executor.update(mappedStatement, parameter);
        dirty = !autoCommit;
        return affectedRows;
    }

    private MappedStatement getStatement(
            String statementId,
            SqlCommandType expectedCommandType) {
        MappedStatement mappedStatement = configuration.getMappedStatement(statementId);
        if (mappedStatement.sqlCommandType() != expectedCommandType) {
            throw new PersistenceException(
                    "Mapped statement " + statementId + " is "
                            + mappedStatement.sqlCommandType() + ", not " + expectedCommandType
            );
        }
        return mappedStatement;
    }

    private void checkOpen() {
        if (closed) {
            throw new PersistenceException("SqlSession is already closed");
        }
    }
}
