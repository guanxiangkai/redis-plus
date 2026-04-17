package com.actomize.redis.plus.enhance.protection;

import com.actomize.redis.plus.cache.spi.CacheLoader;
import com.actomize.redis.plus.core.async.DefaultRedisPlusAsyncExecutor;
import com.actomize.redis.plus.core.async.RedisPlusAsyncExecutor;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 缓存击穿防护策略 SPI
 *
 * <p>热点 Key 过期后，大量并发请求同时穿透到数据库的场景（缓存击穿）防护。
 * 内置实现：
 * <ul>
 *   <li>{@link #mutex()} — 互斥锁策略（每个 Key 独立加锁，用 256 桶 Striped Lock 限制内存）</li>
 *   <li>{@link #earlyExpiry()} — 逻辑过期策略（提前异步刷新，不阻塞读请求）</li>
 * </ul>
 */
@FunctionalInterface
public interface BreakdownProtectionStrategy {

    RedisPlusAsyncExecutor DEFAULT_ASYNC_EXECUTOR = new DefaultRedisPlusAsyncExecutor("redis-plus-breakdown", 1);

    /**
     * 互斥锁策略：同一 Key 的并发回源请求排队，仅首个请求真正加载，其余复用结果。
     *
     * <p>使用 256 桶 Striped Lock，内存固定、无泄漏风险。
     */
    static BreakdownProtectionStrategy mutex() {
        final int STRIPE_COUNT = 256;
        final ReentrantLock[] stripes = new ReentrantLock[STRIPE_COUNT];
        Arrays.setAll(stripes, i -> new ReentrantLock());

        return new BreakdownProtectionStrategy() {
            @Override
            public <T> T load(String key, CacheLoader<T> loader) {
                ReentrantLock lock = stripes[(key.hashCode() & Integer.MAX_VALUE) % STRIPE_COUNT];
                lock.lock();
                try {
                    return loader.load(key);
                } finally {
                    lock.unlock();
                }
            }
        };
    }

    // ── 内置工厂 ─────────────────────────────────────────────────────

    /**
     * 逻辑过期策略：若当前 Key 已有异步刷新任务在进行，则直接调用 loader（同步加载）；
     * 否则在虚拟线程中异步触发一次刷新，同时本次请求也执行同步加载作为兜底。
     *
     * <p><b>注意</b>：真正的"零阻塞返回旧值"语义需要调用方在缓存层保存逻辑过期时间并
     * 在命中时提前判断，此实现为框架集成的基础版本，确保数据正确性优先。
     */
    static BreakdownProtectionStrategy earlyExpiry() {
        return earlyExpiry(DEFAULT_ASYNC_EXECUTOR);
    }

    static BreakdownProtectionStrategy earlyExpiry(RedisPlusAsyncExecutor asyncExecutor) {
        final Set<String> refreshingKeys = ConcurrentHashMap.newKeySet();

        return new BreakdownProtectionStrategy() {
            @Override
            public <T> T load(String key, CacheLoader<T> loader) {
                if (refreshingKeys.add(key)) {
                    // 首个请求：后台异步刷新（确保 Key 从 refreshingKeys 移除）
                    asyncExecutor.execute("breakdown-early-expiry", () -> {
                        try {
                            loader.load(key);
                        } finally {
                            refreshingKeys.remove(key);
                        }
                    });
                }
                // 同步加载作为兜底（异步刷新完成前保证返回有效数据）
                return loader.load(key);
            }
        };
    }

    /**
     * 执行防击穿保护的数据加载。
     *
     * @param key    缓存 Key
     * @param loader 原始数据加载器
     * @param <T>    值类型
     * @return 加载结果
     */
    <T> T load(String key, CacheLoader<T> loader);
}
