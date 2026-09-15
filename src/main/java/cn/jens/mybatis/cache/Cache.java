package cn.jens.mybatis.cache;

import java.util.List;

/** Mapper namespace 共享的二级缓存。 */
public interface Cache {

    String getId();

    <T> List<T> get(CacheKey key);

    void put(CacheKey key, List<?> value);

    /** 删除条目；BlockingCache 中用于释放未命中 key 的加载锁。 */
    List<?> remove(CacheKey key);

    void clear();
}
