package com.actomize.redis.plus.ratelimit.impl;

import com.actomize.redis.plus.core.key.KeyNamespaceUtils;
import com.actomize.redis.plus.core.key.KeyNamingStrategy;
import com.actomize.redis.plus.core.script.RedisScriptExecutor;
import com.actomize.redis.plus.ratelimit.RateLimitConfig;
import com.actomize.redis.plus.ratelimit.RateLimiter;

import java.util.Collections;

/**
 * 基于 Redis Hash 的漏桶限流器
 *
 * <p>漏桶以固定速率"漏水"（处理请求），超出桶容量直接拒绝，不等待。
 * 与令牌桶的区别：令牌桶允许短时突发流量，漏桶严格以恒定速率输出。
 *
 * <p>存储结构（Redis Hash）：
 * <ul>
 *   <li>{@code last_time}   — 上次更新时间戳（ms）</li>
 *   <li>{@code water_level} — 当前桶内积水量（待处理请求数）</li>
 * </ul>
 *
 * <p>算法：
 * <pre>
 *   leaked    = elapsed_ms * (leakTokens / leakPeriodMs)  // 漏出量
 *   water_level = max(0, water_level - leaked) + 1     // 新请求入桶
 *   if water_level > capacity → reject
 * </pre>
 */
public class LeakyBucketRateLimiter implements RateLimiter<RateLimitConfig.LeakyBucket> {

    private static final String SCRIPT = """
            local key        = KEYS[1]
            local now        = tonumber(ARGV[1])
            local capacity   = tonumber(ARGV[2])
            local leak_rate  = tonumber(ARGV[3])
            local data       = redis.call('HMGET', key, 'last_time', 'water_level')
            local last_time  = tonumber(data[1]) or now
            local water      = tonumber(data[2]) or 0
            -- 计算漏出量
            local elapsed    = math.max(0, now - last_time)
            local leaked     = elapsed * leak_rate
            water = math.max(0, water - leaked)
            -- 新请求入桶
            if water < capacity then
                water = water + 1
                redis.call('HMSET', key, 'last_time', now, 'water_level', water)
                -- TTL = 桶满时间的 2 倍
                local ttl_ms = math.ceil(capacity / leak_rate) * 2
                redis.call('PEXPIRE', key, ttl_ms)
                return 1
            end
            return 0
            """;

    private final RedisScriptExecutor scriptExecutor;
    private final String keyNamespace;
    private final KeyNamingStrategy keyNamingStrategy;

    public LeakyBucketRateLimiter(RedisScriptExecutor scriptExecutor,
                                  String keyPrefix,
                                  KeyNamingStrategy keyNamingStrategy) {
        this.scriptExecutor = scriptExecutor;
        this.keyNamespace = KeyNamespaceUtils.namespace(keyPrefix, "redis-plus:ratelimit:leaky");
        this.keyNamingStrategy = keyNamingStrategy;
    }

    @Override
    public boolean tryAcquire(String key, RateLimitConfig.LeakyBucket config) {
        String redisKey = keyNamingStrategy.resolve(keyNamespace, key);
        long now = System.currentTimeMillis();
        double leakRate = (double) config.leakTokens() / config.leakPeriod().toMillis();
        Long result = scriptExecutor.execute(SCRIPT, Long.class,
                Collections.singletonList(redisKey),
                String.valueOf(now),
                String.valueOf(config.capacity()),
                String.valueOf(leakRate));
        return result != null && result == 1L;
    }
}
