package com.actomize.redis.plus.lock.impl;

import com.actomize.redis.plus.core.exception.RedisLockException;
import com.actomize.redis.plus.core.script.RedisScriptExecutor;
import com.actomize.redis.plus.lock.DistributedLock;
import com.actomize.redis.plus.lock.DistributedReadWriteLock;
import com.actomize.redis.plus.lock.LockDefinition;
import com.actomize.redis.plus.lock.spi.LockLeaseStrategy;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于 Redis Lua 脚本的分布式读写锁实现
 *
 * <p>策略：
 * <ul>
 *   <li>写锁：排他锁，无读/写锁时才能获取</li>
 *   <li>读锁：共享锁，无写锁时可多个线程并发持有</li>
 * </ul>
 *
 * <p>Key 规划：
 * <ul>
 *   <li>{@code {rwlockName}:write} — 写锁 Hash（field=owner, value=count）</li>
 *   <li>{@code {rwlockName}:read}  — 读锁 Hash（field=owner, value=count）</li>
 * </ul>
 */
public class RedisReadWriteLock implements DistributedReadWriteLock {

    // ── 写锁 Lua 脚本 ─────────────────────────────────────────────────

    private static final String WRITE_ACQUIRE = """
            -- 先清扫已过期的读者 field（{count}:{expiryEpochMs}），再检查是否有活跃读锁
            if redis.call('exists', KEYS[2]) == 1 then
                local t = redis.call('time')
                local nowMs = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
                local rfields = redis.call('hgetall', KEYS[2])
                for i = 1, #rfields, 2 do
                    local v = rfields[i+1]
                    local pos = string.find(v, ':', 1, true)
                    if pos then
                        local exp = tonumber(string.sub(v, pos + 1))
                        if exp and exp <= nowMs then
                            redis.call('hdel', KEYS[2], rfields[i])
                        end
                    end
                end
                if redis.call('hlen', KEYS[2]) == 0 then
                    redis.call('del', KEYS[2])
                end
            end
            -- 存在活跃读锁或写锁（非本线程）则返回 pttl
            if redis.call('exists', KEYS[2]) == 1 then
                return redis.call('pttl', KEYS[2])
            end
            if redis.call('exists', KEYS[1]) == 1 then
                if redis.call('hexists', KEYS[1], ARGV[1]) == 0 then
                    return redis.call('pttl', KEYS[1])
                end
            end
            if redis.call('hexists', KEYS[1], ARGV[1]) == 1 then
                redis.call('hincrby', KEYS[1], ARGV[1], 1)
            else
                redis.call('hset', KEYS[1], ARGV[1], 1)
            end
            redis.call('pexpire', KEYS[1], ARGV[2])
            return nil
            """;

    private static final String WRITE_RELEASE = """
            if redis.call('hexists', KEYS[1], ARGV[1]) == 0 then
                return nil
            end
            local count = redis.call('hincrby', KEYS[1], ARGV[1], -1)
            if count <= 0 then
                redis.call('del', KEYS[1])
                return 1
            end
            redis.call('pexpire', KEYS[1], ARGV[2])
            return 0
            """;

    // ── 读锁 Lua 脚本 ─────────────────────────────────────────────────
    //
    // 读锁 hash 的 field value 格式：{reentrantCount}:{expiryEpochMs}
    // 通过记录每个 owner 的到期时间戳，WRITE_ACQUIRE 和 READ_ACQUIRE 在执行前
    // 主动清扫已过期的 field，彻底防止 crashed reader 的 ghost field 因新 reader
    // 不断刷新 hash TTL 而永久存活，导致写锁长期饥饿。

    private static final String READ_ACQUIRE = """
            -- 存在写锁时，仅允许持有同一写锁的 owner 降级获取读锁
            if redis.call('exists', KEYS[2]) == 1 then
                if redis.call('hexists', KEYS[2], ARGV[1]) == 0 then
                    return redis.call('pttl', KEYS[2])
                end
            end
            -- 清扫已过期的读者 field，防止 ghost field 阻塞写锁
            local t = redis.call('time')
            local nowMs = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
            local leaseMs = tonumber(ARGV[2])
            local fields = redis.call('hgetall', KEYS[1])
            for i = 1, #fields, 2 do
                local v = fields[i+1]
                local pos = string.find(v, ':', 1, true)
                if pos then
                    local exp = tonumber(string.sub(v, pos + 1))
                    if exp and exp <= nowMs then
                        redis.call('hdel', KEYS[1], fields[i])
                    end
                end
            end
            -- 累加重入计数，刷新到期时间（格式：{count}:{expiryEpochMs}）
            local existing = redis.call('hget', KEYS[1], ARGV[1])
            local count = 1
            if existing then
                local pos = string.find(existing, ':', 1, true)
                if pos then
                    count = tonumber(string.sub(existing, 1, pos - 1)) + 1
                end
            end
            redis.call('hset', KEYS[1], ARGV[1], count .. ':' .. (nowMs + leaseMs))
            redis.call('pexpire', KEYS[1], leaseMs)
            return nil
            """;

