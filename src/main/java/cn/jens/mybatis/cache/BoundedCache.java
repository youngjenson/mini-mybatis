package cn.jens.mybatis.cache;

/** 保留原有构造入口，内部统一使用 CacheBuilder 组装装饰器。 */
public class BoundedCache extends CacheDecorator {

    public static final int DEFAULT_SIZE = CacheBuilder.DEFAULT_SIZE;

    public BoundedCache(String id) {
        this(id, DEFAULT_SIZE, EvictionPolicy.LRU);
    }

    public BoundedCache(String id, int size, EvictionPolicy evictionPolicy) {
        super(new CacheBuilder(id).size(size).eviction(evictionPolicy).build());
    }
}
