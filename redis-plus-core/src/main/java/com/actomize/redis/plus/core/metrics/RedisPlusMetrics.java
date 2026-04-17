package com.actomize.redis.plus.core.metrics;

import java.time.Duration;

/**
 * Redis Plus 可观测性指标 SPI
 *
 * <p>框架各模块通过此接口上报指标，与具体指标库（Micrometer 等）解耦。
 * 默认实现为 {@link NoopRedisPlusMetrics}（空操作），
 * 引入 {@code redis-plus-governance} 时自动切换为 Micrometer 实现。
 */
public interface RedisPlusMetrics {

    // ── 锁 ──────────────────────────────────────────────────────────

    /**
     * 记录锁成功获取
     */
    void recordLockAcquired(String lockName, Duration waitTime);

    /**
     * 记录锁获取失败
     */
    void recordLockFailed(String lockName);

    /**
     * 记录锁释放
     */
    void recordLockReleased(String lockName, Duration heldTime);

    // ── 缓存 ────────────────────────────────────────────────────────

    /**
     * 记录缓存命中（指定缓存层级：L1/L2）
     */
    void recordCacheHit(String cacheName, String level);

    /**
     * 记录缓存未命中
     */
    void recordCacheMiss(String cacheName);

    /**
     * 记录缓存回源（DB 加载）耗时
     */
    void recordCacheLoad(String cacheName, Duration loadTime);

    /**
     * 记录缓存穿透次数
     */
    void recordCachePenetration(String cacheName);

    /**
     * 记录缓存失效次数（evict / clear 触发）
     */
    void recordCacheEvict(String cacheName);

    // ── 限流 ────────────────────────────────────────────────────────

    /**
     * 记录限流通过
     */
    void recordRateLimitPassed(String limitKey);

    /**
     * 记录限流拒绝
     */
    void recordRateLimitRejected(String limitKey);

    // ── 幂等 ────────────────────────────────────────────────────────

    /**
     * 记录幂等请求通过（首次执行）
     */
    void recordIdempotentPassed(String idempotentKey);

    /**
     * 记录幂等请求重复（直接返回缓存结果）
     */
    void recordIdempotentDuplicated(String idempotentKey);

    /**
     * 记录幂等操作重试（上次失败，本次重新执行）
     */
    void recordIdempotentRetried(String idempotentKey);
}