    private static final String READ_RELEASE = """
            local existing = redis.call('hget', KEYS[1], ARGV[1])
            if not existing then
                return nil
            end
            local pos = string.find(existing, ':', 1, true)
            local count = 1
            local expSuffix = ''
            if pos then
                count = tonumber(string.sub(existing, 1, pos - 1))
                expSuffix = string.sub(existing, pos)
            end
            count = count - 1
            if count <= 0 then
                redis.call('hdel', KEYS[1], ARGV[1])
                if redis.call('hlen', KEYS[1]) == 0 then
                    redis.call('del', KEYS[1])
                end
                return 1
            end
            redis.call('hset', KEYS[1], ARGV[1], count .. expSuffix)
            return 0
            """;

    private static final String WRITE_RENEW = """
            if redis.call('hexists', KEYS[1], ARGV[1]) == 1 then
                redis.call('pexpire', KEYS[1], ARGV[2])
                return 1
            end
            return 0
            """;

    private static final String READ_RENEW = """
            local existing = redis.call('hget', KEYS[1], ARGV[1])
            if not existing then
                return 0
            end
            local t = redis.call('time')
            local nowMs = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
            local leaseMs = tonumber(ARGV[2])
            local pos = string.find(existing, ':', 1, true)
            local count = '1'
            if pos then
                count = string.sub(existing, 1, pos - 1)
            end
            redis.call('hset', KEYS[1], ARGV[1], count .. ':' .. (nowMs + leaseMs))
            redis.call('pexpire', KEYS[1], leaseMs)
            return 1
            """;

    private final StringRedisTemplate redisTemplate;
    private final RedisScriptExecutor scriptExecutor;
    private final ScheduledExecutorService watchdogPool;
    private final String baseName;
    private final String writeKey;
    private final String readKey;
    private final LockOwnerProvider ownerProvider;
    private final LockLeaseStrategy leaseStrategy;
    private final LockDefinition definition;

    public RedisReadWriteLock(StringRedisTemplate redisTemplate,
                              RedisScriptExecutor scriptExecutor,
                              String baseName) {
        this(redisTemplate, scriptExecutor, null, baseName, new LockOwnerProvider(), null, LockDefinition.of(baseName));
    }

    RedisReadWriteLock(StringRedisTemplate redisTemplate,
                       RedisScriptExecutor scriptExecutor,
                       ScheduledExecutorService watchdogPool,
                       String baseName,
                       LockOwnerProvider ownerProvider,
                       LockLeaseStrategy leaseStrategy,
                       LockDefinition definition) {
        this.redisTemplate = redisTemplate;
        this.scriptExecutor = scriptExecutor;
        this.watchdogPool = watchdogPool;
        this.baseName = baseName;
        this.ownerProvider = ownerProvider;
        this.leaseStrategy = leaseStrategy;
        this.definition = definition;
        // 使用 hash tag 保证 cluster 模式同槽
        this.writeKey = "{" + baseName + "}:write";
        this.readKey = "{" + baseName + "}:read";
    }

    @Override
    public DistributedLock readLock() {
        return new RWInnerLock(false, ownerProvider.currentOwner());
    }

    @Override
    public DistributedLock writeLock() {
        return new RWInnerLock(true, ownerProvider.currentOwner());
    }

    // ── 内部读/写锁实现 ───────────────────────────────────────────────

    private class RWInnerLock implements DistributedLock {

        private static final long DEFAULT_LEASE_MS = 30_000L;
        private final boolean isWrite;
        private final String owner;
        private final AtomicReference<ScheduledFuture<?>> watchdogFuture = new AtomicReference<>();

        RWInnerLock(boolean isWrite, String owner) {
            this.isWrite = isWrite;
            this.owner = owner;
        }

