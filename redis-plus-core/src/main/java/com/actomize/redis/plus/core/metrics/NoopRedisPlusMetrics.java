package com.actomize.redis.plus.core.metrics;

import java.time.Duration;

/**
 * 空操作指标实现（No-op）
 *
 * <p>当用户未引入 Micrometer 或 {@code redis-plus-governance} 时，
 * 由 {@code redis-plus-starter} 自动装配此实现（{@code @ConditionalOnMissingBean}），
 * 避免 NPE 且对性能零影响。
 */
public class NoopRedisPlusMetrics implements RedisPlusMetrics {

    /**
     * 共享单例，避免反复 new
     */
    public static final NoopRedisPlusMetrics INSTANCE = new NoopRedisPlusMetrics();

    @Override
    public void recordLockAcquired(String lockName, Duration waitTime) {
    }

    @Override
    public void recordLockFailed(String lockName) {
    }

    @Override
    public void recordLockReleased(String lockName, Duration heldTime) {
    }

    @Override
    public void recordCacheHit(String cacheName, String level) {
    }

    @Override
    public void recordCacheMiss(String cacheName) {
    }

    @Override
    public void recordCacheLoad(String cacheName, Duration loadTime) {
    }

    @Override
    public void recordCachePenetration(String cacheName) {
    }

    @Override
    public void recordCacheEvict(String cacheName) {
    }

    @Override
    public void recordRateLimitPassed(String limitKey) {
    }

    @Override
    public void recordRateLimitRejected(String limitKey) {
    }

    @Override
    public void recordIdempotentPassed(String idempotentKey) {
    }

    @Override
    public void recordIdempotentDuplicated(String idempotentKey) {
    }

    @Override
    public void recordIdempotentRetried(String idempotentKey) {
    }
}

