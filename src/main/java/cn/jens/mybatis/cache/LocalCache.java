package cn.jens.mybatis.cache;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** SqlSession 私有的查询结果缓存。 */
public class LocalCache {

    private final Map<CacheKey, List<?>> entries = new HashMap<>();

    @SuppressWarnings("unchecked")
    public <T> List<T> get(CacheKey key) {
        return (List<T>) entries.get(key);
    }

    public void put(CacheKey key, List<?> value) {
        entries.put(key, value);
    }

    public void clear() {
        entries.clear();
    }
}
