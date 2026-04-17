package com.actomize.redis.plus.ratelimit.impl;

import com.actomize.redis.plus.core.key.KeyNamespaceUtils;
import com.actomize.redis.plus.core.key.KeyNamingStrategy;
import com.actomize.redis.plus.core.script.RedisScriptExecutor;
import com.actomize.redis.plus.ratelimit.RateLimitConfig;
import com.actomize.redis.plus.ratelimit.RateLimiter;

import java.util.Collections;

/**
 * 基于 Redis INCR 的固定窗口限流器
 *
 * <p>实现简单高效，但存在窗口边界突刺问题（窗口切换瞬间可能放过 2*limit 次请求）。
 * 适合对精度要求不高的粗粒度限流场景。
 */
public class FixedWindowRateLimiter implements RateLimiter<RateLimitConfig.FixedWindow> {

    private static final String SCRIPT = """
            local key    = KEYS[1]
            local limit  = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local current = redis.call('INCR', key)
            if current == 1 then
                redis.call('PEXPIRE', key, window)
            end
            if current <= limit then
                return 1
            end
            return 0
            """;

    private final RedisScriptExecutor scriptExecutor;
    private final String keyNamespace;
    private final KeyNamingStrategy keyNamingStrategy;

    public FixedWindowRateLimiter(RedisScriptExecutor scriptExecutor,
                                  String keyPrefix,
                                  KeyNamingStrategy keyNamingStrategy) {
        this.scriptExecutor = scriptExecutor;
        this.keyNamespace = KeyNamespaceUtils.namespace(keyPrefix, "redis-plus:ratelimit:fixed");
        this.keyNamingStrategy = keyNamingStrategy;
    }

    @Override
    public boolean tryAcquire(String key, RateLimitConfig.FixedWindow config) {
        String redisKey = keyNamingStrategy.resolve(keyNamespace, key);
        Long result = scriptExecutor.execute(SCRIPT, Long.class,
                Collections.singletonList(redisKey),
                String.valueOf(config.limit()),
                String.valueOf(config.window().toMillis()));
        return result != null && result == 1L;
    }
}
