package cn.jens.mybatis.cache;

import java.util.LinkedHashMap;
import java.util.List;

/** 仅维护 key 的访问顺序，数据由 delegate 保存；共享使用时需外层同步装饰器。 */
public class LruCache extends CacheDecorator {

    private final int size;

    private final LinkedHashMap<CacheKey, Boolean> keys = new LinkedHashMap<>(16, 0.75f, true);

    public LruCache(Cache delegate, int size) {
        super(delegate);
        if (size <= 0) {
            throw new IllegalArgumentException("Cache size must be positive: " + size);
        }
        this.size = size;
    }

    @Override
    public <T> List<T> get(CacheKey key) {
        keys.get(key);
        return delegate.get(key);
    }

    @Override
    public void put(CacheKey key, List<?> value) {
        delegate.put(key, value);
        keys.put(key, Boolean.TRUE);
        if (keys.size() > size) {
            delegate.remove(keys.pollFirstEntry().getKey());
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
