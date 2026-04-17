package com.actomize.redis.plus.lock;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link LockDefinition} 单元测试
 */
class LockDefinitionTest {

    @Test
    void of_name_usesDefaults() {
        LockDefinition def = LockDefinition.of("order:pay");
        assertEquals("order:pay", def.name());
        assertEquals(Duration.ofSeconds(30), def.leaseTime());
        assertEquals(Duration.ofSeconds(5), def.waitTime());
        assertTrue(def.reentrant());
    }

    @Test
    void of_withTimes() {
        LockDefinition def = LockDefinition.of("key", Duration.ofSeconds(10), Duration.ofSeconds(3));
        assertEquals(Duration.ofSeconds(10), def.leaseTime());
        assertEquals(Duration.ofSeconds(3), def.waitTime());
    }

    @Test
    void isWatchDogEnabled_negativeLease() {
        LockDefinition def = new LockDefinition("key", Duration.ofSeconds(-1), Duration.ofSeconds(5), true);
        assertTrue(def.isWatchDogEnabled());
    }

    @Test
    void isWatchDogEnabled_zeroLease() {
        LockDefinition def = new LockDefinition("key", Duration.ZERO, Duration.ofSeconds(5), true);
        assertTrue(def.isWatchDogEnabled());
    }

    @Test
    void isWatchDogEnabled_positiveLease() {
        LockDefinition def = LockDefinition.of("key");
        assertFalse(def.isWatchDogEnabled());
    }

    @Test
    void isWatchDogEnabled_nullLease() {
        LockDefinition def = new LockDefinition("key", null, Duration.ofSeconds(5), true);
        assertTrue(def.isWatchDogEnabled());
    }
}

