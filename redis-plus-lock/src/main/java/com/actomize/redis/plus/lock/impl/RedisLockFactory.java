package com.actomize.redis.plus.lock.impl;

import com.actomize.redis.plus.core.key.DefaultKeyNamingStrategy;
import com.actomize.redis.plus.core.key.KeyNamespaceUtils;
import com.actomize.redis.plus.core.key.KeyNamingStrategy;
import com.actomize.redis.plus.core.script.RedisScriptExecutor;
import com.actomize.redis.plus.lock.DistributedLock;
import com.actomize.redis.plus.lock.DistributedReadWriteLock;
import com.actomize.redis.plus.lock.LockDefinition;
import com.actomize.redis.plus.lock.spi.LockLeaseStrategy;
import com.actomize.redis.plus.lock.spi.impl.DefaultLockLeaseStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁工厂
 *
 * <p>统一入口，根据锁名获取 {@link DistributedLock} 或 {@link DistributedReadWriteLock} 实例。
 *
 * <p>工厂持有共享的 WatchDog 线程池（daemon 线程，默认 2 个调度线程），
 * 并实现 {@link DisposableBean} 在 Spring 容器关闭时优雅关闭线程池，
 * 防止应用下线后后台续期线程继续运行造成资源泄漏。
 *
 * <p>注意：每次调用返回新实例，但 owner 由工厂按"JVM 实例 + 当前线程"稳定生成，
 * 因此同线程重复获取同名锁时仍保持一致的可重入语义。
 */
public class RedisLockFactory implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(RedisLockFactory.class);

    /** WatchDog 线程池（daemon 线程，随 Spring 容器关闭而关闭） */
    private final ScheduledExecutorService watchdogPool;

    private final StringRedisTemplate redisTemplate;
    private final RedisScriptExecutor scriptExecutor;
    private final String keyNamespace;
    private final LockOwnerProvider ownerProvider;
    private final KeyNamingStrategy keyNamingStrategy;
    private final LockLeaseStrategy leaseStrategy;
    private final RedisScriptExecutor fallbackScriptExecutor;

    public RedisLockFactory(StringRedisTemplate redisTemplate,
                            RedisScriptExecutor scriptExecutor,
                            String keyPrefix,
                            KeyNamingStrategy keyNamingStrategy,
                            LockLeaseStrategy leaseStrategy) {
        this(redisTemplate, scriptExecutor, keyPrefix, new LockOwnerProvider(), keyNamingStrategy, leaseStrategy);
    }

    public RedisLockFactory(StringRedisTemplate redisTemplate, String keyPrefix) {
        this(redisTemplate, null, keyPrefix, new LockOwnerProvider(),
                new DefaultKeyNamingStrategy(), new DefaultLockLeaseStrategy());
    }

    RedisLockFactory(StringRedisTemplate redisTemplate,
                     RedisScriptExecutor scriptExecutor,
                     String keyPrefix,
                     LockOwnerProvider ownerProvider,
                     KeyNamingStrategy keyNamingStrategy,
                     LockLeaseStrategy leaseStrategy) {
        this.redisTemplate = redisTemplate;
        this.scriptExecutor = scriptExecutor;
        this.keyNamespace = KeyNamespaceUtils.namespace(keyPrefix, "redis-plus:lock");
        this.ownerProvider = ownerProvider;
        this.keyNamingStrategy = keyNamingStrategy;
        this.leaseStrategy = leaseStrategy;
        this.watchdogPool  = Executors.newScheduledThreadPool(2, r -> {
            Thread t = Thread.ofPlatform()
                    .name("redis-plus-watchdog")
                    .daemon(true)
                    .unstarted(r);
            return t;
        });
        this.fallbackScriptExecutor = buildFallbackScriptExecutor();
    }

    /**
     * 获取互斥分布式锁。
     *
     * @param name 锁业务名称，最终 Key = {@code keyPrefix + name}
     */
    public DistributedLock getLock(String name) {
        return getLock(LockDefinition.of(name));
    }

    public DistributedLock getLock(LockDefinition definition) {
        return new RedisDistributedLock(redisTemplate, effectiveScriptExecutor(), watchdogPool,
                buildKey(definition.name()), ownerProvider.currentOwner(), leaseStrategy, definition);
    }

    /**
     * 获取分布式读写锁。
     *
     * @param name 锁业务名称
     */
    public DistributedReadWriteLock getReadWriteLock(String name) {
        return getReadWriteLock(LockDefinition.of(name));
    }

    public DistributedReadWriteLock getReadWriteLock(LockDefinition definition) {
        return new RedisReadWriteLock(redisTemplate, effectiveScriptExecutor(), watchdogPool,
                buildKey(definition.name()), ownerProvider, leaseStrategy, definition);
    }

    /**
     * Spring 容器关闭时优雅停止 WatchDog 线程池，最多等待 5 秒；超时则强制关闭。
     */
    @Override
    public void destroy() {
        watchdogPool.shutdown();
        try {
            if (!watchdogPool.awaitTermination(5, TimeUnit.SECONDS)) {
                watchdogPool.shutdownNow();
                log.warn("[redis-plus] WatchDog 线程池未在 5 秒内正常关闭，已强制中断");
            }
        } catch (InterruptedException e) {
            watchdogPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private String buildKey(String name) {
        return keyNamingStrategy.resolve(keyNamespace, name);
    }

    private RedisScriptExecutor effectiveScriptExecutor() {
        return scriptExecutor != null ? scriptExecutor : fallbackScriptExecutor;
    }

    @SuppressWarnings("unchecked")
    private RedisScriptExecutor buildFallbackScriptExecutor() {
        var scriptCache = new ConcurrentHashMap<String, org.springframework.data.redis.core.script.DefaultRedisScript<?>>();
        return new RedisScriptExecutor() {
            @Override
            public <T> T execute(String script, Class<T> resultType, java.util.List<String> keys, Object... args) {
                org.springframework.data.redis.core.script.DefaultRedisScript<T> compiled =
                        (org.springframework.data.redis.core.script.DefaultRedisScript<T>)
                        scriptCache.computeIfAbsent(script, s ->
                                new org.springframework.data.redis.core.script.DefaultRedisScript<>(s, resultType));
                return redisTemplate.execute(compiled, keys, args);
            }
        };
    }
}
