package com.actomize.redis.plus.autoconfigure.enhance;

import com.actomize.redis.plus.autoconfigure.cache.RedisPlusCacheAutoConfiguration;
import com.actomize.redis.plus.autoconfigure.properties.RedisPlusProperties;
import com.actomize.redis.plus.cache.ThreeLevelCacheTemplate;
import com.actomize.redis.plus.cache.spi.CacheValueSerializer;
import com.actomize.redis.plus.cache.spi.LocalCacheProvider;
import com.actomize.redis.plus.enhance.batch.BatchCacheOperations;
import com.actomize.redis.plus.enhance.batch.impl.RedisBatchCacheOperations;
import com.actomize.redis.plus.enhance.bloom.BloomFilter;
import com.actomize.redis.plus.enhance.bloom.aop.BloomCheckAspect;
import com.actomize.redis.plus.enhance.bloom.impl.RedisBitmapBloomFilter;
import com.actomize.redis.plus.enhance.bloom.spi.BloomHashProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

/**
 * 缓存增强能力自动装配（布隆过滤器 + 批量操作）
 */
@AutoConfiguration(after = RedisPlusCacheAutoConfiguration.class)
@EnableConfigurationProperties(RedisPlusProperties.class)
public class RedisPlusEnhanceAutoConfiguration {

    // ── 布隆过滤器 ──────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(BloomHashProvider.class)
    public BloomHashProvider bloomHashProvider() {
        return BloomHashProvider.fnv1a();
    }

    /**
     * 默认布隆过滤器（名称为 "default"）。
     * 如需多个过滤器，用户可自行注册 {@code Map<String, BloomFilter>} Bean。
     */
    @Bean("defaultBloomFilter")
    @ConditionalOnMissingBean(name = "defaultBloomFilter")
    @ConditionalOnProperty(prefix = "redis-plus.enhance.bloom", name = "enabled", matchIfMissing = true)
    public BloomFilter<String> defaultBloomFilter(BloomHashProvider hashProvider,
                                                  StringRedisTemplate redisTemplate,
                                                  RedisPlusProperties properties) {
        var bloomProps = properties.getEnhance().getBloom();
        return new RedisBitmapBloomFilter<>(
                "default",
                bloomProps.getExpectedInsertions(),
                bloomProps.getFalsePositiveProbability(),
                bloomProps.getVersion(),
                hashProvider,
                redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(BloomCheckAspect.class)
    @ConditionalOnProperty(prefix = "redis-plus.enhance.bloom", name = "enabled", matchIfMissing = true)
    public BloomCheckAspect bloomCheckAspect(Map<String, BloomFilter<String>> bloomFilters) {
        return new BloomCheckAspect(bloomFilters);
    }

    // ── 批量操作 ─────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(BatchCacheOperations.class)
    public BatchCacheOperations batchCacheOperations(ThreeLevelCacheTemplate cacheTemplate,
                                                     LocalCacheProvider l1,
                                                     StringRedisTemplate redisTemplate,
                                                     CacheValueSerializer serializer) {
        return new RedisBatchCacheOperations(cacheTemplate, l1, redisTemplate, serializer);
    }
}

