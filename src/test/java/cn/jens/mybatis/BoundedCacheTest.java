package cn.jens.mybatis;

import cn.jens.mybatis.cache.BoundedCache;
import cn.jens.mybatis.cache.CacheKey;
import cn.jens.mybatis.cache.EvictionPolicy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoundedCacheTest {

    @ParameterizedTest
    @EnumSource(EvictionPolicy.class)
    void shouldEvictAccordingToPolicyAfterRead(EvictionPolicy policy) {
        BoundedCache cache = new BoundedCache("sample.Mapper", 2, policy);
        cache.put(key(1), List.of("one"));
        cache.put(key(2), List.of("two"));
        assertEquals(List.of("one"), cache.get(key(1)));
        assertNull(cache.get(key(99)));

        cache.put(key(3), List.of("three"));

        assertNull(cache.get(key(policy == EvictionPolicy.LRU ? 2 : 1)));
        assertNotNull(cache.get(key(policy == EvictionPolicy.LRU ? 1 : 2)));
        assertEquals(List.of("three"), cache.get(key(3)));
    }

    @ParameterizedTest
    @EnumSource(EvictionPolicy.class)
    void shouldOverwriteWithoutConsumingCapacity(EvictionPolicy policy) {
        BoundedCache cache = new BoundedCache("sample.Mapper", 2, policy);
        cache.put(key(1), List.of("one"));
        cache.put(key(2), List.of("two"));
        cache.put(key(1), List.of("updated"));
        cache.put(key(3), List.of("three"));

        assertNull(cache.get(key(policy == EvictionPolicy.LRU ? 2 : 1)));
        assertEquals(policy == EvictionPolicy.LRU ? List.of("updated") : List.of("two"),
                cache.get(key(policy == EvictionPolicy.LRU ? 1 : 2)));
        assertEquals(List.of("three"), cache.get(key(3)));
    }

    @ParameterizedTest
    @EnumSource(EvictionPolicy.class)
    void shouldClearEntriesAndOrdering(EvictionPolicy policy) {
        BoundedCache cache = new BoundedCache("sample.Mapper", 2, policy);
        cache.put(key(1), List.of(1));
        cache.put(key(2), List.of(2));
        cache.get(key(1));
        cache.clear();
        assertNull(cache.get(key(1)));
        assertNull(cache.get(key(2)));

        cache.put(key(3), List.of(3));
        cache.put(key(1), List.of(1));
        cache.put(key(4), List.of(4));
        assertNull(cache.get(key(3)));
        assertEquals(List.of(1), cache.get(key(1)));
        assertEquals(List.of(4), cache.get(key(4)));
    }

    @ParameterizedTest
    @EnumSource(EvictionPolicy.class)
    void shouldSupportSingleEntryAndEmptyQueryResult(EvictionPolicy policy) {
        BoundedCache cache = new BoundedCache("sample.Mapper", 1, policy);
        cache.put(key(1), List.of());
        assertEquals(List.of(), cache.get(key(1)));
        cache.put(key(2), List.of(2));
        assertNull(cache.get(key(1)));
        assertEquals(List.of(2), cache.get(key(2)));
    }

    @Test
    void shouldRejectInvalidArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> new BoundedCache("sample.Mapper", 0, EvictionPolicy.LRU));
        assertThrows(IllegalArgumentException.class,
                () -> new BoundedCache("sample.Mapper", -1, EvictionPolicy.FIFO));
        assertThrows(NullPointerException.class,
                () -> new BoundedCache("sample.Mapper", 2, null));
        BoundedCache cache = new BoundedCache("sample.Mapper");
        assertEquals("sample.Mapper", cache.getId());
        assertThrows(NullPointerException.class, () -> cache.put(key(1), null));
        assertThrows(NullPointerException.class, () -> cache.put(null, List.of()));
        assertThrows(NullPointerException.class, () -> cache.get(null));
    }

    @ParameterizedTest
    @EnumSource(EvictionPolicy.class)
    void shouldRemoveEntryAndItsEvictionRecord(EvictionPolicy policy) {
        BoundedCache cache = new BoundedCache("sample.Mapper", 2, policy);
        cache.put(key(1), List.of(1));
        cache.put(key(2), List.of(2));
        assertEquals(List.of(1), cache.remove(key(1)));
        assertNull(cache.get(key(1)));
        cache.put(key(3), List.of(3));
        cache.put(key(1), List.of(10));
        assertNull(cache.get(key(2)));
        assertEquals(List.of(3), cache.get(key(3)));
        assertEquals(List.of(10), cache.get(key(1)));
    }

    @ParameterizedTest
    @EnumSource(EvictionPolicy.class)
    void shouldKeepCapacityUnderConcurrentAccess(EvictionPolicy policy) throws Exception {
        int capacity = 32;
        BoundedCache cache = new BoundedCache("sample.Mapper", capacity, policy);
        try (var executor = Executors.newFixedThreadPool(4)) {
            List<Future<?>> tasks = new ArrayList<>();
            for (int worker = 0; worker < 4; worker++) {
                int offset = worker * 500;
                tasks.add(executor.submit(() -> {
                    for (int index = 0; index < 500; index++) {
                        cache.put(key(offset + index), List.of(offset + index));
                        cache.get(key(offset + index));
                        if (index % 100 == 0) {
                            cache.clear();
                        }
                    }
                }));
            }
            for (Future<?> task : tasks) {
                task.get(10, TimeUnit.SECONDS);
            }
        }

        int retained = 0;
        for (int index = 0; index < 2000; index++) {
            List<Integer> result = cache.get(key(index));
            if (result != null) {
                assertEquals(List.of(index), result);
                retained++;
            }
        }
        assertEquals(capacity, retained);
    }

    private CacheKey key(int id) {
        return new CacheKey("sample.Mapper.select", "select name from user where id = ?", List.of(id));
    }
}
