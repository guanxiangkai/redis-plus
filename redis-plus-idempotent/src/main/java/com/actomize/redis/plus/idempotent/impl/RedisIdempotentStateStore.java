package com.actomize.redis.plus.idempotent.impl;

import com.actomize.redis.plus.core.serializer.ValueSerializer;
import com.actomize.redis.plus.idempotent.IdempotentState;
import com.actomize.redis.plus.idempotent.spi.IdempotentStateStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;

/**
 * 基于 Redis 的幂等状态存储默认实现
 *
 * <p>使用 Lua 脚本保证 CAS 原子性：
 * <ul>
 *   <li>{@link #tryAcquire} — 原子性地"仅当 Key 不存在时"写入 {@code PROCESSING} 状态</li>
 *   <li>{@link #markDone} / {@link #markFailed} — 普通 SET 覆写状态</li>
 * </ul>
 *
 * <p>存储格式：Key 对应的 Value 为结构化 {@link IdempotentState} JSON 字符串，
 * 由 {@link RedisIdempotentExecutor} 通过 {@link ValueSerializer} 序列化 / 反序列化。
 */
public class RedisIdempotentStateStore implements IdempotentStateStore {

    /** CAS 占位脚本：仅当 Key 不存在时设置 PROCESSING 状态 */
    private static final String TRY_ACQUIRE_SCRIPT = """
            local existing = redis.call('GET', KEYS[1])
            if existing then
                return existing
            end
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
            return nil
            """;

    private static final DefaultRedisScript<String> TRY_ACQUIRE =
            new DefaultRedisScript<>(TRY_ACQUIRE_SCRIPT, String.class);

    /** CAS 脚本：原子地将 FAILED 状态替换为 PROCESSING（并发重试保护） */
    private static final String TRY_REACQUIRE_FAILED_SCRIPT = """
            local current = redis.call('GET', KEYS[1])
            if current == ARGV[1] then
                redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
                return 1
            end
            return 0
            """;

    private static final DefaultRedisScript<Long> TRY_REACQUIRE_FAILED =
            new DefaultRedisScript<>(TRY_REACQUIRE_FAILED_SCRIPT, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ValueSerializer serializer;

    public RedisIdempotentStateStore(StringRedisTemplate redisTemplate,
                                     ValueSerializer serializer) {
        this.redisTemplate = redisTemplate;
        this.serializer = serializer;
    }

    @Override
    public Optional<String> tryAcquire(String idempotentKey, Duration ttl) {
        String processingJson = serializer.serialize(IdempotentState.processing());
        String existing = redisTemplate.execute(TRY_ACQUIRE,
                Collections.singletonList(idempotentKey),
                processingJson,
                String.valueOf(ttl.toMillis()));
        return Optional.ofNullable(existing);
    }

    @Override
    public void markDone(String idempotentKey, String resultValue, Duration ttl) {
        redisTemplate.opsForValue().set(idempotentKey, resultValue, ttl);
    }

    @Override
    public void markFailed(String idempotentKey, Duration failureTtl) {
        String failedJson = serializer.serialize(IdempotentState.failed());
        redisTemplate.opsForValue().set(idempotentKey, failedJson, failureTtl);
    }

    @Override
    public Optional<String> getStatus(String idempotentKey) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(idempotentKey));
    }

    @Override
    public void delete(String idempotentKey) {
        redisTemplate.delete(idempotentKey);
    }

    @Override
    public boolean tryReacquireFromFailed(String idempotentKey, String failedValue,
                                          String processingValue, Duration processingTimeout) {
        Long result = redisTemplate.execute(TRY_REACQUIRE_FAILED,
                Collections.singletonList(idempotentKey),
                failedValue,
                processingValue,
                String.valueOf(processingTimeout.toMillis()));
        return Long.valueOf(1L).equals(result);
    }
}
