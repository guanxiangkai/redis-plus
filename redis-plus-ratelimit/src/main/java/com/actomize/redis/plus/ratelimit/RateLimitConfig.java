package com.actomize.redis.plus.ratelimit;

import java.time.Duration;

/**
 * 限流配置模型。
 *
 * <p>不同算法使用各自独立的配置记录，避免将所有算法强行折叠为同一组参数。
 */
public sealed interface RateLimitConfig {

    private static void requirePositive(long value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be > 0");
        }
    }

    private static void requirePositive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be > 0");
        }
    }

    record FixedWindow(long limit, Duration window) implements RateLimitConfig {
        public FixedWindow {
            requirePositive(limit, "limit");
            requirePositive(window, "window");
        }
    }

    record SlidingWindow(long limit, Duration window) implements RateLimitConfig {
        public SlidingWindow {
            requirePositive(limit, "limit");
            requirePositive(window, "window");
        }
    }

    record TokenBucket(long capacity, long refillTokens, Duration refillPeriod) implements RateLimitConfig {
        public TokenBucket {
            requirePositive(capacity, "capacity");
            requirePositive(refillTokens, "refillTokens");
            requirePositive(refillPeriod, "refillPeriod");
        }
    }

    record LeakyBucket(long capacity, long leakTokens, Duration leakPeriod) implements RateLimitConfig {
        public LeakyBucket {
            requirePositive(capacity, "capacity");
            requirePositive(leakTokens, "leakTokens");
            requirePositive(leakPeriod, "leakPeriod");
        }
    }
}
