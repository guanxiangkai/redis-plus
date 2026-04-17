package com.actomize.redis.plus.cache.spi;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link CacheLoadProtection} 单元测试
 */
class CacheLoadProtectionTest {

    @Test
    void noProtection_alwaysReturnsTrue() {
        CacheLoadProtection protection = CacheLoadProtection.noProtection();
        CacheLoadProtection.LockHandle handle = protection.acquire("key", 0, 0, TimeUnit.SECONDS);
        assertNotNull(handle);
        handle.close();
    }

    @Test
    void local_providesMutualExclusion() throws InterruptedException {
        CacheLoadProtection protection = CacheLoadProtection.local();
        AtomicInteger concurrent = new AtomicInteger(0);
        AtomicInteger maxConcurrent = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(10);

        for (int i = 0; i < 10; i++) {
            new Thread(() -> {
                try {
                    CacheLoadProtection.LockHandle handle = protection.acquire("test-key", 5, 30, TimeUnit.SECONDS);
                    if (handle != null) {
                        try (handle) {
                            int c = concurrent.incrementAndGet();
                            maxConcurrent.updateAndGet(max -> Math.max(max, c));
                            Thread.sleep(10);
                            concurrent.decrementAndGet();
                        }
                    }
                } catch (InterruptedException | IllegalStateException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await(10, TimeUnit.SECONDS);
        assertEquals(1, maxConcurrent.get(), "Only one thread should hold the lock at a time");
    }

    @Test
    void local_differentKeysIndependent() {
        CacheLoadProtection protection = CacheLoadProtection.local();

        CacheLoadProtection.LockHandle handleA = protection.acquire("key-a", 0, 30, TimeUnit.SECONDS);
        CacheLoadProtection.LockHandle handleB = protection.acquire("key-b", 0, 30, TimeUnit.SECONDS);
        assertNotNull(handleA);
        assertNotNull(handleB);

        handleA.close();
        handleB.close();
    }
}
