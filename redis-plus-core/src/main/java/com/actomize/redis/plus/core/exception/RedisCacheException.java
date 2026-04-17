package com.actomize.redis.plus.core.exception;

/**
 * 缓存操作异常
 */
public class RedisCacheException extends RedisPlusException {

    public RedisCacheException(String message) {
        super(message);
    }

    public RedisCacheException(String message, Throwable cause) {
        super(message, cause);
    }
}

