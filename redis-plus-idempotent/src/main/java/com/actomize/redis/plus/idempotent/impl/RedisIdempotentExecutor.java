package com.actomize.redis.plus.idempotent.impl;

import com.actomize.redis.plus.core.exception.RedisPlusException;
import com.actomize.redis.plus.core.util.ExceptionUtils;
import com.actomize.redis.plus.core.key.DefaultKeyNamingStrategy;
import com.actomize.redis.plus.core.key.KeyNamespaceUtils;
import com.actomize.redis.plus.core.key.KeyNamingStrategy;
import com.actomize.redis.plus.core.observation.RedisPlusObservationType;
import com.actomize.redis.plus.core.observation.RedisPlusObserver;
import com.actomize.redis.plus.core.serializer.ValueSerializer;
import com.actomize.redis.plus.idempotent.IdempotentExecutor;
import com.actomize.redis.plus.idempotent.IdempotentState;
import com.actomize.redis.plus.idempotent.spi.IdempotentStateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 基于 {@link IdempotentStateStore} SPI 的幂等执行器实现
 *
 * <p>状态流转（存储格式：结构化 JSON {@link IdempotentState}）：
 * <ul>
 *   <li>{@code PROCESSING} — 正在执行，其他线程等待或直接拒绝</li>
 *   <li>{@code DONE}       — 执行完成，后续请求直接返回历史结果</li>
 *   <li>{@code FAILED}     — 执行失败，允许重试</li>
 * </ul>
 *
 * <p>所有 Redis 操作通过 {@link IdempotentStateStore} SPI 解耦；
 * 用户可替换为数据库存储或其他方案。
 */
public class RedisIdempotentExecutor implements IdempotentExecutor {

    private static final Logger log = LoggerFactory.getLogger(RedisIdempotentExecutor.class);

    private static final Duration DEFAULT_FAILURE_TTL = Duration.ofMinutes(5);
    private static final Duration DEFAULT_PROCESSING_TIMEOUT = Duration.ofMinutes(10);

    private final IdempotentStateStore stateStore;
    private final ValueSerializer serializer;
    private final String keyNamespace;
    private final KeyNamingStrategy keyNamingStrategy;
    private final RedisPlusObserver observer;
    /**
     * PROCESSING 状态 TTL，独立于业务结果 TTL。
     * 应设置为任务最大执行时间 + 安全缓冲，防止慢任务导致 PROCESSING key 提前过期，
     * 引发并发重复执行。
     */
    private final Duration processingTimeout;

    public RedisIdempotentExecutor(IdempotentStateStore stateStore,
                                   ValueSerializer serializer) {
        this(stateStore, serializer, null, new DefaultKeyNamingStrategy());
    }

    public RedisIdempotentExecutor(IdempotentStateStore stateStore,
                                   ValueSerializer serializer,
                                   String keyPrefix) {
        this(stateStore, serializer, keyPrefix, new DefaultKeyNamingStrategy(), RedisPlusObserver.noop());
    }

    public RedisIdempotentExecutor(IdempotentStateStore stateStore,
                                   ValueSerializer serializer,
                                   String keyPrefix,
                                   KeyNamingStrategy keyNamingStrategy) {
        this(stateStore, serializer, keyPrefix, keyNamingStrategy, RedisPlusObserver.noop());
    }

    public RedisIdempotentExecutor(IdempotentStateStore stateStore,
                                   ValueSerializer serializer,
                                   String keyPrefix,
                                   KeyNamingStrategy keyNamingStrategy,
                                   RedisPlusObserver observer) {
        this(stateStore, serializer, keyPrefix, keyNamingStrategy, observer, DEFAULT_PROCESSING_TIMEOUT);
    }

