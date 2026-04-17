package com.actomize.redis.plus.core.async;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 默认统一异步执行器实现。
 */
public class DefaultRedisPlusAsyncExecutor implements RedisPlusAsyncExecutor {

    private final ExecutorService workerPool;
    private final ScheduledExecutorService scheduler;

    public DefaultRedisPlusAsyncExecutor() {
        this("redis-plus-async", Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
    }

    public DefaultRedisPlusAsyncExecutor(String threadPrefix, int schedulerThreads) {
        Objects.requireNonNull(threadPrefix, "threadPrefix must not be null");
        if (schedulerThreads <= 0) {
            throw new IllegalArgumentException("schedulerThreads must be > 0");
        }
        this.workerPool = Executors.newThreadPerTaskExecutor(Thread.ofVirtual()
                .name(threadPrefix + "-worker-", 0)
                .factory());
        this.scheduler = Executors.newScheduledThreadPool(
                schedulerThreads,
                daemonFactory(threadPrefix + "-scheduler-"));
    }

    private static ThreadFactory daemonFactory(String prefix) {
        AtomicInteger index = new AtomicInteger();
        return runnable -> Thread.ofPlatform()
                .name(prefix + index.incrementAndGet())
                .daemon(true)
                .unstarted(runnable);
    }

    @Override
    public void execute(String taskName, Runnable task) {
        Objects.requireNonNull(task, "task must not be null");
        workerPool.execute(task);
    }

    @Override
    public Cancellable schedule(String taskName, Duration delay, Runnable task) {
        Objects.requireNonNull(delay, "delay must not be null");
        Objects.requireNonNull(task, "task must not be null");
        ScheduledFuture<?> future = scheduler.schedule(
                () -> workerPool.execute(task),
                Math.max(0L, delay.toMillis()),
                TimeUnit.MILLISECONDS);
        return () -> future.cancel(false);
    }

    @Override
    public void close() {
        workerPool.close();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
