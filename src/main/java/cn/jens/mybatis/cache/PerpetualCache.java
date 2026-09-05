package cn.jens.mybatis.cache;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 进程内永久缓存，生命周期与 Configuration 相同。 */
public class PerpetualCache implements Cache {

    private final String id;

    private final Map<CacheKey, List<?>> entries = new ConcurrentHashMap<>();

    public PerpetualCache(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> get(CacheKey key) {
        return (List<T>) entries.get(key);
    }

    @Override
    public void put(CacheKey key, List<?> value) {
        entries.put(key, value);
    }

    @Override
    public void clear() {
        entries.clear();
    }
}
