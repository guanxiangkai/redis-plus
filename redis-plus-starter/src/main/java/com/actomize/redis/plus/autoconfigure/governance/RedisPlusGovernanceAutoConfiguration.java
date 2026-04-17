package com.actomize.redis.plus.autoconfigure.governance;

import com.actomize.redis.plus.autoconfigure.cache.RedisPlusCacheAutoConfiguration;
import com.actomize.redis.plus.core.metrics.RedisPlusMetrics;
import com.actomize.redis.plus.core.observation.RedisPlusObserver;
import com.actomize.redis.plus.governance.health.RedisPlusHealthContributor;
import com.actomize.redis.plus.governance.metrics.MicrometerRedisPlusMetrics;
import com.actomize.redis.plus.governance.observation.DefaultRedisPlusObservationConvention;
import com.actomize.redis.plus.governance.observation.MicrometerRedisPlusObserver;
import com.actomize.redis.plus.governance.observation.RedisPlusObservationConvention;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.util.Map;

/**
 * 治理与可观测性自动装配（Micrometer 指标 + 健康检查）
 *
 * <p>当 {@link MeterRegistry} 在 classpath 中时，注册 {@link MicrometerRedisPlusMetrics}
 * 替换 {@code RedisPlusCacheAutoConfiguration} 注册的 {@link com.actomize.redis.plus.core.metrics.NoopRedisPlusMetrics}。
 */
@AutoConfiguration(after = RedisPlusCacheAutoConfiguration.class)
@ConditionalOnClass(MeterRegistry.class)
public class RedisPlusGovernanceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RedisPlusObservationConvention.class)
    public RedisPlusObservationConvention redisPlusObservationConvention() {
        return new DefaultRedisPlusObservationConvention();
    }

    @Bean
    @ConditionalOnMissingBean(RedisPlusMetrics.class)
    public RedisPlusMetrics redisPlusMetrics(MeterRegistry meterRegistry) {
        return new MicrometerRedisPlusMetrics(meterRegistry);
    }

    @Bean
    @ConditionalOnBean(ObservationRegistry.class)
    @ConditionalOnMissingBean(RedisPlusObserver.class)
    public RedisPlusObserver redisPlusObserver(ObservationRegistry observationRegistry,
                                               RedisPlusObservationConvention convention) {
        return new MicrometerRedisPlusObserver(observationRegistry, convention);
    }

    @Bean("redisPlusHealthContributor")
    @ConditionalOnMissingBean(name = "redisPlusHealthContributor")
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.AbstractHealthIndicator")
    public RedisPlusHealthContributor redisPlusHealthContributor(
            Map<String, RedisConnectionFactory> connectionFactories) {
        return new RedisPlusHealthContributor(connectionFactories);
    }
}
