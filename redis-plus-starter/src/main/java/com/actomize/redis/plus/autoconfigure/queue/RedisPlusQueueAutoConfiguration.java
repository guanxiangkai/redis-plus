package com.actomize.redis.plus.autoconfigure.queue;

import com.actomize.redis.plus.autoconfigure.cache.RedisPlusCacheAutoConfiguration;
import com.actomize.redis.plus.autoconfigure.governance.RedisPlusGovernanceAutoConfiguration;
import com.actomize.redis.plus.autoconfigure.properties.RedisPlusProperties;
import com.actomize.redis.plus.core.async.RedisPlusAsyncExecutor;
import com.actomize.redis.plus.core.observation.RedisPlusObserver;
import com.actomize.redis.plus.core.serializer.ValueSerializer;
import com.actomize.redis.plus.queue.RedisQueueFactory;
import com.actomize.redis.plus.queue.spi.DeadLetterHandler;
import com.actomize.redis.plus.queue.spi.QueueRetryStrategy;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 队列能力自动装配。
 */
@AutoConfiguration(after = {RedisPlusCacheAutoConfiguration.class, RedisPlusGovernanceAutoConfiguration.class})
@ConditionalOnClass(StringRedisTemplate.class)
@EnableConfigurationProperties(RedisPlusProperties.class)
public class RedisPlusQueueAutoConfiguration {

    @Bean("redisQueueFactory")
    @ConditionalOnMissingBean(RedisQueueFactory.class)
    public RedisQueueFactory redisQueueFactory(StringRedisTemplate redisTemplate,
                                               ValueSerializer serializer,
                                               RedisPlusAsyncExecutor asyncExecutor,
                                               RedisPlusObserver observer,
                                               RedisPlusProperties properties) {
        RedisPlusProperties.QueueProperties q = properties.getQueue();
        String base = normalizePrefix(q.getKeyPrefix());
        return new RedisQueueFactory(
                redisTemplate, serializer,
                base + "list:",
                base + "stream:",
                q.getDefaultConsumerGroup(),
                asyncExecutor,
                QueueRetryStrategy.fixed(java.time.Duration.ofSeconds(1), q.getMaxRetryAttempts()),
                DeadLetterHandler.logAndDiscard(),
                q.getPollTimeout(),
                q.getBatchSize(),
                observer,
                q.isReclaimOnStart(),
                q.getPendingReclaimIdleTime(),
                q.getMaxStreamLength());
    }

    private static String normalizePrefix(String value) {
        if (value == null || value.isBlank()) {
            return "redis-plus:queue:";
        }
        return value.endsWith(":") ? value : value + ":";
    }
}
