package cn.jens.mybatis.executor;

import cn.jens.mybatis.cache.Cache;
import cn.jens.mybatis.cache.CacheKey;
import cn.jens.mybatis.cache.TransactionalCacheManager;
import cn.jens.mybatis.config.Configuration;
import cn.jens.mybatis.mapping.MappedStatement;
import cn.jens.mybatis.scripting.BoundSql;
import cn.jens.mybatis.scripting.SqlParser;

import java.util.List;

/** 在基础 Executor 外增加 Mapper namespace 级二级缓存。 */
public class CachingExecutor implements Executor {

    private final Executor delegate;

    private final Configuration configuration;

    private final boolean autoCommit;

    private final TransactionalCacheManager transactionalCacheManager =
            new TransactionalCacheManager();

    public CachingExecutor(
            Executor delegate,
            Configuration configuration,
            boolean autoCommit) {
        this.delegate = delegate;
        this.configuration = configuration;
        this.autoCommit = autoCommit;
    }

    @Override
    public <T> List<T> query(MappedStatement mappedStatement, Object parameter) {
        Cache cache = getCache(mappedStatement);
        if (cache == null) {
            return delegate.query(mappedStatement, parameter);
        }

        try {
            if (mappedStatement.flushCacheRequired()) {
                transactionalCacheManager.clear(cache);
            }

            List<T> results;
            if (mappedStatement.useCache()) {
                BoundSql boundSql = SqlParser.parse(mappedStatement.sql(), parameter);
                CacheKey cacheKey = CacheKey.create(mappedStatement, boundSql);
                results = transactionalCacheManager.get(cache, cacheKey);
                if (results == null) {
                    results = delegate.query(mappedStatement, parameter);
                    transactionalCacheManager.put(cache, cacheKey, results);
                }
            } else {
                results = delegate.query(mappedStatement, parameter);
            }

            commitCacheIfAutoCommit();
            return results;
        } catch (RuntimeException | Error e) {
            rollbackCacheIfAutoCommit();
            throw e;
        }
    }

    @Override
    public int update(MappedStatement mappedStatement, Object parameter) {
        Cache cache = getCache(mappedStatement);
        try {
            if (cache != null && mappedStatement.flushCacheRequired()) {
                transactionalCacheManager.clear(cache);
            }
            int affectedRows = delegate.update(mappedStatement, parameter);
            commitCacheIfAutoCommit();
            return affectedRows;
        } catch (RuntimeException | Error e) {
            rollbackCacheIfAutoCommit();
            throw e;
        }
    }

    @Override
    public void commit() {
        delegate.commit();
        transactionalCacheManager.commit();
    }

    @Override
    public void rollback() {
        try {
            delegate.rollback();
        } finally {
            transactionalCacheManager.rollback();
        }
    }

    @Override
    public void clearLocalCache() {
        delegate.clearLocalCache();
    }

    @Override
    public void close() {
        try {
            transactionalCacheManager.rollback();
        } finally {
            delegate.close();
        }
    }

    private Cache getCache(MappedStatement mappedStatement) {
        return configuration.getCache(mappedStatement.namespace());
    }

    private void commitCacheIfAutoCommit() {
        if (autoCommit) {
            transactionalCacheManager.commit();
        }
    }

    private void rollbackCacheIfAutoCommit() {
        if (autoCommit) {
            transactionalCacheManager.rollback();
        }
    }
}
