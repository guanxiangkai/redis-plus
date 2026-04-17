package com.actomize.redis.plus.core.observation;

import java.util.Map;

/**
 * redis-plus 统一 Observation SPI。
 */
public interface RedisPlusObserver {

    static RedisPlusObserver noop() {
        return NoopRedisPlusObserver.INSTANCE;
    }

    /**
     * 对一次操作进行观测。
     */
    <T> T observe(RedisPlusObservationType type,
                  Map<String, String> lowCardinalityTags,
                  Map<String, String> highCardinalityTags,
                  CheckedSupplier<T> supplier) throws Throwable;

    /**
     * 对一次无返回值操作进行观测。
     */
    default void observe(RedisPlusObservationType type,
                         Map<String, String> lowCardinalityTags,
                         Map<String, String> highCardinalityTags,
                         CheckedRunnable runnable) throws Throwable {
        observe(type, lowCardinalityTags, highCardinalityTags, () -> {
            runnable.run();
            return null;
        });
    }

    @FunctionalInterface
    interface CheckedSupplier<T> {
        T get() throws Throwable;
    }

    @FunctionalInterface
    interface CheckedRunnable {
        void run() throws Throwable;
    }
}
