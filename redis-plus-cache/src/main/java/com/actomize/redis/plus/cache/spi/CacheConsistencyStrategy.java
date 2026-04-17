package com.actomize.redis.plus.cache.spi;

import com.actomize.redis.plus.core.async.DefaultRedisPlusAsyncExecutor;
import com.actomize.redis.plus.core.async.RedisPlusAsyncExecutor;

import java.time.Duration;

/**
 * 缓存一致性策略 SPI
 *
 * <p>定义写操作（更新 / 删除数据库记录）时如何处理缓存，以保证最终一致性。
 * 内置三种策略：
 * <ul>
 *   <li>{@link #invalidate()} — 直接删除缓存（简单场景首选）</li>
 *   <li>{@link #delayDoubleDelete(long)} — 先删后延迟再删（缓解并发写读不一致）</li>
 *   <li>{@link #writeThrough()} — 先更新数据库，再更新缓存</li>
 * </ul>
 */
public interface CacheConsistencyStrategy {

    RedisPlusAsyncExecutor DEFAULT_ASYNC_EXECUTOR = new DefaultRedisPlusAsyncExecutor("redis-plus-cache", 1);

    /**
     * 直接失效策略：写操作后立即删除 L1 + L2 缓存。
     */
    static CacheConsistencyStrategy invalidate() {
        return (cacheName, key, handler) -> handler.evict(cacheName, key);
    }

    // ── 内置实现工厂 ──────────────────────────────────────────────

    /**
     * 延迟双删策略：先删缓存，延迟指定毫秒后再删一次。
     *
     * @param delayMillis 延迟毫秒数（建议 500～2000ms）
     */
    static CacheConsistencyStrategy delayDoubleDelete(long delayMillis) {
        return delayDoubleDelete(delayMillis, DEFAULT_ASYNC_EXECUTOR);
    }

    static CacheConsistencyStrategy delayDoubleDelete(long delayMillis, RedisPlusAsyncExecutor asyncExecutor) {
        if (delayMillis <= 0) {
            throw new IllegalArgumentException("delayMillis 必须大于 0，实际值：" + delayMillis);
        }
        return (cacheName, key, handler) -> {
            handler.evict(cacheName, key);
            asyncExecutor.schedule("cache-delay-double-delete", Duration.ofMillis(delayMillis),
                    () -> handler.evict(cacheName, key));
        };
    }

    /**
     * 写穿策略：写操作后同步更新 L2 + L1 缓存。
     * 调用方须通过 {@link #afterWrite(String, String, Object, Duration, CacheOperationHandler)}
     * 传入新值；若使用无值重载 {@link #afterWrite(String, String, CacheOperationHandler)}
     * 则退化为失效策略。
     */
    static CacheConsistencyStrategy writeThrough() {
        return new CacheConsistencyStrategy() {
            @Override
            public void afterWrite(String cacheName, String key, CacheOperationHandler handler) {
                // 无新值时退化为失效，避免缓存不一致
                handler.evict(cacheName, key);
            }

            @Override
            public void afterWrite(String cacheName, String key, Object newValue,
                                   Duration ttl, CacheOperationHandler handler) {
                handler.put(cacheName, key, newValue, ttl);
            }
        };
    }

    /**
     * 在数据写操作完成后执行缓存一致性处理（无新值版本）。
     *
     * @param cacheName 缓存名
     * @param key       缓存 Key
     * @param handler   底层缓存操作接口（evict / put）
     */
    void afterWrite(String cacheName, String key, CacheOperationHandler handler);

    /**
     * 在数据写操作完成后执行缓存一致性处理（含新值版本，供 write-through 使用）。
     *
     * @param cacheName 缓存名
     * @param key       缓存 Key
     * @param newValue  写入数据库后的最新值
     * @param ttl       缓存过期时间
     * @param handler   底层缓存操作接口
     */
    default void afterWrite(String cacheName, String key, Object newValue,
                            Duration ttl, CacheOperationHandler handler) {
        // 默认退化为无值版本（仅 invalidate / delayDoubleDelete 策略走此路径）
        afterWrite(cacheName, key, handler);
    }

    // ── 缓存操作回调接口 ─────────────────────────────────────────

    /**
     * 缓存操作回调，由 {@link com.actomize.redis.plus.cache.ThreeLevelCacheTemplate} 实现
     */
    interface CacheOperationHandler {
        void evict(String cacheName, String key);

        /**
         * 同步写入 L1 + L2 缓存。
         *
         * @param cacheName 缓存名
         * @param key       缓存 Key
         * @param value     新值
         * @param ttl       过期时间
         */
        void put(String cacheName, String key, Object value, Duration ttl);
    }
}
