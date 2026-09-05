package cn.jens.mybatis.cache;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 为单个 SqlSession 暂存二级缓存操作，只有事务提交后才修改共享缓存。
 */
public class TransactionalCache {

    private final Cache delegate;

    private final Map<CacheKey, List<?>> entriesToAddOnCommit = new LinkedHashMap<>();

    private boolean clearOnCommit;

    public TransactionalCache(Cache delegate) {
        this.delegate = delegate;
    }

    public <T> List<T> get(CacheKey key) {
        if (clearOnCommit) {
            return null;
        }
        return delegate.get(key);
    }

    public void put(CacheKey key, List<?> value) {
        entriesToAddOnCommit.put(key, value);
    }

    public void clear() {
        clearOnCommit = true;
        entriesToAddOnCommit.clear();
    }

    public void commit() {
        if (clearOnCommit) {
            delegate.clear();
        }
        entriesToAddOnCommit.forEach(delegate::put);
        reset();
    }

    public void rollback() {
        reset();
    }

    private void reset() {
        clearOnCommit = false;
        entriesToAddOnCommit.clear();
    }
}
