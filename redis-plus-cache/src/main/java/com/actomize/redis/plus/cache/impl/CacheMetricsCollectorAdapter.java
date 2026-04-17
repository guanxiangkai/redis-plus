package com.actomize.redis.plus.cache.impl;

import com.actomize.redis.plus.cache.spi.CacheMetricsCollector;
import com.actomize.redis.plus.core.metrics.NoopRedisPlusMetrics;
import com.actomize.redis.plus.core.metrics.RedisPlusMetrics;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.util.Objects;

/**
 * 将 {@link RedisPlusMetrics} 适配为 {@link CacheMetricsCollector}，
 * 通过 {@link ObjectProvider} <em>懒解析</em>，彻底解耦 AutoConfiguration 注册顺序。
 *
 * <h3>为何使用 ObjectProvider</h3>
 * <p>{@code RedisPlusCacheAutoConfiguration} 先于 {@code RedisPlusGovernanceAutoConfiguration}
 * 运行，此时 Micrometer {@link RedisPlusMetrics} Bean 尚未注册。若在构造期直接解析，
 * 只能拿到 Noop 实例；改用 {@link ObjectProvider} 后，每次方法调用时动态查找，
 * 可正确获取 Governance 模块注册的 {@code MicrometerRedisPlusMetrics}。
 *
 * <p>此适配器使 cache 模块完全不感知 governance 模块的存在，
 * 同时保证 {@link com.actomize.redis.plus.cache.ThreeLevelCacheTemplate}
 * 使用统一的 {@link CacheMetricsCollector} SPI。
 */
public class CacheMetricsCollectorAdapter implements CacheMetricsCollector {

    private final ObjectProvider<RedisPlusMetrics> metricsProvider;

    public CacheMetricsCollectorAdapter(ObjectProvider<RedisPlusMetrics> metricsProvider) {
        this.metricsProvider = Objects.requireNonNull(metricsProvider, "metricsProvider must not be null");
    }

    // ── 懒解析：每次从 ApplicationContext 中查找最终注册的 RedisPlusMetrics ──

    private RedisPlusMetrics metrics() {
        return metricsProvider.getIfAvailable(NoopRedisPlusMetrics::new);
    }

    @Override
    public void recordHit(String cacheName, String level) {
        metrics().recordCacheHit(cacheName, level);
    }

    @Override
    public void recordMiss(String cacheName) {
        metrics().recordCacheMiss(cacheName);
    }

    @Override
    public void recordLoad(String cacheName, Duration loadTime) {
        metrics().recordCacheLoad(cacheName, loadTime);
    }

    @Override
    public void recordPenetration(String cacheName) {
        metrics().recordCachePenetration(cacheName);
    }

    @Override
    public void recordEvict(String cacheName) {
        metrics().recordCacheEvict(cacheName);
    }
}
