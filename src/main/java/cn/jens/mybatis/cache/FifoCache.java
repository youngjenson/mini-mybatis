package cn.jens.mybatis.cache;

import java.util.LinkedHashSet;
import java.util.List;

/** 维护 key 的插入顺序，覆盖写入不重复排队；共享使用时需外层同步装饰器。 */
public class FifoCache extends CacheDecorator {

    private final int size;

    private final LinkedHashSet<CacheKey> keys = new LinkedHashSet<>();

    public FifoCache(Cache delegate, int size) {
        super(delegate);
        if (size <= 0) {
            throw new IllegalArgumentException("Cache size must be positive: " + size);
        }
        this.size = size;
    }

    @Override
    public void put(CacheKey key, List<?> value) {
        delegate.put(key, value);
        keys.add(key);
        if (keys.size() > size) {
            delegate.remove(keys.removeFirst());
        }
    }

    @Override
    public List<?> remove(CacheKey key) {
        keys.remove(key);
        return delegate.remove(key);
    }

    @Override
    public void clear() {
        delegate.clear();
        keys.clear();
    }
}
