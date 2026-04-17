package com.actomize.redis.plus.ratelimit.spi;

import com.actomize.redis.plus.ratelimit.RateLimitConfig;

/**
 * 限流算法扩展 SPI
 *
 * <p>允许用户注册自定义限流算法，与 {@link com.actomize.redis.plus.ratelimit.RateLimiter}
 * 接口配合使用。注册为 Spring Bean 后，{@code RateLimitAspect} 会按算法名称匹配调用。
 *
 * <p>示例（Guava RateLimiter 包装）：
 * <pre>
 * {@literal @}Bean
 * public RateLimitAlgorithm guavaTokenBucket() {
 *     return new RateLimitAlgorithm() {
 *         public String algorithmName() { return "GUAVA_TOKEN_BUCKET"; }
 *         public boolean tryAcquire(String key, RateLimitConfig config) {
 *             return globalLimiter.tryAcquire();
 *         }
 *     };
 * }
 * </pre>
 */
public interface RateLimitAlgorithm {

    /**
     * 算法唯一名称标识（与 {@code @RateLimit(algorithm)} 枚举名对应，大小写不敏感）。
     */
    String algorithmName();

    /**
     * 尝试获取令牌（执行限流检查）。
     *
     * @param key    限流维度 Key（已包含前缀）
     * @param config 算法配置
     * @return {@code true} 表示通过限流；{@code false} 表示被拒绝
     */
    boolean tryAcquire(String key, RateLimitConfig config);
}
