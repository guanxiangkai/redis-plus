package com.actomize.redis.plus.core.observation;

/**
 * redis-plus 统一 Observation 操作类型。
 */
public enum RedisPlusObservationType {
    LOCK,
    CACHE_GET,
    CACHE_PUT,
    CACHE_EVICT,
    RATELIMIT,
    IDEMPOTENT,
    QUEUE_SEND,
    QUEUE_CONSUME
}
