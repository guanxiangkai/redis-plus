package com.actomize.redis.plus.cache.protection;

import com.actomize.redis.plus.cache.spi.CacheLoadProtection;
import com.actomize.redis.plus.lock.DistributedLock;
import com.actomize.redis.plus.lock.DistributedReadWriteLock;
import com.actomize.redis.plus.lock.impl.RedisLockFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * 基于 {@link RedisLockFactory} 的分布式缓存回源保护实现。
 *
 * <p>当 classpath 中存在 {@code redis-plus-lock} 模块时，
 * 由 {@code RedisPlusCacheAutoConfiguration}
 * 自动注册此实现，替换默认的 JVM 本地锁保护。
 *
 * <p><b>租约到期处理</b>：回源操作耗时超过锁租约时，{@link LockHandle#close()} 会检测到锁已过期，
 * 降级为 warn 日志而非抛出异常——此时缓存通常已写入成功，属于可接受的预期场景。
 */
public class DistributedCacheLoadProtection implements CacheLoadProtection {

    private static final Logger log = LoggerFactory.getLogger(DistributedCacheLoadProtection.class);

    private final RedisLockFactory lockFactory;

    public DistributedCacheLoadProtection(RedisLockFactory lockFactory) {
        this.lockFactory = lockFactory;
    }

    @Override
    public LockHandle acquire(String lockKey, long waitTime, long leaseTime, TimeUnit unit) {
        DistributedReadWriteLock rwLock = lockFactory.getReadWriteLock(lockKey);
        DistributedLock writeLock = rwLock.writeLock();
        if (!writeLock.tryLock(waitTime, leaseTime, unit)) {
            return null;
        }
        return new DistributedLockHandle(lockKey, writeLock);
    }

    private static final class DistributedLockHandle implements LockHandle {

        private final String key;
        private final DistributedLock lock;
        private final java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean(false);

        private DistributedLockHandle(String key, DistributedLock lock) {
            this.key = key;
            this.lock = lock;
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                try {
                    lock.unlock();
                } catch (IllegalMonitorStateException e) {
                    // 回源耗时超过锁租约时，Redis key 已自然过期，unlock() 会检测到 field 不存在并抛此异常。
                    // 此时缓存通常已写入成功，属于预期降级场景，只记录告警不向上传播。
                    log.warn("[redis-plus] 缓存回源锁已过期，跳过解锁（回源耗时可能超过租约）：key={}", key);
                }
            }
        }
    }
}
