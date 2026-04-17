package com.actomize.redis.plus.queue.impl;

import com.actomize.redis.plus.core.async.DefaultRedisPlusAsyncExecutor;
import com.actomize.redis.plus.core.async.RedisPlusAsyncExecutor;
import com.actomize.redis.plus.core.observation.RedisPlusObservationType;
import com.actomize.redis.plus.core.observation.RedisPlusObserver;
import com.actomize.redis.plus.core.serializer.ValueSerializer;
import com.actomize.redis.plus.queue.MessageQueue;
import com.actomize.redis.plus.queue.QueueDelivery;
import com.actomize.redis.plus.queue.QueueSubscription;
import com.actomize.redis.plus.queue.spi.DeadLetterHandler;
import com.actomize.redis.plus.queue.spi.QueueRetryStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 基于 Redis List（LPUSH / BRPOP）的简单消息队列实现
 *
 * <p>特性：
 * <ul>
 *   <li>FIFO 顺序（LPUSH 入队尾，BRPOP 出队头）</li>
 *   <li>不支持 ACK 确认（消息弹出即消费完成）</li>
 *   <li>适合非关键路径、容忍丢失的场景（如日志异步处理）</li>
 *   <li>关键业务建议使用 Redis Stream 实现</li>
 * </ul>
 *
 * @param <T> 消息类型
 */
public class RedisListQueue<T> implements MessageQueue<T> {

    private static final Logger log = LoggerFactory.getLogger(RedisListQueue.class);
    private static final String KEY_PREFIX = "redis-plus:queue:list:";

    private final String queueName;
    private final String redisKey;
    private final Class<T> messageType;
    private final StringRedisTemplate redisTemplate;
    private final ValueSerializer serializer;
    private final RedisPlusAsyncExecutor asyncExecutor;
    private final boolean ownsAsyncExecutor;
    private final QueueRetryStrategy retryStrategy;
    private final DeadLetterHandler<T> deadLetterHandler;
    private final Duration pollTimeout;
    private final RedisPlusObserver observer;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Set<RedisPlusAsyncExecutor.Cancellable> scheduledRetries = ConcurrentHashMap.newKeySet();

    public RedisListQueue(String queueName,
                          Class<T> messageType,
                          StringRedisTemplate redisTemplate,
                          ValueSerializer serializer) {
        this(queueName, KEY_PREFIX, messageType, redisTemplate, serializer,
                null, QueueRetryStrategy.noRetry(),
                DeadLetterHandler.logAndDiscard(), Duration.ofSeconds(2), RedisPlusObserver.noop());
    }

    public RedisListQueue(String queueName,
                          String keyPrefix,
                          Class<T> messageType,
                          StringRedisTemplate redisTemplate,
                          ValueSerializer serializer,
                          RedisPlusAsyncExecutor asyncExecutor,
                          QueueRetryStrategy retryStrategy,
                          DeadLetterHandler<T> deadLetterHandler,
                          Duration pollTimeout) {
        this(queueName, keyPrefix, messageType, redisTemplate, serializer, asyncExecutor,
                retryStrategy, deadLetterHandler, pollTimeout, RedisPlusObserver.noop());
    }

    public RedisListQueue(String queueName,
                          String keyPrefix,
                          Class<T> messageType,
                          StringRedisTemplate redisTemplate,
                          ValueSerializer serializer,
                          RedisPlusAsyncExecutor asyncExecutor,
                          QueueRetryStrategy retryStrategy,
                          DeadLetterHandler<T> deadLetterHandler,
                          Duration pollTimeout,
                          RedisPlusObserver observer) {
        this.queueName = queueName;
        this.redisKey = keyPrefix + queueName;
        this.messageType = messageType;
        this.redisTemplate = redisTemplate;
        this.serializer = serializer;
        if (asyncExecutor == null) {
            this.asyncExecutor = new DefaultRedisPlusAsyncExecutor("redis-plus-list-queue", 1);
            this.ownsAsyncExecutor = true;
        } else {
            this.asyncExecutor = asyncExecutor;
            this.ownsAsyncExecutor = false;
        }
        this.retryStrategy = retryStrategy;
        this.deadLetterHandler = deadLetterHandler;
        this.pollTimeout = pollTimeout;
        this.observer = observer != null ? observer : RedisPlusObserver.noop();
    }

    @Override
    public String send(T message) {
        try {
            return observer.observe(RedisPlusObservationType.QUEUE_SEND,
                    Map.of("queue.type", "list"),
                    Map.of("queue.name", queueName),
                    () -> {
                        String json = serializer.serialize(message);
                        String msgId = UUID.randomUUID().toString();
                        redisTemplate.opsForList().leftPush(redisKey, msgId + ":" + json);
                        return msgId;
                    });
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException("queue send failed", t);
        }
    }

