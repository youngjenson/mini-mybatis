package cn.jens.mybatis.cache;

import java.util.List;
import java.util.Objects;

/** 缓存装饰器的基础委托，具体装饰器只覆盖自身负责的行为。 */
public abstract class CacheDecorator implements Cache {

    protected final Cache delegate;

    protected CacheDecorator(Cache delegate) {
        this.delegate = Objects.requireNonNull(delegate, "Cache delegate must not be null");
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public <T> List<T> get(CacheKey key) {
        return delegate.get(key);
    }

    @Override
    public void put(CacheKey key, List<?> value) {
        delegate.put(key, value);
    }

    @Override
    public List<?> remove(CacheKey key) {
        return delegate.remove(key);
    }

    @Override
    public void clear() {
        delegate.clear();
    }
}
