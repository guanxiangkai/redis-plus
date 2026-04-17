package com.actomize.redis.plus.lock.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 分布式互斥锁注解
 *
 * <p>标注在方法上，AOP 切面会在方法执行前自动获取分布式锁，执行完成后释放。
 *
 * <p>示例：
 * <pre>
 * {@literal @}RedisLock(key = "'order:pay:' + #orderId", waitTime = 3)
 * public void payOrder(Long orderId) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RedisLock {

    /**
     * 锁名称，支持 SpEL 表达式（以 {@code #} 引用方法参数）。
     * 例如 {@code "'order:' + #id"}
     */
    String key();

    /**
     * 最大等待时间（默认 5 秒）。
     * 超过此时间仍未获取到锁则触发 {@code onFailure} 处理。
     */
    long waitTime() default -1;

    /**
     * 锁持有时间（默认 -1 表示启用 WatchDog 自动续期）。
     * 设置为正值时禁用 WatchDog，到期自动释放。
     */
    long leaseTime() default -1;

    /**
     * 时间单位，默认秒
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;
}
