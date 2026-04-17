package com.actomize.redis.plus.core.async;

import java.time.Duration;

/**
 * redis-plus 统一异步执行器。
 */
public interface RedisPlusAsyncExecutor extends AutoCloseable {

    /**
     * 立即异步执行任务。
     */
    void execute(String taskName, Runnable task);

    /**
     * 延迟执行任务。
     */
    Cancellable schedule(String taskName, Duration delay, Runnable task);

    @Override
    default void close() {
    }

    @FunctionalInterface
    interface Cancellable {
        void cancel();
    }
}
