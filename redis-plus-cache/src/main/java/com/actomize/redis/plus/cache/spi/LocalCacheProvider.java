package com.actomize.redis.plus.cache.spi;

import java.time.Duration;

/**
 * 本地缓存（L1）提供者 SPI
 *
 * <p>框架默认使用 Caffeine；用户可实现此接口替换为其他本地缓存容器。
 */
public interface LocalCacheProvider {
    /**
     * 从本地缓存获取值。
     *
     * @return 缓存值；不存在返回 {@code null}
     */
    Object get(String key);

    /**
     * 写入本地缓存。
     *
     * @param key   缓存 Key
     * @param value 缓存值
     * @param ttl   过期时间；{@code null} 或 {@link Duration#ZERO} 表示不过期
     */
    void put(String key, Object value, Duration ttl);

    /**
     * 删除本地缓存条目。
     */
    void evict(String key);

    /**
     * 清空所有本地缓存（谨慎使用）。
     */
    void clear();

    /**
     * 删除所有 Key 以 {@code keyPrefix} 开头的本地缓存条目。
     * 用于按缓存域（cacheName）精确清理，避免清空全部本地缓存。
     *
     * @param keyPrefix Key 前缀（通常为 {@code cacheName + ":"}）
     */
    void clearByPrefix(String keyPrefix);
}
