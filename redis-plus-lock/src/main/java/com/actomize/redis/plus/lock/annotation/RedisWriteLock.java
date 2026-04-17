package com.actomize.redis.plus.lock.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 分布式写锁注解（排他锁，无读锁和写锁时才能获取）
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RedisWriteLock {

    /**
     * 锁名称（支持 SpEL）
     */
    String key();

    /**
     * 最大等待时间（默认 5 秒）
     */
    long waitTime() default -1;

    /**
     * 锁持有时间（默认 -1 表示 WatchDog 续期）
     */
    long leaseTime() default -1;

    /**
     * 时间单位，默认秒
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;
}
