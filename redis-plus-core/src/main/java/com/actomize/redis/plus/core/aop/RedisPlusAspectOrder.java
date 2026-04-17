package com.actomize.redis.plus.core.aop;

import org.springframework.core.Ordered;

/**
 * redis-plus AOP 执行顺序常量。
 */
public final class RedisPlusAspectOrder {

    public static final int DATASOURCE = Ordered.HIGHEST_PRECEDENCE + 1;
    public static final int RATELIMIT = Ordered.HIGHEST_PRECEDENCE + 10;
    public static final int IDEMPOTENT = Ordered.HIGHEST_PRECEDENCE + 20;
    public static final int CACHE = Ordered.HIGHEST_PRECEDENCE + 30;
    public static final int LOCK = Ordered.HIGHEST_PRECEDENCE + 40;

    private RedisPlusAspectOrder() {
    }
}
