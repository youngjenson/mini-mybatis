package cn.jens.mybatis.cache;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 为单个 SqlSession 暂存二级缓存操作，只有事务提交后才修改共享缓存。
 */
public class TransactionalCache {

    private final Cache delegate;

    private final Map<CacheKey, List<?>> entriesToAddOnCommit = new LinkedHashMap<>();

    private final Set<CacheKey> entriesMissedInCache = new LinkedHashSet<>();

    private boolean clearOnCommit;

    public TransactionalCache(Cache delegate) {
        this.delegate = delegate;
    }

    public <T> List<T> get(CacheKey key) {
        // 已持有该 key 的加载锁，交给一级缓存或数据库处理，避免重复获取而等待自己。
        if (entriesMissedInCache.contains(key)) {
            return null;
        }
        List<T> value = delegate.get(key);
        // 普通缓存没有加载锁，回滚时不应删除其他事务已经写入的结果。
        if (value == null && delegate instanceof BlockingCache) {
            entriesMissedInCache.add(key);
        }
        return clearOnCommit ? null : value;
    }

    public void put(CacheKey key, List<?> value) {
        entriesToAddOnCommit.put(key, value);
    }

    public void clear() {
        clearOnCommit = true;
        entriesToAddOnCommit.clear();
    }

    public void commit() {
        try {
            if (clearOnCommit) {
                delegate.clear();
            }
            for (Map.Entry<CacheKey, List<?>> entry : entriesToAddOnCommit.entrySet()) {
                // BlockingCache.put 即使写入失败也会解锁，不能在 finally 中重复释放。
                entriesMissedInCache.remove(entry.getKey());
                delegate.put(entry.getKey(), entry.getValue());
            }
        } finally {
            releaseMissedEntries();
        }
    }

    public void rollback() {
        releaseMissedEntries();
    }

    private void releaseMissedEntries() {
        try {
            // 无结果回填的未命中也必须解锁；无需把 null 写入底层缓存。
            entriesMissedInCache.forEach(delegate::remove);
        } finally {
            reset();
        }
    }

    private void reset() {
        clearOnCommit = false;
        entriesToAddOnCommit.clear();
        entriesMissedInCache.clear();
    }
}
