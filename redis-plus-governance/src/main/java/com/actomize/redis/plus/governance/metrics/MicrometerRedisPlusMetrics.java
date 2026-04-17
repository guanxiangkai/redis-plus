package com.actomize.redis.plus.governance.metrics;

import com.actomize.redis.plus.core.metrics.RedisPlusMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Micrometer 的可观测性指标实现
 *
 * <p>当 {@code redis-plus-governance} 在 classpath 且 {@link MeterRegistry} 可用时，
 * 由 {@code redis-plus-starter} 自动替换默认的 {@link com.actomize.redis.plus.core.metrics.NoopRedisPlusMetrics}。
 *
 * <p><b>基数控制策略</b>：所有 tag 值均为低基数标识——模块名、缓存名称（通常几十个）、
 * 缓存层级（L1/L2）、限流算法名称等静态枚举值。
 * 完整 Redis Key（含动态 ID）绝不作为 Prometheus label，高基数信息通过 Trace/Log 记录。
 *
 * <p>指标命名规范（Prometheus 风格，使用下划线分隔）：
 * <ul>
 *   <li>{@code redis_plus_lock_acquired_total} — 锁成功获取次数</li>
 *   <li>{@code redis_plus_lock_failed_total} — 锁获取失败次数</li>
 *   <li>{@code redis_plus_lock_wait_duration_seconds} — 锁等待时长分布</li>
 *   <li>{@code redis_plus_lock_held_duration_seconds} — 锁持有时长分布</li>
 *   <li>{@code redis_plus_cache_hit_total} — 缓存命中次数（tag: cache, level=L1/L2）</li>
 *   <li>{@code redis_plus_cache_miss_total} — 缓存未命中次数（tag: cache）</li>
 *   <li>{@code redis_plus_cache_load_duration_seconds} — 回源耗时分布（tag: cache）</li>
 *   <li>{@code redis_plus_cache_penetration_total} — 缓存穿透次数（tag: cache）</li>
 *   <li>{@code redis_plus_ratelimit_passed_total} — 限流通过次数（tag: algorithm）</li>
 *   <li>{@code redis_plus_ratelimit_rejected_total} — 限流拒绝次数（tag: algorithm）</li>
 *   <li>{@code redis_plus_idempotent_passed_total} — 幂等首次执行次数</li>
 *   <li>{@code redis_plus_idempotent_duplicated_total} — 幂等重复请求次数</li>
 *   <li>{@code redis_plus_idempotent_retried_total} — 幂等操作重试次数（上次失败后重新执行）</li>
 * </ul>
 */
public class MicrometerRedisPlusMetrics implements RedisPlusMetrics {

    private static final String TAG_CACHE  = "cache";
    private static final String TAG_LEVEL  = "level";
    private static final String TAG_ALGO   = "algorithm";

    private final MeterRegistry registry;

    public MicrometerRedisPlusMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    // ── 锁指标（不以完整 lockKey 为 tag，避免高基数） ────────────────

    @Override
    public void recordLockAcquired(String lockName, Duration waitTime) {
        Counter.builder("redis_plus_lock_acquired_total")
                .register(registry).increment();
        Timer.builder("redis_plus_lock_wait_duration_seconds")
                .register(registry)
                .record(waitTime.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void recordLockFailed(String lockName) {
        Counter.builder("redis_plus_lock_failed_total")
                .register(registry).increment();
    }

    @Override
    public void recordLockReleased(String lockName, Duration heldTime) {
        Timer.builder("redis_plus_lock_held_duration_seconds")
                .register(registry)
                .record(heldTime.toMillis(), TimeUnit.MILLISECONDS);
    }

    // ── 缓存指标（cacheName 低基数，L1/L2 固定枚举） ─────────────────

    @Override
    public void recordCacheHit(String cacheName, String level) {
        Counter.builder("redis_plus_cache_hit_total")
                .tag(TAG_CACHE, cacheName).tag(TAG_LEVEL, level)
                .register(registry).increment();
    }

    @Override
    public void recordCacheMiss(String cacheName) {
        Counter.builder("redis_plus_cache_miss_total")
                .tag(TAG_CACHE, cacheName)
                .register(registry).increment();
    }

    @Override
    public void recordCacheLoad(String cacheName, Duration loadTime) {
        Timer.builder("redis_plus_cache_load_duration_seconds")
                .tag(TAG_CACHE, cacheName)
                .register(registry)
                .record(loadTime.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void recordCachePenetration(String cacheName) {
        Counter.builder("redis_plus_cache_penetration_total")
                .tag(TAG_CACHE, cacheName)
                .register(registry).increment();
    }

    @Override
    public void recordCacheEvict(String cacheName) {
        Counter.builder("redis_plus_cache_evict_total")
                .tag(TAG_CACHE, cacheName)
                .register(registry).increment();
    }

    // ── 限流指标（用 limitKey 中的算法前缀作为 tag，而非完整 key） ──

    @Override
    public void recordRateLimitPassed(String limitKey) {
        Counter.builder("redis_plus_ratelimit_passed_total")
                .tag(TAG_ALGO, extractAlgorithm(limitKey))
                .register(registry).increment();
    }

    @Override
    public void recordRateLimitRejected(String limitKey) {
        Counter.builder("redis_plus_ratelimit_rejected_total")
                .tag(TAG_ALGO, extractAlgorithm(limitKey))
                .register(registry).increment();
    }

    // ── 幂等指标（仅计总量，不记录完整幂等 key） ────────────────────

    @Override
    public void recordIdempotentPassed(String idempotentKey) {
        Counter.builder("redis_plus_idempotent_passed_total")
                .register(registry).increment();
    }

    @Override
    public void recordIdempotentDuplicated(String idempotentKey) {
        Counter.builder("redis_plus_idempotent_duplicated_total")
                .register(registry).increment();
    }

    @Override
    public void recordIdempotentRetried(String idempotentKey) {
        Counter.builder("redis_plus_idempotent_retried_total")
                .register(registry).increment();
    }

    // ── 工具方法 ─────────────────────────────────────────────────────

    /**
     * 从限流 Key 中提取算法标识（低基数）。
     *
     * <p>限流 Key 格式：{@code redis-plus:ratelimit:[algorithm]:[business-key]}，
     * 例如 {@code redis-plus:ratelimit:sliding:user:123}。
     * 提取第三段（{@code sliding}）作为 algorithm tag；提取失败则返回 {@code "unknown"}。
     */
    private static String extractAlgorithm(String limitKey) {
        if (limitKey == null) return "unknown";
        String[] parts = limitKey.split(":", 5);
        // 格式: [0]=redis-plus [1]=ratelimit [2]=algorithm [3]=...business...
        return parts.length >= 3 ? parts[2] : "unknown";
    }
}

