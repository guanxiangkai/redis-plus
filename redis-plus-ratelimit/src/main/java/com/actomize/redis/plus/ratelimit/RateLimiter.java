package com.actomize.redis.plus.ratelimit;

/**
 * 限流器统一抽象
 *
 * <p>支持多种算法（固定窗口、滑动窗口、令牌桶），
 * 通过 {@link com.actomize.redis.plus.ratelimit.annotation.RateLimit} 注解或编程式调用接入。
 */
public interface RateLimiter<C extends RateLimitConfig> {

    /**
     * 尝试获取一个令牌（消费 1 个配额）。
     *
     * @param key    限流维度 Key（如 userId、IP、接口名）
     * @param config 算法专属配置
     * @return {@code true} 表示通过，{@code false} 表示被限流
     */
    boolean tryAcquire(String key, C config);
}
