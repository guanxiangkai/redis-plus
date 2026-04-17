package com.actomize.redis.plus.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RateLimitConfigTest {

    @Test
    void tokenBucket_requiresPositiveFields() {
        assertThrows(IllegalArgumentException.class,
                () -> new RateLimitConfig.TokenBucket(0, 10, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new RateLimitConfig.TokenBucket(10, 0, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new RateLimitConfig.TokenBucket(10, 10, Duration.ZERO));
    }

    @Test
    void leakyBucket_acceptsPositiveValues() {
        assertDoesNotThrow(() ->
                new RateLimitConfig.LeakyBucket(100, 25, Duration.ofSeconds(1)));
    }
}
