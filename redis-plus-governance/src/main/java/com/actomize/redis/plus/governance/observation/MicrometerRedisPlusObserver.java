package com.actomize.redis.plus.governance.observation;

import com.actomize.redis.plus.core.observation.RedisPlusObservationType;
import com.actomize.redis.plus.core.observation.RedisPlusObserver;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import java.util.Map;

/**
 * 基于 Micrometer Observation 的统一观测实现。
 */
public class MicrometerRedisPlusObserver implements RedisPlusObserver {

    private final ObservationRegistry observationRegistry;
    private final RedisPlusObservationConvention convention;

    public MicrometerRedisPlusObserver(ObservationRegistry observationRegistry,
                                       RedisPlusObservationConvention convention) {
        this.observationRegistry = observationRegistry;
        this.convention = convention;
    }

    @Override
    public <T> T observe(RedisPlusObservationType type,
                         Map<String, String> lowCardinalityTags,
                         Map<String, String> highCardinalityTags,
                         CheckedSupplier<T> supplier) throws Throwable {
        Observation observation = Observation.createNotStarted(observationName(type), observationRegistry)
                .contextualName(type.name().toLowerCase());
        applyTags(observation, lowCardinalityTags, highCardinalityTags);
        return observation.observeChecked(supplier::get);
    }

    private String observationName(RedisPlusObservationType type) {
        return switch (type) {
            case LOCK -> convention.lockObservationName();
            case CACHE_GET -> convention.cacheGetObservationName();
            case CACHE_PUT -> convention.cachePutObservationName();
            case CACHE_EVICT -> convention.cacheEvictObservationName();
            case RATELIMIT -> convention.rateLimitObservationName();
            case IDEMPOTENT -> convention.idempotentObservationName();
            case QUEUE_SEND -> convention.queueSendObservationName();
            case QUEUE_CONSUME -> convention.queueConsumeObservationName();
        };
    }

    private void applyTags(Observation observation,
                           Map<String, String> lowCardinalityTags,
                           Map<String, String> highCardinalityTags) {
        if (lowCardinalityTags != null) {
            lowCardinalityTags.forEach((key, value) -> {
                if (key != null && value != null && !value.isBlank()) {
                    observation.lowCardinalityKeyValue(key, value);
                }
            });
        }
        if (highCardinalityTags != null) {
            highCardinalityTags.forEach((key, value) -> {
                if (key != null && value != null && !value.isBlank()) {
                    observation.highCardinalityKeyValue(key, value);
                }
            });
        }
    }
}
