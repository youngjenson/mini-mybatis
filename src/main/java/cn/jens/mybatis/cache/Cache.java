package cn.jens.mybatis.cache;

import java.util.List;

/** Mapper namespace 共享的二级缓存。 */
public interface Cache {

    String getId();

    <T> List<T> get(CacheKey key);

    void put(CacheKey key, List<?> value);

    void clear();
}