        @Override
        public boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) {
            long leaseMs = resolveLeaseMillis(leaseTime, unit);
            long deadline = System.currentTimeMillis() + unit.toMillis(waitTime);
            while (true) {
                Long ttl = execAcquire(leaseMs);
                if (ttl == null) {
                    if (shouldUseWatchdog(leaseTime)) {
                        startWatchdog(leaseMs);
                    }
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
                if (ttl == null) {
                    if (shouldUseWatchdog(leaseTime)) {
                        startWatchdog(leaseMs);
                    }
                    return;
                }
                sleepForRetry(Math.max(1L, Math.min(ttl, 200)));
            }
        }

        @Override
        public void unlock() {
            Long released = execRelease(resolveDefinitionLeaseMillis());
            if (released == null) {
                throw new IllegalMonitorStateException("当前线程未持有锁，lockKey=" + baseName);
            }
            if (released == 1L) {
                cancelWatchdog();
            }
        }

        @Override
        public boolean isHeldByCurrentThread() {
            String key = isWrite ? writeKey : readKey;
            return Boolean.TRUE.equals(redisTemplate.opsForHash().hasKey(key, owner));
        }

        @Override
        public boolean isLocked() {
            String key = isWrite ? writeKey : readKey;
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        }

        @Override
        public long remainingLeaseTime() {
            String key = isWrite ? writeKey : readKey;
            Long pttl = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
            return pttl != null ? pttl : -1L;
        }

        private Long execAcquire(long leaseMs) {
            List<String> keys = isWrite
                    ? List.of(writeKey, readKey)
                    : List.of(readKey, writeKey);
            String script = isWrite ? WRITE_ACQUIRE : READ_ACQUIRE;
            return scriptExecutor.execute(script, Long.class, keys, owner, String.valueOf(leaseMs));
        }

        private Long execRelease(long leaseMs) {
            List<String> keys = isWrite
                    ? List.of(writeKey, readKey)
                    : List.of(readKey, writeKey);
            String script = isWrite ? WRITE_RELEASE : READ_RELEASE;
            return scriptExecutor.execute(script, Long.class, keys, owner, String.valueOf(leaseMs));
        }

        private void startWatchdog(long leaseMs) {
            if (watchdogPool == null) {
                return;
            }
            cancelWatchdog();
            long intervalMs = Math.max(1L, renewIntervalMillis(leaseMs));
            ScheduledFuture<?> future = watchdogPool.scheduleAtFixedRate(() -> {
                try {
                    Long renewed = scriptExecutor.execute(isWrite ? WRITE_RENEW : READ_RENEW,
                            Long.class,
                            List.of(isWrite ? writeKey : readKey),
                            owner,
                            String.valueOf(leaseMs));
                    if (renewed == null || renewed == 0L) {
                        cancelWatchdog();
                    }
                } catch (Exception ex) {
                    cancelWatchdog();
                }
            }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
            watchdogFuture.set(future);
        }

        private void cancelWatchdog() {
            ScheduledFuture<?> future = watchdogFuture.getAndSet(null);
            if (future != null && !future.isCancelled()) {
                future.cancel(false);
            }
        }

        private void sleepForRetry(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RedisLockException("加锁等待被中断，lockKey=" + baseName, e);
            }
        }

        private long resolveLeaseMillis(long leaseTime, TimeUnit unit) {
            if (leaseTime >= 0) {
                return Math.max(1L, unit.toMillis(leaseTime));
            }
            long configured = resolveDefinitionLeaseMillis();
            return configured > 0 ? configured : DEFAULT_LEASE_MS;
        }

        private long resolveDefinitionLeaseMillis() {
            if (leaseStrategy == null) {
                return DEFAULT_LEASE_MS;
            }
            long leaseMs = leaseStrategy.leaseTimeMillis(definition);
            if (leaseMs < 0) {
                leaseMs = leaseStrategy.defaultLeaseMillis();
            }
            return leaseMs > 0 ? leaseMs : DEFAULT_LEASE_MS;
        }

        private boolean shouldUseWatchdog(long leaseTime) {
            return leaseTime < 0 && watchdogPool != null;
        }

        private long renewIntervalMillis(long leaseMs) {
            if (leaseStrategy == null) {
                return leaseMs / 3;
            }
            return leaseStrategy.renewIntervalMillis(leaseMs);
        }
    }
}
