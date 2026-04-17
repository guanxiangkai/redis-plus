package com.actomize.redis.plus.cache;

import com.actomize.redis.plus.cache.spi.CacheLoader;

import java.time.Duration;

/**
 * 三级缓存用户接口（类型安全门面）
 *
 * <p>对 {@link ThreeLevelCacheTemplate} 进行轻量包装，提供类型参数绑定，
 * 减少业务代码中重复传入 {@code cacheName} 和 {@code Class<T>} 的样板。
 *
 * <p>使用示例：
 * <pre>
 * // 注入或构造
 * ThreeLevelCache&lt;User&gt; userCache = new ThreeLevelCache&lt;&gt;(
 *         "user", User.class, Duration.ofMinutes(30), cacheTemplate);
 *
 * // 查询（未命中时自动回源）
 * User user = userCache.get(String.valueOf(userId),
 *         key -> userRepository.findById(Long.parseLong(key)).orElse(null));
 *
 * // 主动写入
 * userCache.put(String.valueOf(userId), user);
 *
 * // 失效
 * userCache.evict(String.valueOf(userId));
 * </pre>
 *
 * @param <V> 缓存值类型
 */
public class ThreeLevelCache<V> {

    private final String cacheName;
    private final Class<V> valueType;
    private final Duration defaultTtl;
    private final ThreeLevelCacheTemplate template;

    /**
     * @param cacheName  缓存域名称（用于 Key 前缀和指标标签）
     * @param valueType  缓存值类型，用于反序列化
     * @param defaultTtl 默认 TTL（可被单次调用覆盖）
     * @param template   底层三级缓存模板
     */
    public ThreeLevelCache(String cacheName,
                           Class<V> valueType,
                           Duration defaultTtl,
                           ThreeLevelCacheTemplate template) {
        this.cacheName = cacheName;
        this.valueType = valueType;
        this.defaultTtl = defaultTtl;
        this.template = template;
    }

    // ── 查询 ──────────────────────────────────────────────────────────

    /**
     * 三级缓存查询（使用默认 TTL）。
     *
     * @param key    业务 Key
     * @param loader L3 回源加载器；返回 {@code null} 表示数据不存在
     * @return 缓存值，可为 {@code null}
     */
    public V get(String key, CacheLoader<V> loader) {
        return template.get(cacheName, key, valueType, defaultTtl, loader);
    }

    /**
     * 三级缓存查询（指定 TTL，覆盖默认值）。
     *
     * @param key    业务 Key
     * @param ttl    本次缓存 TTL
     * @param loader L3 回源加载器
     * @return 缓存值，可为 {@code null}
     */
    public V get(String key, Duration ttl, CacheLoader<V> loader) {
        return template.get(cacheName, key, valueType, ttl, loader);
    }

    // ── 写入 ──────────────────────────────────────────────────────────

    /**
     * 手动写入缓存（使用默认 TTL）。
     *
     * @param key   业务 Key
     * @param value 缓存值（不可为 null）
     */
    public void put(String key, V value) {
        template.put(cacheName, key, value, defaultTtl);
    }

    /**
     * 手动写入缓存（指定 TTL）。
     *
     * @param key   业务 Key
     * @param value 缓存值
     * @param ttl   TTL
     */
    public void put(String key, V value, Duration ttl) {
        template.put(cacheName, key, value, ttl);
    }

    // ── 失效 ──────────────────────────────────────────────────────────

    /**
     * 失效单个 Key（L1 + L2）。
     *
     * @param key 业务 Key
     */
    public void evict(String key) {
        template.evict(cacheName, key);
    }

    /**
     * 清空整个缓存域（L1 全清，L2 按 pattern SCAN 删除）。
     * <p><b>生产环境谨慎使用。</b>
     */
    public void clear() {
        template.clear(cacheName);
    }

    // ── 元信息 ────────────────────────────────────────────────────────

    public String getCacheName() {
        return cacheName;
    }

    public Class<V> getValueType() {
        return valueType;
    }

    public Duration getDefaultTtl() {
        return defaultTtl;
    }
}

