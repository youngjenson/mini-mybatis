package cn.jens.mybatis;

import cn.jens.mybatis.cache.CacheKey;
import cn.jens.mybatis.cache.PerpetualCache;
import cn.jens.mybatis.cache.TransactionalCache;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TransactionalCacheTest {

    private static final CacheKey KEY = new CacheKey(
            "sample.Mapper.select",
            "select name from user where id = ?",
            List.of(1)
    );

    @Test
    void shouldPublishPendingEntryOnlyAfterCommit() {
        PerpetualCache sharedCache = new PerpetualCache("sample.Mapper");
        TransactionalCache transactionalCache = new TransactionalCache(sharedCache);

        transactionalCache.put(KEY, List.of("Alice"));
        assertNull(sharedCache.get(KEY));

        transactionalCache.commit();
        assertEquals(List.of("Alice"), sharedCache.get(KEY));
    }


    @Test
    void shouldDiscardPendingChangesAfterRollback() {
        PerpetualCache sharedCache = new PerpetualCache("sample.Mapper");
        sharedCache.put(KEY, List.of("Alice"));
        TransactionalCache transactionalCache = new TransactionalCache(sharedCache);

        transactionalCache.clear();
        transactionalCache.put(KEY, List.of("Alicia"));
        transactionalCache.rollback();

        assertEquals(List.of("Alice"), sharedCache.get(KEY));
    }
}
