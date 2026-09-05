package cn.jens.mybatis.executor;

import cn.jens.mybatis.cache.CacheKey;
import cn.jens.mybatis.cache.LocalCache;
import cn.jens.mybatis.config.Configuration;
import cn.jens.mybatis.exception.PersistenceException;
import cn.jens.mybatis.executor.statement.StatementHandler;
import cn.jens.mybatis.mapping.MappedStatement;
import cn.jens.mybatis.scripting.BoundSql;
import cn.jens.mybatis.scripting.SqlParser;
import cn.jens.mybatis.session.LocalCacheScope;
import cn.jens.mybatis.transaction.Transaction;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * 每次执行都创建 PreparedStatement 的简单执行器。
 *
 * @author YumJens
 */
public class SimpleExecutor implements Executor {

    private final Transaction transaction;

    private final Configuration configuration;

    private final LocalCacheScope localCacheScope;

    private final LocalCache localCache = new LocalCache();

    public SimpleExecutor(Transaction transaction, Configuration configuration) {
        this.transaction = transaction;
        this.configuration = configuration;
        this.localCacheScope = configuration.getLocalCacheScope();
    }

    @Override
    public <T> List<T> query(MappedStatement mappedStatement, Object parameter) {
        if (mappedStatement.flushCacheRequired()) {
            clearLocalCache();
        }
        BoundSql boundSql = SqlParser.parse(mappedStatement.sql(), parameter);
        CacheKey cacheKey = CacheKey.create(mappedStatement, boundSql);
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
        StatementHandler statementHandler = configuration.newStatementHandler(
                mappedStatement,
                boundSql
        );
        try (PreparedStatement statement = statementHandler.prepare(transaction.getConnection())) {
            statementHandler.parameterize(statement);
            return statementHandler.query(statement);
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
        StatementHandler statementHandler = configuration.newStatementHandler(
                mappedStatement,
                boundSql
        );
        try (PreparedStatement statement = statementHandler.prepare(transaction.getConnection())) {
            statementHandler.parameterize(statement);
            return statementHandler.update(statement);
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
}
