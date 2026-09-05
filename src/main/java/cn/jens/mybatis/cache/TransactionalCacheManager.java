package cn.jens.mybatis.cache;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 管理一个 SqlSession 涉及的所有 namespace 事务缓存。 */
public class TransactionalCacheManager {

    private final Map<Cache, TransactionalCache> transactionalCaches = new HashMap<>();

    public <T> List<T> get(Cache cache, CacheKey key) {
        return transactionalCache(cache).get(key);
    }

    public void put(Cache cache, CacheKey key, List<?> value) {
        transactionalCache(cache).put(key, value);
    }

    public void clear(Cache cache) {
        transactionalCache(cache).clear();
    }

    public void commit() {
        transactionalCaches.values().forEach(TransactionalCache::commit);
    }

    public void rollback() {
        transactionalCaches.values().forEach(TransactionalCache::rollback);
    }

    private TransactionalCache transactionalCache(Cache cache) {
        return transactionalCaches.computeIfAbsent(cache, TransactionalCache::new);
    }
}
