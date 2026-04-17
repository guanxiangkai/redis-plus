package com.actomize.redis.plus.ratelimit.impl;

import com.actomize.redis.plus.core.key.KeyNamespaceUtils;
import com.actomize.redis.plus.core.key.KeyNamingStrategy;
import com.actomize.redis.plus.core.script.RedisScriptExecutor;
import com.actomize.redis.plus.ratelimit.RateLimitConfig;
import com.actomize.redis.plus.ratelimit.RateLimiter;

import java.util.Collections;

/**
 * 基于 Redis Hash 的令牌桶限流器
 *
 * <p>支持突发流量：令牌按固定速率补充，请求消耗令牌。
 * 桶内有足够令牌则通过，否则拒绝（不等待）。
 *
 * <p>存储结构（Redis Hash）：
 * <ul>
 *   <li>{@code tokens} — 当前桶内令牌数</li>
 *   <li>{@code last_time} — 上次补充令牌的时间戳（ms）</li>
 * </ul>
 */
public class TokenBucketRateLimiter implements RateLimiter<RateLimitConfig.TokenBucket> {

    private static final String SCRIPT = """
            local key       = KEYS[1]
            local now       = tonumber(ARGV[1])
             local capacity  = tonumber(ARGV[2])
             local refill_tokens = tonumber(ARGV[3])
             local refill_period = tonumber(ARGV[4])
             local requested = tonumber(ARGV[5])
             local data      = redis.call('HMGET', key, 'tokens', 'last_time')
             local tokens    = tonumber(data[1]) or capacity
             local last_time = tonumber(data[2]) or now
             -- 按时间差补充令牌
             local elapsed   = math.max(0, now - last_time)
             local refill    = math.floor(elapsed * refill_tokens / refill_period)
             tokens = math.min(capacity, tokens + refill)
             if tokens >= requested then
                 tokens = tokens - requested
                 redis.call('HMSET', key, 'tokens', tokens, 'last_time', now)
                 redis.call('PEXPIRE', key, math.ceil(capacity * refill_period / refill_tokens) * 2)
                 return 1
             end
             return 0
            """;

    private final RedisScriptExecutor scriptExecutor;
    private final String keyNamespace;
    private final KeyNamingStrategy keyNamingStrategy;

    public TokenBucketRateLimiter(RedisScriptExecutor scriptExecutor,
                                  String keyPrefix,
                                  KeyNamingStrategy keyNamingStrategy) {
        this.scriptExecutor = scriptExecutor;
        this.keyNamespace = KeyNamespaceUtils.namespace(keyPrefix, "redis-plus:ratelimit:token");
        this.keyNamingStrategy = keyNamingStrategy;
    }

    @Override
    public boolean tryAcquire(String key, RateLimitConfig.TokenBucket config) {
        String redisKey = keyNamingStrategy.resolve(keyNamespace, key);
        long now = System.currentTimeMillis();
        Long result = scriptExecutor.execute(SCRIPT, Long.class,
                Collections.singletonList(redisKey),
                String.valueOf(now),
                String.valueOf(config.capacity()),
                String.valueOf(config.refillTokens()),
                String.valueOf(config.refillPeriod().toMillis()),
                "1");                        // requested tokens
        return result != null && result == 1L;
    }
}
