package com.actomize.redis.plus.core.observation;

import java.util.Map;

/**
 * 空 Observation 实现。
 */
public enum NoopRedisPlusObserver implements RedisPlusObserver {
    INSTANCE;

    @Override
    public <T> T observe(RedisPlusObservationType type,
                         Map<String, String> lowCardinalityTags,
                         Map<String, String> highCardinalityTags,
                         CheckedSupplier<T> supplier) throws Throwable {
        return supplier.get();
    }
}
