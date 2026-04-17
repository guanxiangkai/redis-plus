package com.actomize.redis.plus.cache.spi;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 缓存回源保护 SPI
 *
 * <p>在 L2 未命中需要回源 L3 时，通过此接口提供互斥保护，防止缓存击穿。
 * 默认实现使用 JVM 级别的 ReentrantLock（适用于单机部署），
 * 引入 {@code redis-plus-lock} 模块后可自动切换为分布式锁实现。
 *
 * @see CacheLoadProtection#noProtection()
 */
public interface CacheLoadProtection {

    /**
     * 无保护策略（始终返回 true，不加锁）。
     * <p><b>仅建议在开发/测试环境使用。</b>
     */
    static CacheLoadProtection noProtection() {
        return new CacheLoadProtection() {
            @Override
            public LockHandle acquire(String lockKey, long waitTime, long leaseTime, TimeUnit unit) {
                return LockHandle.noop(lockKey);
            }
        };
    }

    /**
     * 基于 JVM ReentrantLock 的本地保护（适用于单节点部署）。
     */
    static CacheLoadProtection local() {
        return new LocalCacheLoadProtection();
    }

    /**
     * 尝试获取回源保护锁。
     *
     * @param lockKey   锁 Key
     * @param waitTime  最大等待时间
     * @param leaseTime 锁持有时间
     * @param unit      时间单位
     * @return 保护锁句柄，获取失败时返回 {@code null}
     */
    LockHandle acquire(String lockKey, long waitTime, long leaseTime, TimeUnit unit);

    /**
     * 回源保护锁句柄。
     */
    interface LockHandle extends AutoCloseable {

        static LockHandle noop(String key) {
            AtomicBoolean closed = new AtomicBoolean(false);
            return new LockHandle() {
                @Override
                public String key() {
                    return key;
                }

                @Override
                public void close() {
                    closed.compareAndSet(false, true);
                }

                @Override
                public String toString() {
                    return "LockHandle[" + key + ", closed=" + closed.get() + "]";
                }
            };
        }

        String key();

        @Override
        void close();
    }
}
