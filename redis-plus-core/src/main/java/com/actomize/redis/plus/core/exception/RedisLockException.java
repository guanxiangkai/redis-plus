package com.actomize.redis.plus.core.exception;

/**
 * 分布式锁异常
 */
public class RedisLockException extends RedisPlusException {

    public RedisLockException(String message) {
        super(message);
    }

    public RedisLockException(String message, Throwable cause) {
        super(message, cause);
    }
}

