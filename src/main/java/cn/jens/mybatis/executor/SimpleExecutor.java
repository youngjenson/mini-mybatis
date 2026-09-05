package cn.jens.mybatis.executor;

import cn.jens.mybatis.cache.CacheKey;
import cn.jens.mybatis.cache.LocalCache;
import cn.jens.mybatis.exception.PersistenceException;
import cn.jens.mybatis.mapping.MappedStatement;
import cn.jens.mybatis.reflection.ResultSetHandler;
import cn.jens.mybatis.scripting.BoundSql;
import cn.jens.mybatis.scripting.ParameterMapping;
import cn.jens.mybatis.scripting.SqlParser;
import cn.jens.mybatis.session.LocalCacheScope;
import cn.jens.mybatis.transaction.Transaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 每次执行都创建 PreparedStatement 的简单执行器。
 *
 * @author YumJens
 */
public class SimpleExecutor implements Executor {

    private final Transaction transaction;

    private final LocalCacheScope localCacheScope;

    private final ResultSetHandler resultSetHandler = new ResultSetHandler();

    private final LocalCache localCache = new LocalCache();

    public SimpleExecutor(Transaction transaction, LocalCacheScope localCacheScope) {
        this.transaction = transaction;
        this.localCacheScope = localCacheScope;
    }

    @Override
    public <T> List<T> query(MappedStatement mappedStatement, Object parameter) {
        if (mappedStatement.flushCacheRequired()) {
            clearLocalCache();
        }
        BoundSql boundSql = SqlParser.parse(mappedStatement.sql(), parameter);
        CacheKey cacheKey = createCacheKey(mappedStatement, boundSql);
        if (localCacheScope == LocalCacheScope.SESSION) {
            List<T> cachedResults = localCache.get(cacheKey);
            if (cachedResults != null) {
                return cachedResults;
            }
        }

        List<T> results = queryFromDatabase(mappedStatement, boundSql);
        if (localCacheScope == LocalCacheScope.SESSION) {
            localCache.put(cacheKey, results);
        }
        return results;
    }

    private <T> List<T> queryFromDatabase(
            MappedStatement mappedStatement,
            BoundSql boundSql) {
        try (PreparedStatement statement = transaction
                .getConnection()
                .prepareStatement(boundSql.getSql())) {
            setParameters(statement, boundSql);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSetHandler.handle(
                        resultSet,
                        mappedStatement.resultType(),
                        mappedStatement.resultMap()
                );
            }
        } catch (SQLException e) {
            throw new PersistenceException(
                    "Failed to execute mapped statement: " + mappedStatement.id(),
                    e
            );
        }
    }

    @Override
    public int update(MappedStatement mappedStatement, Object parameter) {
        clearLocalCache();
        BoundSql boundSql = SqlParser.parse(mappedStatement.sql(), parameter);
        try (PreparedStatement statement = transaction
                .getConnection()
                .prepareStatement(boundSql.getSql())) {
            setParameters(statement, boundSql);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException(
                    "Failed to execute mapped statement: " + mappedStatement.id(),
                    e
            );
        }
    }

    @Override
    public void commit() {
        clearLocalCache();
        transaction.commit();
    }

    @Override
    public void rollback() {
        clearLocalCache();
        transaction.rollback();
    }

    @Override
    public void clearLocalCache() {
        localCache.clear();
    }

    @Override
    public void close() {
        clearLocalCache();
        transaction.close();
    }

    private CacheKey createCacheKey(MappedStatement mappedStatement, BoundSql boundSql) {
        List<Object> parameterValues = new ArrayList<>();
        for (ParameterMapping mapping : boundSql.getParameterMappings()) {
            parameterValues.add(mapping.value());
        }
        return new CacheKey(mappedStatement.id(), boundSql.getSql(), parameterValues);
    }

    private void setParameters(PreparedStatement statement, BoundSql boundSql) throws SQLException {
        List<ParameterMapping> mappings = boundSql.getParameterMappings();
        for (int index = 0; index < mappings.size(); index++) {
            statement.setObject(index + 1, mappings.get(index).value());
        }
    }
}
