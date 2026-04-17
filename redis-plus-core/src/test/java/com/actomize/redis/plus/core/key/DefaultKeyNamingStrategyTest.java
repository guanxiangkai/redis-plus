package com.actomize.redis.plus.core.key;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link DefaultKeyNamingStrategy} 单元测试
 */
class DefaultKeyNamingStrategyTest {

    private final DefaultKeyNamingStrategy strategy = new DefaultKeyNamingStrategy();

    @Test
    void resolve_noParts() {
        assertEquals("lock", strategy.resolve("lock"));
    }

    @Test
    void resolve_nullParts() {
        assertEquals("lock", strategy.resolve("lock", (String[]) null));
    }

    @Test
    void resolve_singlePart() {
        assertEquals("lock:order", strategy.resolve("lock", "order"));
    }

    @Test
    void resolve_multipleParts() {
        assertEquals("cache:user:42", strategy.resolve("cache", "user", "42"));
    }

    @Test
    void resolve_skipsBlankParts() {
        assertEquals("lock:order", strategy.resolve("lock", "", "order", "  ", null));
    }
}

