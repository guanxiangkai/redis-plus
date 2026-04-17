package com.actomize.redis.plus.ratelimit.impl;

import com.actomize.redis.plus.core.key.KeyNamespaceUtils;
import com.actomize.redis.plus.core.key.KeyNamingStrategy;
import com.actomize.redis.plus.core.script.RedisScriptExecutor;
import com.actomize.redis.plus.ratelimit.RateLimitConfig;
import com.actomize.redis.plus.ratelimit.RateLimiter;

import java.util.Collections;

/**
 * 基于 Redis ZSet 的滑动窗口限流器
 *
 * <p>使用有序集合（ZSet）记录每次请求的时间戳，
 * 通过 ZREMRANGEBYSCORE 清理过期记录后统计当前窗口内请求数。
 *
 * <p>时间复杂度：O(log N) per request（N = 窗口内请求数）
 */
public class SlidingWindowRateLimiter implements RateLimiter<RateLimitConfig.SlidingWindow> {

    private static final String SCRIPT = """
            local key        = KEYS[1]
            local now        = tonumber(ARGV[1])
            local window     = tonumber(ARGV[2])
            local limit      = tonumber(ARGV[3])
            local uniqueId   = ARGV[4]
            local minScore   = now - window
            -- 清理过期记录
            redis.call('ZREMRANGEBYSCORE', key, '-inf', minScore)
            -- 统计当前窗口请求数
            local count = redis.call('ZCARD', key)
            if count < limit then
                redis.call('ZADD', key, now, uniqueId)
                redis.call('PEXPIRE', key, window)
                return 1
            end
            return 0
            """;

    private final RedisScriptExecutor scriptExecutor;
    private final String keyNamespace;
    private final KeyNamingStrategy keyNamingStrategy;

    public SlidingWindowRateLimiter(RedisScriptExecutor scriptExecutor,
                                    String keyPrefix,
                                    KeyNamingStrategy keyNamingStrategy) {
        this.scriptExecutor = scriptExecutor;
        this.keyNamespace = KeyNamespaceUtils.namespace(keyPrefix, "redis-plus:ratelimit:sliding");
        this.keyNamingStrategy = keyNamingStrategy;
    }

    @Override
    public boolean tryAcquire(String key, RateLimitConfig.SlidingWindow config) {
        String redisKey = keyNamingStrategy.resolve(keyNamespace, key);
        long now = System.currentTimeMillis();
        // 唯一 ID 防止同毫秒多请求使用同分数被 ZSet 去重
        String uniqueId = now + ":" + Thread.currentThread().threadId() + ":" + System.nanoTime();
        Long result = scriptExecutor.execute(SCRIPT, Long.class,
                Collections.singletonList(redisKey),
                String.valueOf(now),
                String.valueOf(config.window().toMillis()),
                String.valueOf(config.limit()),
                uniqueId);
        return result != null && result == 1L;
    }
}
