package com.actomize.redis.plus.cache.annotation;

import java.lang.annotation.*;

/**
 * 三级缓存失效注解
 *
 * <p>标注在方法上，框架在方法执行后自动删除 L1 + L2 缓存中的指定条目。
 *
 * <p>示例：
 * <pre>
 * {@literal @}ThreeLevelCacheEvict(name = "user", key = "#user.id")
 * public void updateUser(User user) {
 *     userRepository.save(user);
 * }
 *
 * // 清空整个缓存域（谨慎使用）
 * {@literal @}ThreeLevelCacheEvict(name = "user", allEntries = true)
 * public void clearAllUsers() { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ThreeLevelCacheEvict {

    /**
     * 缓存名称
     */
    String name();

    /**
     * 缓存 Key（支持 SpEL）。
     * 当 {@link #allEntries()} 为 {@code true} 时，此属性被忽略。
     */
    String key() default "";

    /**
     * 是否清空整个缓存域（默认 false）。
     * 设置为 {@code true} 时调用 {@code clear(name)} 而非 {@code evict(name, key)}。
     */
    boolean allEntries() default false;

    /**
     * 是否在方法执行前删除缓存（默认 false，即执行后删除）。
     */
    boolean beforeInvocation() default false;
}

