package com.actomize.redis.plus.autoconfigure.lock;

import com.actomize.redis.plus.autoconfigure.core.RedisPlusCoreAutoConfiguration;
import com.actomize.redis.plus.autoconfigure.properties.RedisPlusProperties;
import com.actomize.redis.plus.core.key.KeyNamingStrategy;
import com.actomize.redis.plus.core.observation.RedisPlusObserver;
import com.actomize.redis.plus.core.script.RedisScriptExecutor;
import com.actomize.redis.plus.lock.aop.LockAspect;
import com.actomize.redis.plus.lock.impl.RedisLockFactory;
import com.actomize.redis.plus.lock.impl.SpelLockKeyResolver;
import com.actomize.redis.plus.lock.spi.*;
import com.actomize.redis.plus.lock.spi.impl.DefaultLockLeaseStrategy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

/**
 * 分布式锁自动装配
 *
 * <p>注册 {@link RedisLockFactory}、{@link LockAspect} 以及锁 SPI 默认实现：
 * <ul>
 *   <li>{@link SpelLockKeyResolver} — 默认 SpEL Key 解析器</li>
 *   <li>{@link DefaultLockLeaseStrategy} — 默认租约策略</li>
 * </ul>
 * 用户可通过注册同类型 Bean 替换上述默认实现；
 * {@link LockFailureHandler} 和 {@link LockEventListener} 无内置 Bean，需用户自行注册。
 */
@AutoConfiguration(after = RedisPlusCoreAutoConfiguration.class)
@ConditionalOnClass(StringRedisTemplate.class)
@EnableConfigurationProperties(RedisPlusProperties.class)
public class RedisPlusLockAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RedisLockFactory redisLockFactory(StringRedisTemplate stringRedisTemplate,
                                             RedisScriptExecutor scriptExecutor,
                                             KeyNamingStrategy keyNamingStrategy,
                                             LockLeaseStrategy lockLeaseStrategy,
                                             RedisPlusProperties properties) {
        return new RedisLockFactory(stringRedisTemplate, scriptExecutor,
                properties.getLock().getKeyPrefix(), keyNamingStrategy, lockLeaseStrategy);
    }

    /**
     * 默认 SpEL Lock Key 解析器
     */
    @Bean
    @ConditionalOnMissingBean(LockKeyResolver.class)
    public LockKeyResolver spelLockKeyResolver() {
        return new SpelLockKeyResolver();
    }

    /**
     * 默认锁租约策略
     */
    @Bean
    @ConditionalOnMissingBean(LockLeaseStrategy.class)
    public LockLeaseStrategy defaultLockLeaseStrategy(RedisPlusProperties properties) {
        long leaseMs = properties.getLock().getDefaultLease().toMillis();
        return new DefaultLockLeaseStrategy(leaseMs);
    }

    @Bean
    @ConditionalOnMissingBean
    public LockAspect lockAspect(RedisLockFactory redisLockFactory,
                                 ObjectProvider<LockKeyResolver> keyResolverProvider,
                                 ObjectProvider<LockFailureHandler> failureHandlerProvider,
                                 LockLeaseStrategy lockLeaseStrategy,
                                 RedisPlusObserver observer,
                                 ObjectProvider<List<LockEventListener>> eventListenersProvider,
                                 ApplicationEventPublisher eventPublisher,
                                 RedisPlusProperties properties) {
        LockEventPublisher lockEventPublisher = eventPublisher::publishEvent;
        return new LockAspect(redisLockFactory,
                keyResolverProvider.getIfAvailable(), failureHandlerProvider.getIfAvailable(),
                properties.getLock().getDefaultWait(), observer,
                eventListenersProvider.getIfAvailable(List::of), lockEventPublisher);
    }
}
