package com.actomize.redis.plus.lock.impl;

import com.actomize.redis.plus.core.exception.RedisLockException;
import com.actomize.redis.plus.core.script.RedisScriptExecutor;
import com.actomize.redis.plus.lock.DistributedLock;
import com.actomize.redis.plus.lock.LockDefinition;
import com.actomize.redis.plus.lock.spi.LockLeaseStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于 Redis Lua 脚本的可重入分布式锁实现
 *
 * <p>核心特性：
 * <ul>
 *   <li>使用 Hash 结构存储锁持有者标识与重入计数</li>
 *   <li>支持 WatchDog 自动续期（leaseTime &lt; 0 时启用）</li>
 *   <li>获取/释放/续期均通过 Lua 脚本保证原子性</li>
 * </ul>
 *
 * <p>Field 格式：{@code instanceId:threadId}（稳定标识同一 JVM 内的同一线程）
 *
 * <p><b>线程安全说明</b>：每个 {@code RedisDistributedLock} 实例与一把锁 Key 绑定，
 * 设计上不跨线程共享（锁实例应在同一线程内获取并释放）。
 * WatchDog Future 使用 {@link AtomicReference} 保证 cancel/reschedule 的原子性。
 */
public class RedisDistributedLock implements DistributedLock {

    private static final Logger log = LoggerFactory.getLogger(RedisDistributedLock.class);

    /**
     * 默认租约时长（毫秒）
     */
    private static final long DEFAULT_LEASE_MS = 30_000L;

    // ── Lua 脚本 ────────────────────────────────────────────────────

    /**
     * 获取锁：Hash + PEXPIRE；已被当前线程持有则重入计数 +1
     */
    private static final String ACQUIRE_SCRIPT = """
            if redis.call('exists', KEYS[1]) == 0 then
                redis.call('hset', KEYS[1], ARGV[1], 1)
                redis.call('pexpire', KEYS[1], ARGV[2])
                return nil
            end
            if redis.call('hexists', KEYS[1], ARGV[1]) == 1 then
                redis.call('hincrby', KEYS[1], ARGV[1], 1)
                redis.call('pexpire', KEYS[1], ARGV[2])
                return nil
            end
            return redis.call('pttl', KEYS[1])
            """;

    /**
     * 释放锁：重入计数 -1；减到 0 则删除 Key
     */
    private static final String RELEASE_SCRIPT = """
            if redis.call('hexists', KEYS[1], ARGV[1]) == 0 then
                return nil
            end
            local count = redis.call('hincrby', KEYS[1], ARGV[1], -1)
            if count > 0 then
                redis.call('pexpire', KEYS[1], ARGV[2])
                return 0
            end
            redis.call('del', KEYS[1])
            return 1
            """;

    /**
     * 续期：仅当 field 存在时重置 TTL
     */
    private static final String RENEW_SCRIPT = """
            if redis.call('hexists', KEYS[1], ARGV[1]) == 1 then
                redis.call('pexpire', KEYS[1], ARGV[2])
                return 1
            end
            return 0
            """;

    private final StringRedisTemplate redisTemplate;
    private final RedisScriptExecutor scriptExecutor;
    private final ScheduledExecutorService watchdogPool;
    private final String lockKey;
    private final String lockOwner;   // instanceId:threadId
    private final LockLeaseStrategy leaseStrategy;
    private final LockDefinition definition;

    /** WatchDog 任务句柄，使用 AtomicReference 保证 cancel/reschedule 的原子性 */
    private final AtomicReference<ScheduledFuture<?>> watchdogFuture = new AtomicReference<>();

    public RedisDistributedLock(StringRedisTemplate redisTemplate,
                                RedisScriptExecutor scriptExecutor,
                                ScheduledExecutorService watchdogPool,
                                String lockKey,
                                String lockOwner,
                                LockLeaseStrategy leaseStrategy,
                                LockDefinition definition) {
        this.redisTemplate = redisTemplate;
        this.scriptExecutor = scriptExecutor;
        this.watchdogPool  = watchdogPool;
        this.lockKey       = lockKey;
        this.lockOwner     = lockOwner;
        this.leaseStrategy = leaseStrategy;
        this.definition = definition;
    }

