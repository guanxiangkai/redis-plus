package com.actomize.redis.plus.cache.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 三级缓存查询注解
 *
 * <p>标注在方法上，框架自动执行 L1→L2→L3 三级缓存查询流程。
 * 方法的返回值即为缓存值；返回 {@code null} 时缓存空值防穿透。
 *
 * <p>示例：
 * <pre>
 * {@literal @}ThreeLevelCacheable(name = "user", key = "#id", ttl = 30)
 * public User getUser(Long id) {
 *     return userRepository.findById(id).orElse(null);
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ThreeLevelCacheable {

    /**
     * 缓存名称（用作 Key 前缀和指标标签）。
     * 例如 {@code "user"} 最终 Key 为 {@code user:{key}}
     */
    String name();

    /**
     * 缓存 Key，支持 SpEL 表达式（以 {@code #} 引用方法参数）。
     * 例如 {@code "'detail:' + #id"}
     */
    String key();

    /**
     * 基准 TTL 时长（默认 30）。
     */
    long ttl() default 30;

    /**
     * TTL 时间单位（默认秒）。
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * L1 本地缓存 TTL（通常比 L2 短；默认 0 表示与 L2 TTL 相同）。
     */
    long localTtl() default 0;

    /**
     * 是否启用空值缓存（防穿透，默认 true）。
     */
    boolean cacheNullValues() default true;
}

