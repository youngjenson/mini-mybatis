package cn.jens.mybatis;

import cn.jens.mybatis.cache.BlockingCache;
import cn.jens.mybatis.cache.Cache;
import cn.jens.mybatis.cache.CacheBuilder;
import cn.jens.mybatis.cache.CacheKey;
import cn.jens.mybatis.cache.PerpetualCache;
import cn.jens.mybatis.cache.TransactionalCache;
import cn.jens.mybatis.cache.TransactionalCacheManager;
import cn.jens.mybatis.exception.PersistenceException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockingCacheTest {

    private static final CacheKey KEY = new CacheKey("sample.Mapper.select", "select ?", List.of(1));

    private static final CacheKey OTHER_KEY = new CacheKey("sample.Mapper.select", "select ?", List.of(2));

    @Test
    void shouldWaitForCommitAndAllowDifferentKeysToProceed() throws Exception {
        Cache cache = new CacheBuilder("sample.Mapper").blocking(true).timeout(3000).build();
        TransactionalCache writer = new TransactionalCache(cache);
        assertNull(writer.get(KEY));
        writer.put(KEY, List.of("Alice"));
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch started = new CountDownLatch(1);
            var reader = executor.submit(() -> {
                TransactionalCache transaction = new TransactionalCache(cache);
                started.countDown();
                try {
                    return transaction.get(KEY);
                } finally {
                    transaction.rollback();
                }
            });
            try {
                assertTrue(started.await(1, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> reader.get(100, TimeUnit.MILLISECONDS));
                var otherReader = executor.submit(() -> {
                    TransactionalCache transaction = new TransactionalCache(cache);
                    try {
                        assertNull(transaction.get(OTHER_KEY));
                        transaction.put(OTHER_KEY, List.of("Bob"));
                        transaction.commit();
                    } finally {
                        transaction.rollback();
                    }
                });
                otherReader.get(1, TimeUnit.SECONDS);
                writer.commit();
                assertEquals(List.of("Alice"), reader.get(1, TimeUnit.SECONDS));
            } finally {
                writer.rollback();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldReleaseMissWithoutPendingValueOnCommitOrRollback(boolean commit) {
        Cache cache = new CacheBuilder("sample.Mapper").blocking(true).timeout(100).build();
        TransactionalCache transaction = new TransactionalCache(cache);
        assertNull(transaction.get(KEY));
        // 重复未命中不得等待自己；清空暂存写入后仍须保留解锁记录。
        assertNull(transaction.get(KEY));
        transaction.put(KEY, List.of("discarded"));
        transaction.clear();
        if (commit) {
            transaction.commit();
        } else {
            transaction.rollback();
        }
        TransactionalCache next = new TransactionalCache(cache);
        assertNull(next.get(KEY));
        next.put(KEY, List.of());
        next.commit();
        assertEquals(List.of(), cache.get(KEY));
        assertEquals(List.of(), cache.get(KEY));
    }

    @Test
    void shouldNotReleaseOwnersLockWhenAnotherTransactionTimesOut() {
        Cache cache = new CacheBuilder("sample.Mapper").blocking(true).timeout(30).build();
        TransactionalCache owner = new TransactionalCache(cache);
        assertNull(owner.get(KEY));
        TransactionalCache waiter = new TransactionalCache(cache);
        assertThrows(PersistenceException.class, () -> waiter.get(KEY));
        waiter.rollback();
        assertThrows(PersistenceException.class, () -> cache.get(KEY));
        owner.put(KEY, List.of("Alice"));
        owner.commit();
        assertEquals(List.of("Alice"), cache.get(KEY));
    }

    @Test
    void shouldRestoreInterruptAndKeepOwnersLock() throws Exception {
        Cache cache = new CacheBuilder("sample.Mapper").blocking(true).build();
        assertNull(cache.get(KEY));
        try (var executor = Executors.newSingleThreadExecutor()) {
            var interruptedReader = executor.submit(() -> {
                Thread.currentThread().interrupt();
                assertThrows(PersistenceException.class, () -> cache.get(KEY));
                assertTrue(Thread.currentThread().isInterrupted());
                Thread.interrupted();
            });
            interruptedReader.get(1, TimeUnit.SECONDS);
        } finally {
            cache.put(KEY, List.of("Alice"));
        }
        assertEquals(List.of("Alice"), cache.get(KEY));
    }

    @Test
    void shouldReleaseLockWhenDelegateReadFails() {
        BlockingCache cache = new BlockingCache(new PerpetualCache("sample.Mapper") {
            private boolean fail = true;

            @Override
            public <T> List<T> get(CacheKey key) {
                if (fail) {
                    fail = false;
                    throw new PersistenceException("read failed");
                }
                return super.get(key);
            }
        }, 100);
        assertThrows(PersistenceException.class, () -> cache.get(KEY));
        assertNull(cache.get(KEY));
        cache.remove(KEY);
    }

    @Test
    void shouldReleaseAllMissesWhenPublishingFails() {
        BlockingCache cache = new BlockingCache(new PerpetualCache("sample.Mapper") {
            @Override
            public void put(CacheKey key, List<?> value) {
                throw new PersistenceException("write failed");
            }
        }, 100);
        TransactionalCache transaction = new TransactionalCache(cache);
        assertNull(transaction.get(KEY));
        assertNull(transaction.get(OTHER_KEY));
        transaction.put(KEY, List.of("Alice"));
        transaction.put(OTHER_KEY, List.of("Bob"));
        assertThrows(PersistenceException.class, transaction::commit);
        assertNull(cache.get(KEY));
        assertNull(cache.get(OTHER_KEY));
        cache.remove(KEY);
        cache.remove(OTHER_KEY);
    }

    @Test
    void shouldAllowRefreshAfterHitAndAfterMiss() {
        Cache cache = new CacheBuilder("sample.Mapper").blocking(true).timeout(100).build();
        cache.put(KEY, List.of("old"));
        TransactionalCache transaction = new TransactionalCache(cache);
        transaction.clear();
        assertNull(transaction.get(KEY));
        transaction.put(KEY, List.of("new"));
        transaction.commit();
        assertEquals(List.of("new"), cache.get(KEY));

        transaction.clear();
        assertNull(transaction.get(OTHER_KEY));
        transaction.put(OTHER_KEY, List.of("other"));
        transaction.commit();
        assertEquals(List.of("other"), cache.get(OTHER_KEY));
        assertNull(cache.get(KEY));
        cache.remove(KEY);
    }

    @Test
    void shouldOnlyUnlockOnRemoveAndKeepData() {
        Cache cache = new CacheBuilder("sample.Mapper").blocking(true).timeout(100).build();
        assertNull(cache.get(KEY));
        cache.remove(KEY);
        assertNull(cache.get(KEY));
        cache.put(KEY, List.of("Alice"));
        cache.remove(KEY);
        assertEquals(List.of("Alice"), cache.get(KEY));
    }

    @Test
    void shouldReleaseOtherNamespacesWhenOneCacheCommitFails() {
        Cache first = failingCache("first.Mapper");
        Cache second = failingCache("second.Mapper");
        TransactionalCacheManager manager = new TransactionalCacheManager();
        assertNull(manager.get(first, KEY));
        assertNull(manager.get(second, KEY));
        manager.put(first, KEY, List.of("Alice"));
        manager.put(second, KEY, List.of("Bob"));
        assertThrows(PersistenceException.class, manager::commit);
        assertNull(first.get(KEY));
        assertNull(second.get(KEY));
        first.remove(KEY);
        second.remove(KEY);
    }

    private Cache failingCache(String id) {
        return new BlockingCache(new PerpetualCache(id) {
            @Override
            public void put(CacheKey key, List<?> value) {
                throw new PersistenceException("write failed");
            }
        }, 100);
    }
}
