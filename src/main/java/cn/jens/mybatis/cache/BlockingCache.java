package cn.jens.mybatis.cache;

import cn.jens.mybatis.exception.PersistenceException;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 按 key 协调未命中请求。get 返回 null 后必须由事务调用 put 或 remove 释放加载锁。
 * 此装饰器必须放在 SynchronizedCache 外层，等待时不能持有整个缓存的锁。
 */
public class BlockingCache extends CacheDecorator {

    private final ConcurrentHashMap<CacheKey, CountDownLatch> locks = new ConcurrentHashMap<>();

    private final long timeoutMillis;

    /** @param timeoutMillis 等待上限（毫秒），0 表示无限等待 */
    public BlockingCache(Cache delegate, long timeoutMillis) {
        super(delegate);
        if (timeoutMillis < 0) {
            throw new IllegalArgumentException("Cache timeout must not be negative: " + timeoutMillis);
        }
        this.timeoutMillis = timeoutMillis;
    }

    @Override
    public <T> List<T> get(CacheKey key) {
        Objects.requireNonNull(key, "Cache key must not be null");
        acquireLock(key);
        boolean missed = false;
        try {
            List<T> value = delegate.get(key);
            missed = value == null;
            return value;
        } finally {
            if (!missed) {
                releaseLock(key);
            }
        }
    }

    @Override
    public void put(CacheKey key, List<?> value) {
        try {
            delegate.put(key, value);
        } finally {
            releaseLock(key);
        }
    }

    /** 只释放加载锁，不删除已提交的数据。 */
    @Override
    public List<?> remove(CacheKey key) {
        releaseLock(key);
        return null;
    }

    private void acquireLock(CacheKey key) {
        long started = System.nanoTime();
        long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        CountDownLatch newLatch = new CountDownLatch(1);
        while (true) {
            CountDownLatch existing = locks.putIfAbsent(key, newLatch);
            if (existing == null) {
                return;
            }
            try {
                if (timeoutMillis == 0) {
                    existing.await();
                } else {
                    long remaining = timeoutNanos - (System.nanoTime() - started);
                    if (remaining <= 0 || !existing.await(remaining, TimeUnit.NANOSECONDS)) {
                        throw new PersistenceException("Timed out waiting for cache entry in " + getId());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new PersistenceException("Interrupted waiting for cache entry in " + getId(), e);
            }
        }
    }

    private void releaseLock(CacheKey key) {
        CountDownLatch latch = locks.remove(key);
        if (latch != null) {
            latch.countDown();
        }
    }
}
