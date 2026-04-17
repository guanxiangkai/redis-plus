package com.actomize.redis.plus.cache.local;

import com.actomize.redis.plus.cache.spi.LocalCacheProvider;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;

import java.time.Duration;

/**
 * 基于 Caffeine 的 L1 本地缓存实现（默认实现）
 *
 * <p>支持按条目独立 TTL：TTL 随值一同封装在 {@link CacheEntry} record 中传入 Caffeine，
 * 在 {@link Expiry#expireAfterCreate} 回调时从条目自身读取，消除并发 put 同一 Key
 * 时通过侧信道（ConcurrentHashMap）传递 TTL 所引发的竞态条件。
 *
 * <p>由 {@code redis-plus-starter} 在 {@code @ConditionalOnClass(Caffeine.class)} 下注册。
 */
public class CaffeineLocalCacheProvider implements LocalCacheProvider {

    private final long defaultTtlNanos;
    private final Cache<String, CacheEntry> cache;

    public CaffeineLocalCacheProvider(long maximumSize, Duration defaultTtl) {
        this.defaultTtlNanos = defaultTtl.toNanos();
        this.cache = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfter(new Expiry<String, CacheEntry>() {
                    @Override
                    public long expireAfterCreate(String key, CacheEntry entry, long currentTime) {
                        return entry.ttlNanos() > 0 ? entry.ttlNanos() : defaultTtlNanos;
                    }

                    @Override
                    public long expireAfterUpdate(String key, CacheEntry entry, long currentTime, long currentDuration) {
                        return entry.ttlNanos() > 0 ? entry.ttlNanos() : currentDuration;
                    }

                    @Override
                    public long expireAfterRead(String key, CacheEntry entry, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
    }

    @Override
    public Object get(String key) {
        CacheEntry entry = cache.getIfPresent(key);
        return entry != null ? entry.value() : null;
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        long nanos = (ttl != null && !ttl.isZero() && !ttl.isNegative()) ? ttl.toNanos() : 0L;
        cache.put(key, new CacheEntry(value, nanos));
    }

    @Override
    public void clear() {
        cache.invalidateAll();
    }

    @Override
    public void evict(String key) {
        cache.invalidate(key);
    }

    @Override
    public void clearByPrefix(String keyPrefix) {
        cache.asMap().keySet().removeIf(k -> k.startsWith(keyPrefix));
    }

    /**
     * 内部封装值和 TTL，消除 TTL 侧信道竞态
     */
    private record CacheEntry(Object value, long ttlNanos) {
    }
}

