package com.actomize.redis.plus.cache.spi;

/**
 * 缓存穿透防护 SPI
 *
 * <p>在执行 L3 回源查询前，对 Key 做合法性预检，
 * 以避免无效 Key 持续穿透到数据库（缓存穿透攻击防护）。
 *
 * <p>内置策略：
 * <ul>
 *   <li>{@link #nullValue()} — 空值缓存策略（ThreeLevelCacheTemplate 内置）</li>
 *   <li>{@link #allowAll()} — 允许所有 Key（不防护，用于关闭防护的场景）</li>
 * </ul>
 *
 * <p>自定义示例（布隆过滤器预检）：
 * <pre>
 * {@literal @}Bean
 * public CachePenetrationGuard bloomGuard(BloomFilter&lt;String&gt; bloomFilter) {
 *     return key -> bloomFilter.mightContain(key);
 * }
 * </pre>
 */
@FunctionalInterface
public interface CachePenetrationGuard {

    /**
     * 空值缓存策略：允许所有 Key 回源，依赖 ThreeLevelCacheTemplate 缓存 null 值防穿透。
     * 这是框架内置默认行为。
     */
    static CachePenetrationGuard nullValue() {
        return key -> true;
    }

    // ── 内置工厂 ─────────────────────────────────────────────────────

    /**
     * 允许所有 Key（等同于关闭防护）。
     */
    static CachePenetrationGuard allowAll() {
        return key -> true;
    }

    /**
     * 黑名单策略：拒绝已知非法 Key 集合。
     *
     * @param blacklist 黑名单 Key 集合
     */
    static CachePenetrationGuard blacklist(java.util.Set<String> blacklist) {
        return key -> !blacklist.contains(key);
    }

    /**
     * 检查 Key 是否可能存在（允许继续回源）。
     *
     * @param key 待查询的缓存 Key（不含前缀）
     * @return {@code true} 表示 Key 可能存在，允许执行回源；
     * {@code false} 表示 Key 肯定不存在，直接返回 null，跳过回源
     */
    boolean isAllowed(String key);
}

