package cn.jens.mybatis;

import cn.jens.mybatis.cache.CacheKey;
import cn.jens.mybatis.cache.BoundedCache;
import cn.jens.mybatis.cache.EvictionPolicy;
import cn.jens.mybatis.cache.PerpetualCache;
import cn.jens.mybatis.cache.TransactionalCache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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

    @Test
    void shouldPreserveOtherTransactionsResultAfterNonBlockingMiss() {
        PerpetualCache sharedCache = new PerpetualCache("sample.Mapper");
        TransactionalCache transaction = new TransactionalCache(sharedCache);
        assertNull(transaction.get(KEY));
        sharedCache.put(KEY, List.of("Alice"));
        assertEquals(List.of("Alice"), transaction.get(KEY));
        transaction.rollback();
        assertEquals(List.of("Alice"), sharedCache.get(KEY));
    }

    @ParameterizedTest
    @EnumSource(EvictionPolicy.class)
    void shouldEvictOnlyWhenPendingEntriesAreCommitted(EvictionPolicy policy) {
        BoundedCache sharedCache = new BoundedCache("sample.Mapper", 1, policy);
        sharedCache.put(KEY, List.of("Alice"));
        CacheKey otherKey = new CacheKey(KEY.statementId(), KEY.sql(), List.of(2));
        TransactionalCache transactionalCache = new TransactionalCache(sharedCache);

        transactionalCache.put(otherKey, List.of("Bob"));
        assertEquals(List.of("Alice"), sharedCache.get(KEY));
        assertNull(sharedCache.get(otherKey));
        transactionalCache.rollback();
        transactionalCache.commit();
        assertEquals(List.of("Alice"), sharedCache.get(KEY));
        assertNull(sharedCache.get(otherKey));

        transactionalCache.put(otherKey, List.of("Bob"));
        transactionalCache.commit();
        assertNull(sharedCache.get(KEY));
        assertEquals(List.of("Bob"), sharedCache.get(otherKey));
    }

    @ParameterizedTest
    @EnumSource(EvictionPolicy.class)
    void shouldClearOnCommitAndEnforceCapacityForBatch(EvictionPolicy policy) {
        BoundedCache sharedCache = new BoundedCache("sample.Mapper", 1, policy);
        sharedCache.put(KEY, List.of("Alice"));
        CacheKey secondKey = new CacheKey(KEY.statementId(), KEY.sql(), List.of(2));
        CacheKey thirdKey = new CacheKey(KEY.statementId(), KEY.sql(), List.of(3));
        TransactionalCache transactionalCache = new TransactionalCache(sharedCache);
        transactionalCache.clear();
        assertNull(transactionalCache.get(KEY));
        assertEquals(List.of("Alice"), sharedCache.get(KEY));
        transactionalCache.put(secondKey, List.of("Bob"));
        transactionalCache.put(thirdKey, List.of("Carol"));
        transactionalCache.commit();

        assertNull(sharedCache.get(KEY));
        assertNull(sharedCache.get(secondKey));
        assertEquals(List.of("Carol"), sharedCache.get(thirdKey));
    }
}