    public RedisIdempotentExecutor(IdempotentStateStore stateStore,
                                   ValueSerializer serializer,
                                   String keyPrefix,
                                   KeyNamingStrategy keyNamingStrategy,
                                   RedisPlusObserver observer,
                                   Duration processingTimeout) {
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore must not be null");
        this.serializer = Objects.requireNonNull(serializer, "serializer must not be null");
        this.keyNamespace = KeyNamespaceUtils.namespace(keyPrefix, "redis-plus:idempotent");
        this.keyNamingStrategy = Objects.requireNonNull(keyNamingStrategy, "keyNamingStrategy must not be null");
        this.observer = observer != null ? observer : RedisPlusObserver.noop();
        this.processingTimeout = processingTimeout != null && !processingTimeout.isNegative() && !processingTimeout.isZero()
                ? processingTimeout : DEFAULT_PROCESSING_TIMEOUT;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T executeOnce(String idempotentKey, Duration ttl, Supplier<T> task) {
        String redisKey = keyNamingStrategy.resolve(keyNamespace, idempotentKey);
        try {
            return observer.observe(RedisPlusObservationType.IDEMPOTENT,
                    Map.of(),
                    Map.of("idempotent.key", idempotentKey),
                    () -> {
                        // PROCESSING state uses its own timeout, not the business result TTL.
                        Optional<String> existing = stateStore.tryAcquire(redisKey, processingTimeout);

                        if (existing.isPresent()) {
                            IdempotentState state = deserializeState(existing.get(), idempotentKey);
                            return switch (state.getStatus()) {
                                case DONE -> {
                                    log.debug("[redis-plus] 幂等命中历史结果，key={}", idempotentKey);
                                    yield restoreResult(state, idempotentKey);
                                }
                                case PROCESSING ->
                                        throw new RedisPlusException("幂等操作正在处理中，请稍后重试，key=" + idempotentKey);
                                case FAILED -> {
                                    String processingJson = serializer.serialize(IdempotentState.processing());
                                    boolean reacquired = stateStore.tryReacquireFromFailed(
                                            redisKey, existing.get(), processingJson, processingTimeout);
                                    if (!reacquired) {
                                        throw new RedisPlusException("幂等操作正在处理中，请稍后重试，key=" + idempotentKey);
                                    }
                                    log.warn("[redis-plus] 幂等操作上次失败，允许重试，key={}", idempotentKey);
                                    yield executeTask(redisKey, idempotentKey, ttl, task);
                                }
                            };
                        }

                        return executeTask(redisKey, idempotentKey, ttl, task);
                    });
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RedisPlusException("幂等观测执行失败，key=" + idempotentKey, t);
        }
    }

    private <T> T executeTask(String redisKey, String idempotentKey, Duration ttl, Supplier<T> task) {
        try {
            T result = task.get();
            String resultJson = result != null ? serializer.serialize(result) : null;
            String resultType = result != null ? result.getClass().getName() : null;
            IdempotentState doneState = IdempotentState.done(resultJson, resultType);
            stateStore.markDone(redisKey, serializer.serialize(doneState), ttl);
            return result;
        } catch (Throwable e) {
            // 捕获所有 Throwable（含 Error），确保 OOM / StackOverflow 等情况下
            // 幂等 key 也能从 PROCESSING 切换到 FAILED，允许后续调用重试。
            try {
                stateStore.markFailed(redisKey, DEFAULT_FAILURE_TTL);
            } catch (Exception markEx) {
                log.warn("[redis-plus] 幂等 FAILED 状态写入失败，key={}", idempotentKey, markEx);
            }
            ExceptionUtils.sneakyThrow(e);
            throw new AssertionError(); // unreachable
        }
    }

    private IdempotentState deserializeState(String json, String idempotentKey) {
        try {
            return serializer.deserialize(json, IdempotentState.class);
        } catch (Exception e) {
            throw new RedisPlusException(
                    "幂等状态反序列化失败，key=" + idempotentKey + "，json=" + json, e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T restoreResult(IdempotentState state, String idempotentKey) {
        if (state.getResultJson() == null) return null;
        if (state.getResultType() == null) return null;
        try {
            Class<T> type = (Class<T>) Class.forName(state.getResultType());
            return serializer.deserialize(state.getResultJson(), type);
        } catch (ClassNotFoundException e) {
            throw new RedisPlusException(
                    "幂等历史结果类型不可加载，type=" + state.getResultType() + "，key=" + idempotentKey, e);
        }
    }
}