    @Override
    public QueueDelivery<T> receive(Duration timeout) {
        String raw;
        if (timeout.isZero()) {
            raw = redisTemplate.opsForList().rightPop(redisKey);
        } else {
            // 使用 rightPop 带超时参数（底层执行 BRPOP），避免 pipeline 中使用阻塞命令的反模式
            raw = redisTemplate.opsForList().rightPop(redisKey, timeout);
        }
        if (raw == null) return null;
        return toDelivery(raw);
    }

    @Override
    public QueueSubscription subscribe(Consumer<T> consumer) {
        if (!running.compareAndSet(false, true)) {
            log.warn("[redis-plus] 消息队列 {} 已有消费者在运行", queueName);
            return subscription();
        }
        asyncExecutor.execute("queue-list-" + queueName, () -> {
            log.info("[redis-plus] 消息队列消费者启动：{}", queueName);
            while (running.get()) {
                try {
                    QueueDelivery<T> delivery = receive(pollTimeout);
                    if (delivery != null) {
                        dispatchWithRetry(delivery.message(), consumer, 1);
                    }
                } catch (Exception e) {
                    log.error("[redis-plus] 消息消费异常，queue={}", queueName, e);
                }
            }
            log.info("[redis-plus] 消息队列消费者停止：{}", queueName);
        });
        return subscription();
    }

    /**
     * 停止消费者（优雅关闭）。
     */
    public void stop() {
        running.set(false);
        scheduledRetries.forEach(RedisPlusAsyncExecutor.Cancellable::cancel);
        scheduledRetries.clear();
        if (ownsAsyncExecutor) {
            try {
                asyncExecutor.close();
            } catch (Exception e) {
                log.warn("[redis-plus] 关闭异步执行器失败，queue={}", queueName, e);
            }
        }
    }

    /**
     * 返回消费者是否正在运行。
     */
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public String getQueueName() {
        return queueName;
    }

    @Override
    public long size() {
        Long len = redisTemplate.opsForList().size(redisKey);
        return len != null ? len : 0L;
    }

    private QueueDelivery<T> toDelivery(String raw) {
        int colonIdx = raw.indexOf(':');
        String json = colonIdx >= 0 ? raw.substring(colonIdx + 1) : raw;
        T message = serializer.deserialize(json, messageType);
        return new QueueDelivery<>() {
            @Override
            public T message() {
                return message;
            }

            @Override
            public DeliveryMode mode() {
                return DeliveryMode.ALREADY_DEQUEUED;
            }

            @Override
            public void acknowledge() {
                // Redis List 在弹出时即完成消费，保留该 no-op 以统一同步接收 API。
            }

            @Override
            public boolean isAcknowledged() {
                return true;
            }
        };
    }

    private void dispatchWithRetry(T message, Consumer<T> consumer, int attempt) {
        try {
            observeConsume(message, consumer);
        } catch (Exception ex) {
            Duration nextDelay = retryStrategy.nextDelay(attempt, ex);
            if (nextDelay == null) {
                deadLetterHandler.handle(queueName, message, ex);
                return;
            }
            scheduleRetry("queue-list-retry-" + queueName, nextDelay,
                    () -> dispatchWithRetry(message, consumer, attempt + 1));
        }
    }

    private void observeConsume(T message, Consumer<T> consumer) throws Exception {
        try {
            observer.observe(RedisPlusObservationType.QUEUE_CONSUME,
                    Map.of("queue.type", "list"),
                    Map.of("queue.name", queueName),
                    () -> {
                        consumer.accept(message);
                        return null;
                    });
        } catch (Exception e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException("queue consume failed", t);
        }
    }

    private void scheduleRetry(String taskName, Duration delay, Runnable task) {
        if (!running.get()) {
            return;
        }
        if (delay == null || delay.isZero() || delay.isNegative()) {
            asyncExecutor.execute(taskName, () -> {
                if (running.get()) {
                    task.run();
                }
            });
            return;
        }
        AtomicReference<RedisPlusAsyncExecutor.Cancellable> ref = new AtomicReference<>();
        RedisPlusAsyncExecutor.Cancellable cancellable = asyncExecutor.schedule(taskName, delay, () -> {
            RedisPlusAsyncExecutor.Cancellable current = ref.getAndSet(null);
            if (current != null) {
                scheduledRetries.remove(current);
            }
            if (running.get()) {
                task.run();
            }
        });
        ref.set(cancellable);
        scheduledRetries.add(cancellable);
    }

    private QueueSubscription subscription() {
        return new QueueSubscription() {
            @Override
            public void stop() {
                RedisListQueue.this.stop();
            }

            @Override
            public boolean isRunning() {
                return RedisListQueue.this.isRunning();
            }
        };
    }
}
