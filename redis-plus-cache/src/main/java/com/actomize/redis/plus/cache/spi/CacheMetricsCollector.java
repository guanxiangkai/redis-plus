package com.actomize.redis.plus.cache.spi;

import java.time.Duration;

/**
 * 缓存指标采集 SPI
 *
 * <p>提供比 {@link com.actomize.redis.plus.core.metrics.RedisPlusMetrics} 更细粒度的
 * 缓存专属指标接口，供 {@link com.actomize.redis.plus.cache.ThreeLevelCacheTemplate}
 * 在缓存各个阶段调用。
 *
 * <p>默认实现为 no-op（{@link #noop()}）。
 * 当引入 Micrometer 时由治理模块提供完整实现。
 *
 * <p>自定义示例：
 * <pre>
 * {@literal @}Bean
 * public CacheMetricsCollector customCacheMetrics(MeterRegistry registry) {
 *     return new MicrometerCacheMetricsCollector(registry, "my_app");
 * }
 * </pre>
 */
public interface CacheMetricsCollector {

    /**
     * 返回空实现（no-op），不产生任何副作用
     */
    static CacheMetricsCollector noop() {
        return new CacheMetricsCollector() {
            @Override
            public void recordHit(String c, String l) {
            }

            @Override
            public void recordMiss(String c) {
            }

            @Override
            public void recordLoad(String c, Duration d) {
            }

            @Override
            public void recordPenetration(String c) {
            }

            @Override
            public void recordEvict(String c) {
            }
        };
    }

    /**
     * 记录缓存命中（L1/L2 层）
     */
    void recordHit(String cacheName, String level);

    /**
     * 记录缓存未命中（进入 L3 回源流程）
     */
    void recordMiss(String cacheName);

    /**
     * 记录 L3 回源耗时
     */
    void recordLoad(String cacheName, Duration loadTime);

    /**
     * 记录缓存穿透（L3 返回 null，缓存了空值）
     */
    void recordPenetration(String cacheName);

    // ── 工厂 ─────────────────────────────────────────────────────────

    /**
     * 记录缓存主动失效（evict 调用）
     */
    void recordEvict(String cacheName);
}

