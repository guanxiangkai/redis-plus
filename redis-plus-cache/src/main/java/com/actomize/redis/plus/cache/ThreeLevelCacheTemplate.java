package com.actomize.redis.plus.cache;

import com.actomize.redis.plus.cache.spi.*;
import com.actomize.redis.plus.core.exception.RedisCacheException;
import com.actomize.redis.plus.core.expire.ExpireStrategy;
import com.actomize.redis.plus.core.key.DefaultKeyNamingStrategy;
import com.actomize.redis.plus.core.key.KeyNamespaceUtils;
import com.actomize.redis.plus.core.key.KeyNamingStrategy;
import com.actomize.redis.plus.core.observation.RedisPlusObservationType;
import com.actomize.redis.plus.core.observation.RedisPlusObserver;
import com.actomize.redis.plus.core.util.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 三级缓存模板
 *
 * <p>查询流程：L1（本地 Caffeine）→ L2（Redis）→ L3（数据库回源）
 *
 * <ul>
 *   <li>L1 命中：直接返回，最快路径</li>
 *   <li>L2 命中：回填 L1，返回</li>
 *   <li>L2 未命中：通过分布式写锁保护，防止缓存击穿，回源 L3 后写入 L2 + L1</li>
 *   <li>L3 返回 null：缓存空值（空值 TTL 缩短为 baseTtl/5），防止缓存穿透</li>
 * </ul>
 *
 * <p>缓存失效行为由 {@link CacheConsistencyStrategy} 策略控制，
 * 默认使用 {@link CacheConsistencyStrategy#invalidate()} 简单失效策略。
 *
 * <p>使用示例：
 * <pre>
 * // 编程式
 * User user = cacheTemplate.get("user", userId, User.class,
 *     Duration.ofMinutes(30), key -> userRepository.findById(key).orElse(null));
 *
 * // 注解式（由 {@link com.actomize.redis.plus.cache.aop.ThreeLevelCacheAspect} 驱动）
 * {@literal @}ThreeLevelCacheable(name = "user", key = "#id", ttl = 30)
 * public User getUser(Long id) { return userRepository.findById(id).orElse(null); }
 * </pre>
 */
public class ThreeLevelCacheTemplate {

    /**
     * L1 中存储的空值占位符（区分"缓存了 null"与"未缓存"）
     */
    static final Object NULL_MARKER = new Object() {
        @Override
        public String toString() {
            return "NULL_MARKER";
        }
    };
    /**
     * L2（Redis）中存储的空值占位符字符串
     */
    static final String NULL_VALUE = "__REDIS_PLUS_NULL__";
    private static final Logger log = LoggerFactory.getLogger(ThreeLevelCacheTemplate.class);
    /**
     * 回源保护锁的 Key 前缀
     */
    private static final String LOCK_PREFIX = "redis-plus:cache:load:";

    /**
     * 默认回源保护锁等待时间（秒）
     */
    private static final long DEFAULT_LOCK_WAIT_SEC = 10;

    /**
     * 默认回源保护锁租约时间（秒）
     */
    private static final long DEFAULT_LOCK_LEASE_SEC = 30;

    private final LocalCacheProvider l1;
    private final StringRedisTemplate redisTemplate;
    private final CacheValueSerializer serializer;
    private final CacheLoadProtection loadProtection;
    private final ExpireStrategy expireStrategy;
    private final CacheMetricsCollector cacheMetrics;
    private final CacheConsistencyStrategy consistencyStrategy;
    private final KeyNamingStrategy keyNamingStrategy;
    private final RedisPlusObserver observer;
    /**
     * 全局 Key 前缀（来自 redis-plus.cache.key-prefix），为空则不添加前缀
     */
    private final String keyNamespace;

    public ThreeLevelCacheTemplate(LocalCacheProvider l1,
                                   StringRedisTemplate redisTemplate,
                                   CacheValueSerializer serializer,
                                   CacheLoadProtection loadProtection,
                                   ExpireStrategy expireStrategy,
                                   CacheMetricsCollector cacheMetrics) {
        this(l1, redisTemplate, serializer, loadProtection, expireStrategy, cacheMetrics,
                new DefaultKeyNamingStrategy());
    }

    public ThreeLevelCacheTemplate(LocalCacheProvider l1,
                                   StringRedisTemplate redisTemplate,
                                   CacheValueSerializer serializer,
                                   CacheLoadProtection loadProtection,
                                   ExpireStrategy expireStrategy,
                                   CacheMetricsCollector cacheMetrics,
                                   KeyNamingStrategy keyNamingStrategy) {
        this(l1, redisTemplate, serializer, loadProtection, expireStrategy, cacheMetrics, "", keyNamingStrategy);
    }

    public ThreeLevelCacheTemplate(LocalCacheProvider l1,
                                   StringRedisTemplate redisTemplate,
                                   CacheValueSerializer serializer,
                                   CacheLoadProtection loadProtection,
                                   ExpireStrategy expireStrategy,
                                   CacheMetricsCollector cacheMetrics,
                                   String keyPrefix) {
        this(l1, redisTemplate, serializer, loadProtection, expireStrategy, cacheMetrics,
                keyPrefix, new DefaultKeyNamingStrategy());
    }

    public ThreeLevelCacheTemplate(LocalCacheProvider l1,
                                   StringRedisTemplate redisTemplate,
                                   CacheValueSerializer serializer,
                                   CacheLoadProtection loadProtection,
                                   ExpireStrategy expireStrategy,
                                   CacheMetricsCollector cacheMetrics,
                                   String keyPrefix,
                                   KeyNamingStrategy keyNamingStrategy) {
        this(l1, redisTemplate, serializer, loadProtection, expireStrategy, cacheMetrics, keyPrefix,
                CacheConsistencyStrategy.invalidate(), keyNamingStrategy);
    }

    public ThreeLevelCacheTemplate(LocalCacheProvider l1,
                                   StringRedisTemplate redisTemplate,
                                   CacheValueSerializer serializer,
                                   CacheLoadProtection loadProtection,
                                   ExpireStrategy expireStrategy,
                                   CacheMetricsCollector cacheMetrics,
                                   String keyPrefix,
                                   CacheConsistencyStrategy consistencyStrategy) {
        this(l1, redisTemplate, serializer, loadProtection, expireStrategy, cacheMetrics, keyPrefix,
                consistencyStrategy, new DefaultKeyNamingStrategy(), RedisPlusObserver.noop());
    }

    public ThreeLevelCacheTemplate(LocalCacheProvider l1,
                                   StringRedisTemplate redisTemplate,
                                   CacheValueSerializer serializer,
                                   CacheLoadProtection loadProtection,
                                   ExpireStrategy expireStrategy,
                                   CacheMetricsCollector cacheMetrics,
                                   String keyPrefix,
                                   CacheConsistencyStrategy consistencyStrategy,
                                   KeyNamingStrategy keyNamingStrategy) {
        this(l1, redisTemplate, serializer, loadProtection, expireStrategy, cacheMetrics,
                keyPrefix, consistencyStrategy, keyNamingStrategy, RedisPlusObserver.noop());
    }

    public ThreeLevelCacheTemplate(LocalCacheProvider l1,
                                   StringRedisTemplate redisTemplate,
                                   CacheValueSerializer serializer,
                                   CacheLoadProtection loadProtection,
                                   ExpireStrategy expireStrategy,
                                   CacheMetricsCollector cacheMetrics,
                                   String keyPrefix,
                                   CacheConsistencyStrategy consistencyStrategy,
                                   KeyNamingStrategy keyNamingStrategy,
                                   RedisPlusObserver observer) {
        this.l1 = l1;
        this.redisTemplate = redisTemplate;
        this.serializer = serializer;
        this.loadProtection = loadProtection;
        this.expireStrategy = expireStrategy;
        this.cacheMetrics = cacheMetrics != null ? cacheMetrics : CacheMetricsCollector.noop();
        this.keyNamespace = KeyNamespaceUtils.namespace(keyPrefix, "");
        this.consistencyStrategy = consistencyStrategy != null
                ? consistencyStrategy : CacheConsistencyStrategy.invalidate();
        this.keyNamingStrategy = keyNamingStrategy != null ? keyNamingStrategy : new DefaultKeyNamingStrategy();
        this.observer = observer != null ? observer : RedisPlusObserver.noop();
    }

    // ── 核心查询流程 ─────────────────────────────────────────────────

    /**
     * 三级缓存查询。
     *
     * @param cacheName 缓存名称（用于 Key 前缀和指标标签）
     * @param key       缓存业务 Key（不含前缀）
     * @param type      值类型，用于反序列化
     * @param ttl       基准 TTL（实际 TTL 由 {@link ExpireStrategy} 加工）
     * @param loader    L3 回源加载器；返回 {@code null} 表示数据不存在
     * @return 缓存值，可为 {@code null}
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String cacheName, String key, Class<T> type, Duration ttl, CacheLoader<T> loader) {
        return get(cacheName, key, type, ttl, Duration.ZERO, loader);
    }

    /**
     * 三级缓存查询，并允许单独指定 L1 本地缓存 TTL。
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String cacheName, String key, Class<T> type,
                     Duration ttl, Duration localTtl, CacheLoader<T> loader) {
        return observeCache(RedisPlusObservationType.CACHE_GET, cacheName, key, () -> {
            String fullKey = buildCacheKey(cacheName, key);

            Object l1Val = l1.get(fullKey);
            if (l1Val != null) {
                if (l1Val == NULL_MARKER) {
                    cacheMetrics.recordHit(cacheName, "L1");
                    return null;
                }
                cacheMetrics.recordHit(cacheName, "L1");
                return (T) l1Val;
            }

            String l2Val = redisTemplate.opsForValue().get(fullKey);
            if (l2Val != null) {
                Duration l1Ttl = resolveLocalCacheTtl(localTtl, resolveRedisTtl(fullKey, ttl));
                cacheMetrics.recordHit(cacheName, "L2");
                if (NULL_VALUE.equals(l2Val)) {
                    l1.put(fullKey, NULL_MARKER, l1Ttl);
                    return null;
                }
                T deserialized = serializer.deserialize(l2Val, type);
                l1.put(fullKey, deserialized, l1Ttl);
                return deserialized;
            }

            cacheMetrics.recordMiss(cacheName);
            return loadFromL3(cacheName, key, fullKey, type, ttl, localTtl, loader);
        });
    }

    /**
     * 手动写入缓存（同时写 L1 + L2）。
     */
    public <T> void put(String cacheName, String key, T value, Duration ttl) {
        observeCacheVoid(RedisPlusObservationType.CACHE_PUT, cacheName, key, () -> {
            String fullKey = buildCacheKey(cacheName, key);
            Duration actual = expireStrategy.computeTtl(ttl);
            redisTemplate.opsForValue().set(fullKey, serializer.serialize(value), actual);
            l1.put(fullKey, value, actual);
        });
    }

    /**
     * 失效缓存（通过 {@link CacheConsistencyStrategy} 处理，默认删除 L1 + L2）。
     */
    public void evict(String cacheName, String key) {
        observeCacheVoid(RedisPlusObservationType.CACHE_EVICT, cacheName, key, () -> {
            consistencyStrategy.afterWrite(cacheName, key, cacheOperationHandler());
            cacheMetrics.recordEvict(cacheName);
        });
    }

    /**
     * 清空指定缓存域（L1 按前缀清理，L2 按 pattern SCAN 删除）。
     * <p><b>生产环境谨慎使用，避免对 Redis 产生全量扫描压力。</b>
     */
    public void clear(String cacheName) {
        observeCacheVoid(RedisPlusObservationType.CACHE_EVICT, cacheName, "*", () -> {
            String prefix = buildCacheKey(cacheName, "");
            l1.clearByPrefix(prefix);
            String pattern = prefix + "*";
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(200).build();
            List<String> toDelete = new ArrayList<>();
            try (Cursor<String> cursor = redisTemplate.scan(options)) {
                cursor.forEachRemaining(toDelete::add);
            }
            if (!toDelete.isEmpty()) {
                redisTemplate.delete(toDelete);
            }
            cacheMetrics.recordEvict(cacheName);
        });
    }

    /**
     * 构建实际缓存 Key（包含全局 keyPrefix）。
     */
    public String buildCacheKey(String cacheName, String key) {
        if (keyNamespace.isEmpty()) {
            return keyNamingStrategy.resolve(cacheName, key);
        }
        return keyNamingStrategy.resolve(keyNamespace, cacheName, key);
    }

    /**
     * 判断 L1 本地缓存中的值是否为空值占位符。
     */
    public boolean isNullMarker(Object value) {
        return value == NULL_MARKER;
    }

    /**
     * 判断 L2 Redis 中的字符串是否表示已缓存空值。
     */
    public boolean isNullValue(String value) {
        return NULL_VALUE.equals(value);
    }

    /**
     * 将空值占位符写入 L1 本地缓存。
     */
    public void putNullMarker(String cacheName, String key, Duration ttl) {
        l1.put(buildCacheKey(cacheName, key), NULL_MARKER, ttl);
    }

    private Duration resolveRedisTtl(String fullKey, Duration fallback) {
        Long remainingMs = redisTemplate.getExpire(fullKey, TimeUnit.MILLISECONDS);
        if (remainingMs != null && remainingMs > 0) {
            return Duration.ofMillis(remainingMs);
        }
        return fallback;
    }

    private Duration resolveLocalCacheTtl(Duration localTtl, Duration l2Ttl) {
        if (localTtl == null || localTtl.isZero() || localTtl.isNegative()) {
            return l2Ttl;
        }
        if (l2Ttl == null || l2Ttl.isZero() || l2Ttl.isNegative()) {
            return localTtl;
        }
        return localTtl.compareTo(l2Ttl) <= 0 ? localTtl : l2Ttl;
    }

    /** 构建委托给底层 L1 + L2 操作的回调处理器（供一致性策略调用）。 */
    private CacheConsistencyStrategy.CacheOperationHandler cacheOperationHandler() {
        return new CacheConsistencyStrategy.CacheOperationHandler() {
            @Override
            public void evict(String cacheName, String key) {
                String fullKey = buildCacheKey(cacheName, key);
                l1.evict(fullKey);
                redisTemplate.delete(fullKey);
            }

            @Override
            public void put(String cacheName, String key, Object value, Duration ttl) {
                ThreeLevelCacheTemplate.this.put(cacheName, key, value, ttl);
            }
        };
    }

    // ── 内部实现 ─────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private <T> T loadFromL3(String cacheName, String key, String fullKey,
                             Class<T> type, Duration ttl, Duration localTtl, CacheLoader<T> loader) {
        String lockKey = LOCK_PREFIX + fullKey;
        CacheLoadProtection.LockHandle handle =
                loadProtection.acquire(lockKey, DEFAULT_LOCK_WAIT_SEC, DEFAULT_LOCK_LEASE_SEC, TimeUnit.SECONDS);
        if (handle == null) {
            log.warn("[redis-plus] 缓存回源锁等待超时，cacheName={}, key={}", cacheName, fullKey);
            throw new RedisCacheException(
                    "缓存回源锁等待超时，cacheName=" + cacheName + ", key=" + key);
        }
        try (handle) {
            // 双重检查：其他线程已完成回源
            String afterLock = redisTemplate.opsForValue().get(fullKey);
            if (afterLock != null) {
                Duration l1Ttl = resolveLocalCacheTtl(localTtl, resolveRedisTtl(fullKey, ttl));
                if (NULL_VALUE.equals(afterLock)) {
                    l1.put(fullKey, NULL_MARKER, l1Ttl);
                    return null;
                }
                T deserialized = serializer.deserialize(afterLock, type);
                l1.put(fullKey, deserialized, l1Ttl);
                return deserialized;
            }

            // 真正执行 L3 回源
            Instant start = Instant.now();
            T loaded = loader.load(key);
            cacheMetrics.recordLoad(cacheName, Duration.between(start, Instant.now()));

            Duration actualTtl = expireStrategy.computeTtl(ttl);
            Duration actualLocalTtl = resolveLocalCacheTtl(localTtl, actualTtl);

            if (loaded == null) {
                // 空值缓存防穿透：TTL 取 baseTtl/5 与 30s 的较大值，防止极小 TTL 仍被穿透
                Duration nullTtl = Duration.ofMillis(Math.max(ttl.toMillis() / 5, 30_000L));
                redisTemplate.opsForValue().set(fullKey, NULL_VALUE, nullTtl);
                l1.put(fullKey, NULL_MARKER, resolveLocalCacheTtl(localTtl, nullTtl));
                cacheMetrics.recordPenetration(cacheName);
                return null;
            }

            // 写回 L2 + L1
            redisTemplate.opsForValue().set(fullKey, serializer.serialize(loaded), actualTtl);
            l1.put(fullKey, loaded, actualLocalTtl);
            return loaded;
        }
    }

    private <T> T observeCache(RedisPlusObservationType type,
                               String cacheName,
                               String key,
                               RedisPlusObserver.CheckedSupplier<T> supplier) {
        try {
            return observer.observe(type,
                    Map.of("cache.name", cacheName),
                    Map.of("cache.key", key),
                    supplier);
        } catch (Throwable t) {
            ExceptionUtils.sneakyThrow(t);
            throw new AssertionError();
        }
    }

    private void observeCacheVoid(RedisPlusObservationType type,
                                  String cacheName,
                                  String key,
                                  RedisPlusObserver.CheckedRunnable runnable) {
        try {
            observer.observe(type,
                    Map.of("cache.name", cacheName),
                    Map.of("cache.key", key),
                    runnable);
        } catch (Throwable t) {
            ExceptionUtils.sneakyThrow(t);
            throw new AssertionError();
        }
    }
}
