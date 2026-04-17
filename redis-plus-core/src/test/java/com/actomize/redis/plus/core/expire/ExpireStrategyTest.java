package com.actomize.redis.plus.core.expire;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ExpireStrategy} 单元测试
 */
class ExpireStrategyTest {

    @Test
    void fixed_returnsSameTtl() {
        ExpireStrategy strategy = ExpireStrategy.fixed();
        Duration base = Duration.ofMinutes(5);
        assertEquals(base, strategy.computeTtl(base));
    }

    @Test
    void randomJitter_returnsGreaterOrEqual() {
        ExpireStrategy strategy = ExpireStrategy.randomJitter(0.2);
        Duration base = Duration.ofMinutes(5);
        for (int i = 0; i < 100; i++) {
            Duration actual = strategy.computeTtl(base);
            assertTrue(actual.compareTo(base) >= 0, "actual should >= base");
            assertTrue(actual.compareTo(base.plusMillis((long) (base.toMillis() * 0.2))) <= 0,
                    "actual should <= base + 20%");
        }
    }

    @Test
    void randomJitter_invalidRatio_throws() {
        assertThrows(IllegalArgumentException.class, () -> ExpireStrategy.randomJitter(0));
        assertThrows(IllegalArgumentException.class, () -> ExpireStrategy.randomJitter(-0.1));
        assertThrows(IllegalArgumentException.class, () -> ExpireStrategy.randomJitter(1.1));
    }

    @Test
    void fixedOffset_addsOffset() {
        Duration offset = Duration.ofSeconds(10);
        ExpireStrategy strategy = ExpireStrategy.fixedOffset(offset);
        Duration base = Duration.ofMinutes(1);
        assertEquals(Duration.ofSeconds(70), strategy.computeTtl(base));
    }
}

