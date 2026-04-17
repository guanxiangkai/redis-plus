package com.actomize.redis.plus.lock.spi;

import com.actomize.redis.plus.core.exception.RedisLockException;
import com.actomize.redis.plus.lock.LockDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link LockFailureHandler} 单元测试
 */
class LockFailureHandlerTest {

    @Test
    void throwException_throwsRedisLockException() {
        LockFailureHandler handler = LockFailureHandler.throwException();
        LockDefinition def = LockDefinition.of("test-lock");

        RedisLockException ex = assertThrows(RedisLockException.class, () -> handler.handle(def));
        assertTrue(ex.getMessage().contains("test-lock"));
    }

    @Test
    void returnFallback_returnsFallbackValue() {
        LockFailureHandler handler = LockFailureHandler.returnFallback("fallback");
        LockDefinition def = LockDefinition.of("test-lock");

        Object result = handler.handle(def);
        assertEquals("fallback", result);
    }

    @Test
    void returnFallback_nullFallback() {
        LockFailureHandler handler = LockFailureHandler.returnFallback(null);
        LockDefinition def = LockDefinition.of("test-lock");

        assertNull(handler.handle(def));
    }
}

