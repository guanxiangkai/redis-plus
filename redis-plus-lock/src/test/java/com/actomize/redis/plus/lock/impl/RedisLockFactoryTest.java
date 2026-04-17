package com.actomize.redis.plus.lock.impl;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;

class RedisLockFactoryTest {

    private static String readField(Object target, String fieldName) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (String) field.get(target);
    }

    @Test
    void getLock_reusesStableOwnerForSameThread() throws Exception {
        RedisLockFactory factory = new RedisLockFactory(mock(StringRedisTemplate.class), "test:");

        RedisDistributedLock first = (RedisDistributedLock) factory.getLock("order:1");
        RedisDistributedLock second = (RedisDistributedLock) factory.getLock("order:1");

        assertEquals(readField(first, "lockOwner"), readField(second, "lockOwner"));
    }

    @Test
    void getLock_usesDifferentOwnerAcrossThreads() throws Exception {
        RedisLockFactory factory = new RedisLockFactory(mock(StringRedisTemplate.class), "test:");
        RedisDistributedLock currentThreadLock = (RedisDistributedLock) factory.getLock("order:1");
        AtomicReference<String> otherThreadOwner = new AtomicReference<>();

        Thread thread = new Thread(() -> {
            try {
                RedisDistributedLock otherThreadLock = (RedisDistributedLock) factory.getLock("order:1");
                otherThreadOwner.set(readField(otherThreadLock, "lockOwner"));
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        });
        thread.start();
        thread.join();

        assertNotEquals(readField(currentThreadLock, "lockOwner"), otherThreadOwner.get());
    }
}
