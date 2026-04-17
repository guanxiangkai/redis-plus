package com.actomize.redis.plus.autoconfigure.cache;

import com.actomize.redis.plus.autoconfigure.lock.RedisPlusLockAutoConfiguration;
import com.actomize.redis.plus.autoconfigure.properties.RedisPlusProperties;
import com.actomize.redis.plus.cache.ThreeLevelCacheTemplate;
import com.actomize.redis.plus.cache.aop.ThreeLevelCacheAspect;
import com.actomize.redis.plus.cache.impl.CacheMetricsCollectorAdapter;
import com.actomize.redis.plus.cache.local.CaffeineLocalCacheProvider;
import com.actomize.redis.plus.cache.local.ConcurrentMapLocalCacheProvider;
import com.actomize.redis.plus.cache.protection.DistributedCacheLoadProtection;
import com.actomize.redis.plus.cache.serializer.JacksonCacheValueSerializer;
import com.actomize.redis.plus.cache.spi.*;
import com.actomize.redis.plus.core.expire.ExpireStrategy;
import com.actomize.redis.plus.core.key.KeyNamingStrategy;
import com.actomize.redis.plus.core.metrics.RedisPlusMetrics;
import com.actomize.redis.plus.core.observation.RedisPlusObserver;
import com.actomize.redis.plus.core.serializer.ValueSerializer;
import com.actomize.redis.plus.lock.impl.RedisLockFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 三级缓存自动装配
 *
 * <p>注册的 Bean：
 * <ul>
 *   <li>{@link LocalCacheProvider} — L1 本地缓存（Caffeine 优先，兜底 ConcurrentHashMap）</li>
 *   <li>{@link CacheValueSerializer} — 缓存值序列化器（Jackson）</li>
 *   <li>{@link ExpireStrategy} — TTL 策略（随机抖动防雪崩）</li>
 *   <li>{@link CacheLoadProtection} — 回源保护（有 lock 模块时用分布式锁，否则本地锁）</li>
 *   <li>{@link CacheConsistencyStrategy} — 缓存一致性策略（默认直接失效）</li>
 *   <li>{@link ThreeLevelCacheTemplate} — 三级缓存编程式入口</li>
 *   <li>{@link ThreeLevelCacheAspect} — AOP 注解驱动</li>
 * </ul>
 */
@AutoConfiguration(after = RedisPlusLockAutoConfiguration.class)
@ConditionalOnClass(StringRedisTemplate.class)
@EnableConfigurationProperties(RedisPlusProperties.class)
public class RedisPlusCacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(LocalCacheProvider.class)
    @ConditionalOnClass(name = "com.github.benmanes.caffeine.cache.Caffeine")
    public LocalCacheProvider caffeineLocalCacheProvider(RedisPlusProperties properties) {
        var localProps = properties.getCache().getLocal();
        return new CaffeineLocalCacheProvider(localProps.getMaximumSize(), localProps.getTtl());
    }

    @Bean
    @ConditionalOnMissingBean(LocalCacheProvider.class)
    public LocalCacheProvider concurrentMapLocalCacheProvider() {
        return new ConcurrentMapLocalCacheProvider();
    }

    @Bean
    @ConditionalOnMissingBean(CacheValueSerializer.class)
    public CacheValueSerializer cacheValueSerializer(ObjectProvider<ObjectMapper> objectMapperProvider,
                                                     RedisPlusProperties properties) {
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        return new JacksonCacheValueSerializer(objectMapper, properties.getCache().getAllowedPackages());
    }

    /**
     * 同时将 {@link JacksonCacheValueSerializer} 注册为 {@link ValueSerializer}，
     * 供 {@code RedisIdempotentExecutor} 等依赖 core 序列化接口的组件注入。
     */
    @Bean
    @ConditionalOnMissingBean(ValueSerializer.class)
    public ValueSerializer valueSerializer(CacheValueSerializer cacheValueSerializer) {
        // JacksonCacheValueSerializer 实现了两个接口，直接委托
        return cacheValueSerializer;
    }

    @Bean
    @ConditionalOnMissingBean(ExpireStrategy.class)
    public ExpireStrategy expireStrategy(RedisPlusProperties properties) {
        double jitter = properties.getCache().getJitterRatio();
        return jitter > 0 ? ExpireStrategy.randomJitter(jitter) : ExpireStrategy.fixed();
    }

    @Bean
    @ConditionalOnMissingBean(CacheMetricsCollector.class)
    public CacheMetricsCollector cacheMetricsCollector(ObjectProvider<RedisPlusMetrics> metricsProvider) {
        return new CacheMetricsCollectorAdapter(metricsProvider);
    }

    /**
     * 缓存一致性策略（默认直接失效）。
     * 用户可通过注册自定义 {@link CacheConsistencyStrategy} Bean 替换为延迟双删或写穿策略。
     */
    @Bean
    @ConditionalOnMissingBean(CacheConsistencyStrategy.class)
    public CacheConsistencyStrategy cacheConsistencyStrategy() {
        return CacheConsistencyStrategy.invalidate();
    }

    /**
     * 回源保护：有 {@link RedisLockFactory} 时使用分布式锁，否则回退到 JVM 本地锁。
     */
    @Bean
    @ConditionalOnMissingBean(CacheLoadProtection.class)
    @ConditionalOnClass(name = "com.actomize.redis.plus.lock.impl.RedisLockFactory")
    public CacheLoadProtection distributedCacheLoadProtection(ObjectProvider<RedisLockFactory> lockFactoryProvider) {
        RedisLockFactory lockFactory = lockFactoryProvider.getIfAvailable();
        if (lockFactory != null) {
            return new DistributedCacheLoadProtection(lockFactory);
        }
        return CacheLoadProtection.local();
    }

    @Bean
    @ConditionalOnMissingBean(CacheLoadProtection.class)
    public CacheLoadProtection localCacheLoadProtection() {
        return CacheLoadProtection.local();
    }

    @Bean
    @ConditionalOnMissingBean
    public ThreeLevelCacheTemplate threeLevelCacheTemplate(LocalCacheProvider l1,
                                                           StringRedisTemplate redisTemplate,
                                                           CacheValueSerializer serializer,
                                                           CacheLoadProtection loadProtection,
                                                           ExpireStrategy expireStrategy,
                                                           CacheMetricsCollector cacheMetricsCollector,
                                                           CacheConsistencyStrategy consistencyStrategy,
                                                           KeyNamingStrategy keyNamingStrategy,
                                                           RedisPlusObserver observer,
                                                           RedisPlusProperties properties) {
        String keyPrefix = properties.getCache().getKeyPrefix();
        return new ThreeLevelCacheTemplate(l1, redisTemplate, serializer, loadProtection,
                expireStrategy, cacheMetricsCollector, keyPrefix, consistencyStrategy, keyNamingStrategy, observer);
    }

    @Bean
    @ConditionalOnMissingBean
    public ThreeLevelCacheAspect threeLevelCacheAspect(ThreeLevelCacheTemplate cacheTemplate,
                                                       ObjectProvider<CacheKeyResolver> keyResolverProvider) {
        return new ThreeLevelCacheAspect(cacheTemplate, keyResolverProvider.getIfAvailable());
    }
}
