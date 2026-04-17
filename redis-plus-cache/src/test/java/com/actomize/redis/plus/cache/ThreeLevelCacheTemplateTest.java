package com.actomize.redis.plus.cache;

import com.actomize.redis.plus.cache.spi.CacheLoadProtection;
import com.actomize.redis.plus.cache.spi.CacheMetricsCollector;
import com.actomize.redis.plus.cache.spi.CacheValueSerializer;
import com.actomize.redis.plus.cache.spi.LocalCacheProvider;
import com.actomize.redis.plus.core.expire.ExpireStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link ThreeLevelCacheTemplate} 单元测试（mock-based，无需 Redis）
 */
class ThreeLevelCacheTemplateTest {

    private LocalCacheProvider l1;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private CacheValueSerializer serializer;
    private ThreeLevelCacheTemplate template;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        l1 = new SimpleLocalCache();
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        serializer = new CacheValueSerializer() {
            @Override
            public String serialize(Object value) {
                return String.valueOf(value);
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T deserialize(String data, Class<T> type) {
                return (T) data;
            }
        };

        template = new ThreeLevelCacheTemplate(
                l1, redisTemplate, serializer,
                CacheLoadProtection.noProtection(),
                ExpireStrategy.fixed(),
                CacheMetricsCollector.noop(),
                ""
        );
    }

    @Test
    void get_l1Hit_returnsDirectly() {
        l1.put("user:1", "Alice", Duration.ofMinutes(5));

        String result = template.get("user", "1", String.class, Duration.ofMinutes(5), k -> "fromDB");

        assertEquals("Alice", result);
        // L2 should not be queried
        verify(valueOps, never()).get(anyString());
    }

    @Test
    void get_l2Hit_backfillsL1() {
        when(valueOps.get("user:1")).thenReturn("Bob");
        when(redisTemplate.getExpire("user:1", java.util.concurrent.TimeUnit.MILLISECONDS)).thenReturn(Duration.ofMinutes(4).toMillis());

        String result = template.get("user", "1", String.class, Duration.ofMinutes(5), k -> "fromDB");

        assertEquals("Bob", result);
        // L1 should now have the value
        assertEquals("Bob", l1.get("user:1"));
        assertEquals(Duration.ofMinutes(4), ((SimpleLocalCache) l1).ttlOf("user:1"));
    }

    @Test
    void get_l2NullValue_returnsNullAndCachesMarker() {
        when(valueOps.get("user:1")).thenReturn("__REDIS_PLUS_NULL__");

        String result = template.get("user", "1", String.class, Duration.ofMinutes(5), k -> "fromDB");

        assertNull(result);
        // L1 should have NULL_MARKER
        assertNotNull(l1.get("user:1"));
    }

    @Test
    void get_miss_loadsFromL3() {
        when(valueOps.get("user:1")).thenReturn(null);

        String result = template.get("user", "1", String.class, Duration.ofMinutes(5), k -> "fromDB");

        assertEquals("fromDB", result);
        // Should write to L2
        verify(valueOps).set(eq("user:1"), eq("fromDB"), any(Duration.class));
        // Should write to L1
        assertEquals("fromDB", l1.get("user:1"));
        assertEquals(Duration.ofMinutes(5), ((SimpleLocalCache) l1).ttlOf("user:1"));
    }

    @Test
    void get_miss_usesShorterLocalTtlWhenConfigured() {
        when(valueOps.get("user:1")).thenReturn(null);

        String result = template.get("user", "1", String.class,
                Duration.ofMinutes(5), Duration.ofMinutes(1), k -> "fromDB");

        assertEquals("fromDB", result);
        assertEquals(Duration.ofMinutes(1), ((SimpleLocalCache) l1).ttlOf("user:1"));
    }

    @Test
    void get_miss_nullFromL3_cachesNullValue() {
        when(valueOps.get("user:1")).thenReturn(null);

        String result = template.get("user", "1", String.class, Duration.ofMinutes(5), k -> null);

        assertNull(result);
        verify(valueOps).set(eq("user:1"), eq("__REDIS_PLUS_NULL__"), any(Duration.class));
    }

    @Test
    void put_writesL1AndL2() {
        template.put("user", "1", "Alice", Duration.ofMinutes(5));

        verify(valueOps).set(eq("user:1"), eq("Alice"), eq(Duration.ofMinutes(5)));
        assertEquals("Alice", l1.get("user:1"));
    }

    @Test
    void evict_removesFromL1AndL2() {
        l1.put("user:1", "Alice", Duration.ofMinutes(5));
        template.evict("user", "1");

        assertNull(l1.get("user:1"));
        verify(redisTemplate).delete("user:1");
    }

    @Test
    void buildCacheKey_noPrefix() {
        assertEquals("user:42", template.buildCacheKey("user", "42"));
    }

    @Test
    void buildCacheKey_withPrefix() {
        ThreeLevelCacheTemplate t = new ThreeLevelCacheTemplate(
                l1, redisTemplate, serializer,
                CacheLoadProtection.noProtection(),
                ExpireStrategy.fixed(),
                CacheMetricsCollector.noop(),
                "app:"
        );
        assertEquals("app:user:42", t.buildCacheKey("user", "42"));
    }

    // Simple in-memory L1 cache for testing
    static class SimpleLocalCache implements LocalCacheProvider {
        private final ConcurrentHashMap<String, Object> map = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Duration> ttlMap = new ConcurrentHashMap<>();

        @Override
        public Object get(String key) {
            return map.get(key);
        }

        @Override
        public void put(String key, Object value, Duration ttl) {
            map.put(key, value);
            ttlMap.put(key, ttl);
        }

        @Override
        public void evict(String key) {
            map.remove(key);
            ttlMap.remove(key);
        }

        @Override
        public void clear() {
            map.clear();
            ttlMap.clear();
        }

        @Override
        public void clearByPrefix(String keyPrefix) {
            map.keySet().removeIf(k -> k.startsWith(keyPrefix));
            ttlMap.keySet().removeIf(k -> k.startsWith(keyPrefix));
        }

        Duration ttlOf(String key) {
            return ttlMap.get(key);
        }
    }
}
