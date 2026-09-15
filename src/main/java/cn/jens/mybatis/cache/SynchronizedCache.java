package cn.jens.mybatis.cache;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/** 使用同一把互斥锁保护整个装饰器链，包括 LRU 读取时的顺序更新。 */
public class SynchronizedCache extends CacheDecorator {

    private final ReentrantLock lock = new ReentrantLock();

    public SynchronizedCache(Cache delegate) {
        super(delegate);
    }

    @Override
    public <T> List<T> get(CacheKey key) {
        lock.lock();
        try {
            return delegate.get(key);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void put(CacheKey key, List<?> value) {
        lock.lock();
        try {
            delegate.put(key, value);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<?> remove(CacheKey key) {
        lock.lock();
        try {
            return delegate.remove(key);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void clear() {
        lock.lock();
        try {
            delegate.clear();
        } finally {
            lock.unlock();
        }
    }
}
