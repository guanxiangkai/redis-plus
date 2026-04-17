package com.actomize.redis.plus.cache.spi;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CachePenetrationGuard} 单元测试
 */
class CachePenetrationGuardTest {

    @Test
    void nullValue_allowsAll() {
        CachePenetrationGuard guard = CachePenetrationGuard.nullValue();
        assertTrue(guard.isAllowed("any-key"));
        assertTrue(guard.isAllowed(""));
    }

    @Test
    void allowAll_allowsAll() {
        CachePenetrationGuard guard = CachePenetrationGuard.allowAll();
        assertTrue(guard.isAllowed("any-key"));
    }

    @Test
    void blacklist_rejectsBlacklistedKeys() {
        Set<String> blacklist = Set.of("bad-key-1", "bad-key-2");
        CachePenetrationGuard guard = CachePenetrationGuard.blacklist(blacklist);

        assertFalse(guard.isAllowed("bad-key-1"));
        assertFalse(guard.isAllowed("bad-key-2"));
        assertTrue(guard.isAllowed("good-key"));
    }
}

