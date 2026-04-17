package com.actomize.redis.plus.queue;

import com.actomize.redis.plus.core.async.RedisPlusAsyncExecutor;
import com.actomize.redis.plus.core.observation.RedisPlusObserver;
import com.actomize.redis.plus.core.serializer.ValueSerializer;
import com.actomize.redis.plus.queue.impl.RedisListQueue;
import com.actomize.redis.plus.queue.impl.RedisStreamQueue;
import com.actomize.redis.plus.queue.spi.DeadLetterHandler;
import com.actomize.redis.plus.queue.spi.QueueRetryStrategy;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Redis 队列实例工厂，同时实现 {@link SmartLifecycle} 以支持 Spring 容器优雅停机。
 *
 * <p>工厂持有所有已创建队列实例的引用；容器关闭时自动调用各队列的 {@link RedisListQueue#stop()} /
 * {@link RedisStreamQueue#stop()}，防止后台消费线程在应用下线后继续运行。
 */
public class RedisQueueFactory implements SmartLifecycle {

    private final StringRedisTemplate redisTemplate;
    private final ValueSerializer serializer;
    private final String listKeyPrefix;
    private final String streamKeyPrefix;
    private final String defaultConsumerGroup;
    private final RedisPlusAsyncExecutor asyncExecutor;
    private final QueueRetryStrategy retryStrategy;
    private final DeadLetterHandler<Object> deadLetterHandler;
    private final Duration pollTimeout;
    private final int batchSize;
    private final RedisPlusObserver observer;
    private final boolean reclaimOnStart;
    private final Duration pendingReclaimIdleTime;
    private final long maxStreamLength;

    private final CopyOnWriteArrayList<RedisListQueue<?>> listQueues = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<RedisStreamQueue<?>> streamQueues = new CopyOnWriteArrayList<>();

    public RedisQueueFactory(StringRedisTemplate redisTemplate,
                             ValueSerializer serializer,
                             String listKeyPrefix,
                             String streamKeyPrefix,
                             String defaultConsumerGroup,
                             RedisPlusAsyncExecutor asyncExecutor,
                             QueueRetryStrategy retryStrategy,
                             DeadLetterHandler<Object> deadLetterHandler,
                             Duration pollTimeout,
                             int batchSize) {
        this(redisTemplate, serializer, listKeyPrefix, streamKeyPrefix, defaultConsumerGroup,
                asyncExecutor, retryStrategy, deadLetterHandler, pollTimeout, batchSize, RedisPlusObserver.noop(),
                false, Duration.ofMinutes(5), 0);
    }

    public RedisQueueFactory(StringRedisTemplate redisTemplate,
                             ValueSerializer serializer,
                             String listKeyPrefix,
                             String streamKeyPrefix,
                             String defaultConsumerGroup,
                             RedisPlusAsyncExecutor asyncExecutor,
                             QueueRetryStrategy retryStrategy,
                             DeadLetterHandler<Object> deadLetterHandler,
                             Duration pollTimeout,
                             int batchSize,
                             RedisPlusObserver observer) {
        this(redisTemplate, serializer, listKeyPrefix, streamKeyPrefix, defaultConsumerGroup,
                asyncExecutor, retryStrategy, deadLetterHandler, pollTimeout, batchSize, observer,
                false, Duration.ofMinutes(5), 0);
    }

    public RedisQueueFactory(StringRedisTemplate redisTemplate,
                             ValueSerializer serializer,
                             String listKeyPrefix,
                             String streamKeyPrefix,
                             String defaultConsumerGroup,
                             RedisPlusAsyncExecutor asyncExecutor,
                             QueueRetryStrategy retryStrategy,
                             DeadLetterHandler<Object> deadLetterHandler,
                             Duration pollTimeout,
                             int batchSize,
                             RedisPlusObserver observer,
                             boolean reclaimOnStart,
                             Duration pendingReclaimIdleTime) {
        this(redisTemplate, serializer, listKeyPrefix, streamKeyPrefix, defaultConsumerGroup,
                asyncExecutor, retryStrategy, deadLetterHandler, pollTimeout, batchSize, observer,
                reclaimOnStart, pendingReclaimIdleTime, 0);
    }

    public RedisQueueFactory(StringRedisTemplate redisTemplate,
                             ValueSerializer serializer,
                             String listKeyPrefix,
                             String streamKeyPrefix,
                             String defaultConsumerGroup,
                             RedisPlusAsyncExecutor asyncExecutor,
                             QueueRetryStrategy retryStrategy,
                             DeadLetterHandler<Object> deadLetterHandler,
                             Duration pollTimeout,
                             int batchSize,
                             RedisPlusObserver observer,
                             boolean reclaimOnStart,
                             Duration pendingReclaimIdleTime,
                             long maxStreamLength) {
        this.redisTemplate = redisTemplate;
        this.serializer = serializer;
        this.listKeyPrefix = listKeyPrefix;
        this.streamKeyPrefix = streamKeyPrefix;
        this.defaultConsumerGroup = defaultConsumerGroup;
        this.asyncExecutor = asyncExecutor;
        this.retryStrategy = retryStrategy;
        this.deadLetterHandler = deadLetterHandler;
        this.pollTimeout = pollTimeout;
        this.batchSize = batchSize;
        this.observer = observer;
        this.reclaimOnStart = reclaimOnStart;
        this.pendingReclaimIdleTime = pendingReclaimIdleTime != null ? pendingReclaimIdleTime : Duration.ofMinutes(5);
        this.maxStreamLength = maxStreamLength > 0 ? maxStreamLength : 0;
    }

    public <T> RedisListQueue<T> createListQueue(String queueName, Class<T> messageType) {
        RedisListQueue<T> queue = new RedisListQueue<>(queueName, listKeyPrefix, messageType,
                redisTemplate, serializer, asyncExecutor, retryStrategy, castDeadLetterHandler(), pollTimeout, observer);
        listQueues.add(queue);
        return queue;
    }

    public <T> RedisStreamQueue<T> createStreamQueue(String queueName, Class<T> messageType) {
        RedisStreamQueue<T> queue = new RedisStreamQueue<>(queueName, defaultConsumerGroup,
                streamKeyPrefix, messageType, redisTemplate, serializer, asyncExecutor,
                retryStrategy, castDeadLetterHandler(), pollTimeout, batchSize, observer,
                reclaimOnStart, pendingReclaimIdleTime, maxStreamLength);
        streamQueues.add(queue);
        return queue;
    }

    public <T> RedisStreamQueue<T> createStreamQueue(String queueName,
                                                     String consumerGroup,
                                                     Class<T> messageType) {
        RedisStreamQueue<T> queue = new RedisStreamQueue<>(queueName, consumerGroup,
                streamKeyPrefix, messageType, redisTemplate, serializer, asyncExecutor,
                retryStrategy, castDeadLetterHandler(), pollTimeout, batchSize, observer,
                reclaimOnStart, pendingReclaimIdleTime, maxStreamLength);
        streamQueues.add(queue);
        return queue;
    }

    // ── SmartLifecycle ────────────────────────────────────────────────────────

    /** 队列在调用 {@code subscribe()} 时自行启动，此处为空操作。 */
    @Override
    public void start() {
        // no-op: queues start when subscribe() is called
    }

    /** 容器关闭时停止所有已追踪队列。 */
    @Override
    public void stop() {
        listQueues.forEach(RedisListQueue::stop);
        streamQueues.forEach(RedisStreamQueue::stop);
    }

    @Override
    public boolean isRunning() {
        return listQueues.stream().anyMatch(RedisListQueue::isRunning)
                || streamQueues.stream().anyMatch(RedisStreamQueue::isRunning);
    }

    @SuppressWarnings("unchecked")
    private <T> DeadLetterHandler<T> castDeadLetterHandler() {
        return (DeadLetterHandler<T>) deadLetterHandler;
    }
}
