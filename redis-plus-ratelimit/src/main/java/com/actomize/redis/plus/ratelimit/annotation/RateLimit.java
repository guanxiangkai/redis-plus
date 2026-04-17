package com.actomize.redis.plus.ratelimit.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 限流注解
 *
 * <p>标注在方法上，框架自动在方法执行前进行限流检查。
 * 超出限流阈值时抛出 {@link com.actomize.redis.plus.core.exception.RedisPlusException}。
 *
 * <p>示例：
 * <pre>
 * // 每个用户每分钟最多 100 次
 * {@literal @}RateLimit(key = "'api:' + #userId", limit = 100, window = 1, unit = TimeUnit.MINUTES)
 * public Response query(Long userId) { ... }
 *
 * // 接口级别全局限流：每秒 500 次（滑动窗口）
 * {@literal @}RateLimit(key = "'api:order:create'", limit = 500, window = 1, algorithm = RateLimit.Algorithm.SLIDING_WINDOW)
 * public Order createOrder(OrderRequest req) { ... }
 *
 * // 令牌桶：容量 200，每秒补充 50 个令牌
 * {@literal @}RateLimit(key = "'api:order:create'", algorithm = RateLimit.Algorithm.TOKEN_BUCKET,
 *         capacity = 200, refillTokens = 50, refillPeriod = 1, refillUnit = TimeUnit.SECONDS)
 * public Order createOrder(OrderRequest req) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * 限流 Key，支持 SpEL 表达式。
     * 建议包含业务语义，如 {@code "'user:' + #userId + ':api:query'"}
     */
    String key();

    /**
     * 时间窗口内最大请求数
     */
    long limit() default 100;

    /**
     * 时间窗口大小
     */
    long window() default 1;

    /**
     * 时间单位（默认秒）
     */
    TimeUnit unit() default TimeUnit.SECONDS;

    /**
     * 桶容量（令牌桶 / 漏桶使用）。未显式指定时回退到 {@link #limit()}。
     */
    long capacity() default 0;

    /**
     * 每个补充周期新增的令牌数（令牌桶使用）。未显式指定时回退到全局默认配置。
     */
    long refillTokens() default 0;

    /**
     * 令牌桶补充周期大小。
     */
    long refillPeriod() default 1;

    /**
     * 令牌桶补充周期单位。
     */
    TimeUnit refillUnit() default TimeUnit.SECONDS;

    /**
     * 每个漏出周期处理的请求数（漏桶使用）。未显式指定时回退到 {@link #limit()}。
     */
    long leakTokens() default 0;

    /**
     * 漏桶漏出周期大小。
     */
    long leakPeriod() default 1;

    /**
     * 漏桶漏出周期单位。
     */
    TimeUnit leakUnit() default TimeUnit.SECONDS;

    /**
     * 限流算法（默认滑动窗口）
     */
    Algorithm algorithm() default Algorithm.SLIDING_WINDOW;

    enum Algorithm {
        /**
         * 固定窗口：实现简单，存在窗口边界突刺问题
         */
        FIXED_WINDOW,
        /**
         * 滑动窗口：精确，适合大多数场景
         */
        SLIDING_WINDOW,
        /**
         * 令牌桶：支持突发流量，适合需要平滑限流的场景
         */
        TOKEN_BUCKET,
        /**
         * 漏桶：以固定速率处理请求，超出桶容量直接拒绝，适合严格恒速场景
         */
        LEAKY_BUCKET
    }
}
