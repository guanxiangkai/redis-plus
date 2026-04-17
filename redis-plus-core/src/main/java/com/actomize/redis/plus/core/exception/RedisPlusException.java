package com.actomize.redis.plus.core.exception;

/**
 * Redis Plus 根异常
 *
 * <p>所有 redis-plus 模块抛出的运行时异常都继承此类，
 * 使用方可统一用 {@code catch (RedisPlusException e)} 捕获。
 */
public class RedisPlusException extends RuntimeException {

    public RedisPlusException(String message) {
        super(message);
    }

    public RedisPlusException(String message, Throwable cause) {
        super(message, cause);
    }

    public RedisPlusException(Throwable cause) {
        super(cause);
    }
}