    @Override
    public boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) {
        long leaseMs = resolveLeaseMillis(leaseTime, unit);
        long waitMs  = unit.toMillis(waitTime);
        long deadline = System.currentTimeMillis() + waitMs;

        while (true) {
            Long ttl = execAcquire(leaseMs);
            if (ttl == null) {
                if (shouldUseWatchdog(leaseTime)) startWatchdog(leaseMs);
                return true;
            }
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) return false;
            sleepForRetry(Math.max(1L, Math.min(ttl, Math.min(remaining, 200))));
        }
    }

    @Override
    public boolean tryLock() {
        long leaseMs = resolveLeaseMillis(-1L, TimeUnit.MILLISECONDS);
        Long ttl = execAcquire(leaseMs);
        if (ttl == null) {
            startWatchdog(leaseMs);
            return true;
        }
        return false;
    }

    @Override
    public void lock() {
        while (true) {
            long leaseMs = resolveLeaseMillis(-1L, TimeUnit.MILLISECONDS);
            Long ttl = execAcquire(leaseMs);
            if (ttl == null) {
                startWatchdog(leaseMs);
                return;
            }
            sleepForRetry(Math.max(1L, Math.min(ttl, 200)));
        }
    }

    @Override
    public void lock(long leaseTime, TimeUnit unit) {
        long leaseMs = resolveLeaseMillis(leaseTime, unit);
        while (true) {
            Long ttl = execAcquire(leaseMs);
            if (ttl == null) return;
            sleepForRetry(Math.max(1L, Math.min(ttl, 200)));
        }
    }

    @Override
    public void unlock() {
        List<String> keys = Collections.singletonList(lockKey);
        long leaseMs = Math.max(resolveDefinitionLeaseMillis(), DEFAULT_LEASE_MS);
        Long result = scriptExecutor.execute(RELEASE_SCRIPT, Long.class, keys, lockOwner, String.valueOf(leaseMs));
        if (result == null) {
            throw new IllegalMonitorStateException("当前线程未持有锁，lockKey=" + lockKey);
        }
        if (result == 1L) {
            cancelWatchdog();
        }
    }

    @Override
    public boolean isHeldByCurrentThread() {
        return Boolean.TRUE.equals(redisTemplate.opsForHash().hasKey(lockKey, lockOwner));
    }

    @Override
    public boolean isLocked() {
        return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey));
    }

    @Override
    public long remainingLeaseTime() {
        Long pttl = redisTemplate.getExpire(lockKey, TimeUnit.MILLISECONDS);
        return pttl != null ? pttl : -1L;
    }

    // ── 内部工具 ─────────────────────────────────────────────────────

    private Long execAcquire(long leaseMs) {
        return scriptExecutor.execute(ACQUIRE_SCRIPT, Long.class,
                Collections.singletonList(lockKey), lockOwner, String.valueOf(leaseMs));
    }

    private void startWatchdog(long leaseMs) {
        cancelWatchdog();
        long intervalMs = Math.max(1L, leaseStrategy.renewIntervalMillis(leaseMs));
        ScheduledFuture<?> future = watchdogPool.scheduleAtFixedRate(() -> {
            try {
                Long renewed = scriptExecutor.execute(RENEW_SCRIPT, Long.class,
                        Collections.singletonList(lockKey), lockOwner, String.valueOf(leaseMs));
                if (renewed == null || renewed == 0L) {
                    log.warn("[redis-plus] WatchDog 续期失败，锁可能已过期：lockKey={}", lockKey);
                    cancelWatchdog();
                }
            } catch (Exception ex) {
                log.error("[redis-plus] WatchDog 续期异常：lockKey={}", lockKey, ex);
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        watchdogFuture.set(future);
    }

    private void cancelWatchdog() {
        ScheduledFuture<?> f = watchdogFuture.getAndSet(null);
        if (f != null && !f.isCancelled()) {
            f.cancel(false);
        }
    }

    private void sleepForRetry(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RedisLockException("加锁等待被中断，lockKey=" + lockKey, e);
        }
    }

    private boolean shouldUseWatchdog(long leaseTime) {
        return leaseTime < 0;
    }

    private long resolveLeaseMillis(long leaseTime, TimeUnit unit) {
        if (leaseTime >= 0) {
            return Math.max(1L, unit.toMillis(leaseTime));
        }
        long configured = resolveDefinitionLeaseMillis();
        return configured > 0 ? configured : DEFAULT_LEASE_MS;
    }

    private long resolveDefinitionLeaseMillis() {
        long leaseMs = leaseStrategy.leaseTimeMillis(definition);
        if (leaseMs < 0) {
            leaseMs = leaseStrategy.defaultLeaseMillis();
        }
        return leaseMs > 0 ? leaseMs : DEFAULT_LEASE_MS;
    }
}
