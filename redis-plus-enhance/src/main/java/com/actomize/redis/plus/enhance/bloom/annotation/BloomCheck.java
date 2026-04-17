package com.actomize.redis.plus.enhance.bloom.annotation;

import java.lang.annotation.*;

/**
 * 布隆过滤器前置检查注解
 *
 * <p>标注在方法上，框架在方法执行前通过布隆过滤器检查参数 Key 是否可能存在。
 * 若过滤器返回"一定不存在"，则直接返回 {@code null}（或指定的 fallback），
 * 不执行方法体，从而根源上防止缓存穿透。
 *
 * <p>示例：
 * <pre>
 * {@literal @}BloomCheck(filter = "userId", key = "#id")
 * {@literal @}ThreeLevelCacheable(name = "user", key = "#id", ttl = 30)
 * public User getUser(Long id) {
 *     return userRepository.findById(id).orElse(null);
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface BloomCheck {

    /**
     * 布隆过滤器名称（对应 {@code BloomFilterFactory} 中注册的过滤器）。
     */
    String filter();

    /**
     * 检查的 Key，支持 SpEL 表达式。
     */
    String key();

    /**
     * 当过滤器判断"一定不存在"时的行为：
     * <ul>
     *   <li>{@code RETURN_NULL}（默认）— 直接返回 {@code null}</li>
     *   <li>{@code THROW_EXCEPTION} — 抛出异常</li>
     * </ul>
     */
    OnMiss onMiss() default OnMiss.RETURN_NULL;

    enum OnMiss {
        /**
         * 返回 null
         */
        RETURN_NULL,
        /**
         * 抛出 {@link com.actomize.redis.plus.core.exception.RedisCacheException}
         */
        THROW_EXCEPTION
    }
}

