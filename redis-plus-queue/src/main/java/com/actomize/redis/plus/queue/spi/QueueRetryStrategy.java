package com.actomize.redis.plus.queue.spi;

import java.time.Duration;

/**
 * 消息队列重试策略 SPI
 *
 * <p>定义消费失败后的重试等待时间策略。
 * 框架在消费者抛出异常时回调此接口决定何时重试，
 * 支持固定间隔、指数退避、抖动等策略。
 */
@FunctionalInterface
public interface QueueRetryStrategy {

    /**
     * 固定间隔重试策略。
     *
     * @param interval    固定等待时长
     * @param maxAttempts 最大重试次数
     */
    static QueueRetryStrategy fixed(Duration interval, int maxAttempts) {
        return (attempt, ex) -> attempt <= maxAttempts ? interval : null;
    }

    // ── 内置工厂 ─────────────────────────────────────────────────────

    /**
     * 指数退避重试策略（每次翻倍，有上限）。
     *
     * @param initial     初始等待时长
     * @param multiplier  倍数（通常为 2）
     * @param maxDelay    最大等待时长上限
     * @param maxAttempts 最大重试次数
     */
    static QueueRetryStrategy exponentialBackoff(Duration initial, double multiplier,
                                                 Duration maxDelay, int maxAttempts) {
        return (attempt, ex) -> {
            if (attempt > maxAttempts) return null;
            long ms = (long) (initial.toMillis() * Math.pow(multiplier, attempt - 1));
            return Duration.ofMillis(Math.min(ms, maxDelay.toMillis()));
        };
    }

    /**
     * 不重试策略：消费失败直接转入死信队列。
     */
    static QueueRetryStrategy noRetry() {
        return (attempt, ex) -> null;
    }

    /**
     * 计算第 {@code attempt} 次重试的等待时长。
     *
     * @param attempt       重试次数（从 1 开始）
     * @param lastException 上次失败的异常
     * @return 等待时长；返回 {@code Duration.ZERO} 表示立即重试；
     * 返回 {@code null} 表示放弃重试（转入死信队列）
     */
    Duration nextDelay(int attempt, Exception lastException);
}

