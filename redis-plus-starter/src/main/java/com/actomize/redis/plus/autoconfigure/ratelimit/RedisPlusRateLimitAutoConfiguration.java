package com.actomize.redis.plus.autoconfigure.ratelimit;

import com.actomize.redis.plus.autoconfigure.cache.RedisPlusCacheAutoConfiguration;
import com.actomize.redis.plus.autoconfigure.governance.RedisPlusGovernanceAutoConfiguration;
import com.actomize.redis.plus.autoconfigure.properties.RedisPlusProperties;
import com.actomize.redis.plus.core.key.KeyNamingStrategy;
import com.actomize.redis.plus.core.observation.RedisPlusObserver;
import com.actomize.redis.plus.core.script.RedisScriptExecutor;
import com.actomize.redis.plus.ratelimit.aop.RateLimitAspect;
import com.actomize.redis.plus.ratelimit.impl.FixedWindowRateLimiter;
import com.actomize.redis.plus.ratelimit.impl.LeakyBucketRateLimiter;
import com.actomize.redis.plus.ratelimit.impl.SlidingWindowRateLimiter;
import com.actomize.redis.plus.ratelimit.impl.TokenBucketRateLimiter;
import com.actomize.redis.plus.ratelimit.spi.RateLimitKeyResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 限流能力自动装配。
 */
@AutoConfiguration(after = {RedisPlusCacheAutoConfiguration.class, RedisPlusGovernanceAutoConfiguration.class})
@ConditionalOnClass(StringRedisTemplate.class)
@EnableConfigurationProperties(RedisPlusProperties.class)
public class RedisPlusRateLimitAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SlidingWindowRateLimiter slidingWindowRateLimiter(RedisScriptExecutor scriptExecutor,
                                                             KeyNamingStrategy keyNamingStrategy,
                                                             RedisPlusProperties properties) {
        String prefix = properties.getRatelimit().getKeyPrefix() + "sliding:";
        return new SlidingWindowRateLimiter(scriptExecutor, prefix, keyNamingStrategy);
    }

    @Bean
    @ConditionalOnMissingBean
    public FixedWindowRateLimiter fixedWindowRateLimiter(RedisScriptExecutor scriptExecutor,
                                                         KeyNamingStrategy keyNamingStrategy,
                                                         RedisPlusProperties properties) {
        String prefix = properties.getRatelimit().getKeyPrefix() + "fixed:";
        return new FixedWindowRateLimiter(scriptExecutor, prefix, keyNamingStrategy);
    }

    @Bean
    @ConditionalOnMissingBean
    public TokenBucketRateLimiter tokenBucketRateLimiter(RedisScriptExecutor scriptExecutor,
                                                         KeyNamingStrategy keyNamingStrategy,
                                                         RedisPlusProperties properties) {
        String prefix = properties.getRatelimit().getKeyPrefix() + "token:";
        return new TokenBucketRateLimiter(scriptExecutor, prefix, keyNamingStrategy);
    }

    @Bean
    @ConditionalOnMissingBean
    public LeakyBucketRateLimiter leakyBucketRateLimiter(RedisScriptExecutor scriptExecutor,
                                                         KeyNamingStrategy keyNamingStrategy,
                                                         RedisPlusProperties properties) {
        String prefix = properties.getRatelimit().getKeyPrefix() + "leaky:";
        return new LeakyBucketRateLimiter(scriptExecutor, prefix, keyNamingStrategy);
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimitAspect rateLimitAspect(SlidingWindowRateLimiter slidingWindow,
                                           FixedWindowRateLimiter fixedWindow,
                                           TokenBucketRateLimiter tokenBucket,
                                           LeakyBucketRateLimiter leakyBucket,
                                           ObjectProvider<RateLimitKeyResolver> keyResolverProvider,
                                           RedisPlusObserver observer,
                                           RedisPlusProperties properties) {
        return new RateLimitAspect(slidingWindow, fixedWindow, tokenBucket, leakyBucket,
                keyResolverProvider.getIfAvailable(),
                properties.getRatelimit().getTokenBucketRefillRate(), observer);
    }
}
