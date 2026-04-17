package com.actomize.redis.plus.autoconfigure.idempotent;

import com.actomize.redis.plus.autoconfigure.cache.RedisPlusCacheAutoConfiguration;
import com.actomize.redis.plus.autoconfigure.governance.RedisPlusGovernanceAutoConfiguration;
import com.actomize.redis.plus.autoconfigure.properties.RedisPlusProperties;
import com.actomize.redis.plus.core.key.KeyNamingStrategy;
import com.actomize.redis.plus.core.observation.RedisPlusObserver;
import com.actomize.redis.plus.core.serializer.ValueSerializer;
import com.actomize.redis.plus.idempotent.IdempotentExecutor;
import com.actomize.redis.plus.idempotent.aop.IdempotentAspect;
import com.actomize.redis.plus.idempotent.impl.RedisIdempotentExecutor;
import com.actomize.redis.plus.idempotent.impl.RedisIdempotentStateStore;
import com.actomize.redis.plus.idempotent.spi.IdempotentKeyResolver;
import com.actomize.redis.plus.idempotent.spi.IdempotentStateStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 幂等能力自动装配。
 */
@AutoConfiguration(after = {RedisPlusCacheAutoConfiguration.class, RedisPlusGovernanceAutoConfiguration.class})
@ConditionalOnClass(StringRedisTemplate.class)
@EnableConfigurationProperties(RedisPlusProperties.class)
public class RedisPlusIdempotentAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(IdempotentStateStore.class)
    public RedisIdempotentStateStore redisIdempotentStateStore(StringRedisTemplate redisTemplate,
                                                               ValueSerializer valueSerializer) {
        return new RedisIdempotentStateStore(redisTemplate, valueSerializer);
    }

    @Bean
    @ConditionalOnMissingBean(IdempotentExecutor.class)
    public RedisIdempotentExecutor redisIdempotentExecutor(IdempotentStateStore stateStore,
                                                           ValueSerializer valueSerializer,
                                                           KeyNamingStrategy keyNamingStrategy,
                                                           RedisPlusObserver observer,
                                                           RedisPlusProperties properties) {
        String keyPrefix = properties.getIdempotent().getKeyPrefix();
        return new RedisIdempotentExecutor(stateStore, valueSerializer,
                keyPrefix, keyNamingStrategy, observer);
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotentAspect idempotentAspect(IdempotentExecutor executor,
                                             ObjectProvider<IdempotentKeyResolver> keyResolverProvider) {
        return new IdempotentAspect(executor, keyResolverProvider.getIfAvailable());
    }
}
