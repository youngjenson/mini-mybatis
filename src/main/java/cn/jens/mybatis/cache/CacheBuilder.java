package cn.jens.mybatis.cache;

import java.util.Objects;

/** 按存储、淘汰、同步、可选阻塞的顺序组装 namespace 缓存。 */
public class CacheBuilder {

    public static final int DEFAULT_SIZE = 1024;

    private final String id;

    private int size = DEFAULT_SIZE;

    private EvictionPolicy eviction = EvictionPolicy.LRU;

    private boolean blocking;

    private long timeout;

    public CacheBuilder(String id) {
        this.id = Objects.requireNonNull(id, "Cache id must not be null");
    }

    public CacheBuilder size(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Cache size must be positive: " + size);
        }
        this.size = size;
        return this;
    }

    public CacheBuilder eviction(EvictionPolicy eviction) {
        this.eviction = Objects.requireNonNull(eviction, "Cache eviction policy must not be null");
        return this;
    }

    public CacheBuilder blocking(boolean blocking) {
        this.blocking = blocking;
        return this;
    }

    public CacheBuilder timeout(long timeout) {
        if (timeout < 0) {
            throw new IllegalArgumentException("Cache timeout must not be negative: " + timeout);
        }
        this.timeout = timeout;
        return this;
    }

    public Cache build() {
        Cache cache = new PerpetualCache(id);
        cache = switch (eviction) {
            case LRU -> new LruCache(cache, size);
            case FIFO -> new FifoCache(cache, size);
        };
        cache = new SynchronizedCache(cache);
        return blocking ? new BlockingCache(cache, timeout) : cache;
    }
}
