package com.actomize.redis.plus.cache.spi;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 基于 JVM 本地 {@link ReentrantLock} 的回源保护实现。
 *
 * <p>使用固定 256 个 lock 桶（Striped Lock）：根据 lockKey 哈希取模选桶，
 * 内存占用固定（256 × ReentrantLock），不随 Key 数量增长，消除内存泄漏风险。
 * 冲突概率 ≤ 1/256，对绝大多数场景足够。
 *
 * <p>适用于单节点部署场景；多节点部署请使用分布式锁实现。
 */
class LocalCacheLoadProtection implements CacheLoadProtection {

    private static final int STRIPE_COUNT = 256;
    private final ReentrantLock[] stripes;

    LocalCacheLoadProtection() {
        stripes = new ReentrantLock[STRIPE_COUNT];
        for (int i = 0; i < STRIPE_COUNT; i++) {
            stripes[i] = new ReentrantLock();
        }
    }

    @Override
    public LockHandle acquire(String lockKey, long waitTime, long leaseTime, TimeUnit unit) {
        ReentrantLock lock = stripe(lockKey);
        try {
            if (!lock.tryLock(waitTime, unit)) {
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("缓存回源等待被中断，lockKey=" + lockKey, e);
        }
        return new LocalLockHandle(lockKey, lock);
    }

    private ReentrantLock stripe(String lockKey) {
        return stripes[(lockKey.hashCode() & Integer.MAX_VALUE) % STRIPE_COUNT];
    }

    private static final class LocalLockHandle implements LockHandle {

        private final String key;
        private final ReentrantLock lock;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private LocalLockHandle(String key, ReentrantLock lock) {
            this.key = key;
            this.lock = lock;
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true) && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
