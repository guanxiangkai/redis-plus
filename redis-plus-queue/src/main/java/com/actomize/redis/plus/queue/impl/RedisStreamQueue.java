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
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 基于 Redis Stream 的消息队列实现（消费组模式）
 *
 * <p>特性：
 * <ul>
 *   <li>消费组（Consumer Group）模式：多消费者并发消费，每条消息仅被组内一个消费者处理</li>
 *   <li>ACK 确认：消费成功后执行 XACK，保证 at-least-once 语义</li>
 *   <li>消息持久化：Stream 消息持久化在 Redis，可回放（指定 ID 从历史重放）</li>
 *   <li>适合关键业务场景，如订单、支付、通知等</li>
 * </ul>
 *
 * <p>PEL 悬挂消息回收：
 * <ul>
 *   <li>当消费者进程异常退出后，其持有的未 ACK 消息会留在 PEL（Pending Entry List）中，不会自动转移。</li>
 *   <li>可调用 {@link #reclaimPending(Duration, java.util.function.Consumer)} 主动回收悬挂消息。</li>
 *   <li>也可通过 {@code redis-plus.queue.reclaim-on-start=true} 配置，让每次 {@code subscribe()} 启动时自动回收。</li>
 * </ul>
 *
 * <p>使用建议：
 * <ul>
 *   <li>消息体字段统一使用 "payload" key</li>
 *   <li>消费失败时抛出异常，框架不执行 ACK，消息保留在 PEL（Pending Entry List）</li>
 *   <li>建议配合 {@link com.actomize.redis.plus.queue.spi.QueueRetryStrategy}
 *       和 {@link com.actomize.redis.plus.queue.spi.DeadLetterHandler} 使用</li>
 * </ul>
 *
 * @param <T> 消息类型
 */
public class RedisStreamQueue<T> implements MessageQueue<T> {

    private static final Logger log = LoggerFactory.getLogger(RedisStreamQueue.class);
    private static final String KEY_PREFIX = "redis-plus:queue:stream:";
    private static final String PAYLOAD_FIELD = "payload";
    private static final String BOOTSTRAP_FIELD = "__redis_plus_bootstrap__";
    private static final DefaultRedisScript<Long> CONDITIONAL_TRIM_SCRIPT = new DefaultRedisScript<>("""
            local pending = redis.call('XPENDING', KEYS[1], ARGV[1])
            local pendingCount = tonumber(pending[1]) or 0
            if pendingCount > 0 then
                return 0
            end
            return redis.call('XTRIM', KEYS[1], 'MAXLEN', '~', ARGV[2])
            """, Long.class);

    private final String queueName;
    private final String streamKey;
    private final String consumerGroup;
    private final String consumerName;
    private final Class<T> messageType;
    private final StringRedisTemplate redisTemplate;
    private final ValueSerializer serializer;
    private final RedisPlusAsyncExecutor asyncExecutor;
    private final boolean ownsAsyncExecutor;
    private final QueueRetryStrategy retryStrategy;
    private final DeadLetterHandler<T> deadLetterHandler;
    private final Duration pollTimeout;
    private final int batchSize;
    private final RedisPlusObserver observer;
    private final boolean reclaimOnStart;
    private final Duration pendingReclaimIdleTime;
    /**
     * Stream 最大长度（近似裁剪）；0 表示不裁剪。
     * 每次 {@link #send} 后执行 {@code XTRIM MAXLEN ~ maxStreamLength}，
     * 防止 Stream 无限增长耗尽 Redis 内存。
     */
    private final long maxStreamLength;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Set<RedisPlusAsyncExecutor.Cancellable> scheduledRetries = ConcurrentHashMap.newKeySet();

    /**
     * @param queueName     队列名称（用于 Redis Key 和日志）
     * @param consumerGroup 消费组名称
     * @param messageType   消息类型
     * @param redisTemplate Spring Data Redis 模板
     * @param serializer    消息序列化器
     */
    public RedisStreamQueue(String queueName,
                            String consumerGroup,
                            Class<T> messageType,
                            StringRedisTemplate redisTemplate,
                            ValueSerializer serializer) {
        this(queueName, consumerGroup, KEY_PREFIX, messageType, redisTemplate, serializer,
                null, QueueRetryStrategy.noRetry(),
                DeadLetterHandler.logAndDiscard(), Duration.ofSeconds(2), 10, RedisPlusObserver.noop(),
                false, Duration.ofMinutes(5), 0);
    }

    public RedisStreamQueue(String queueName,
                            String consumerGroup,
                            String streamKeyPrefix,
                            Class<T> messageType,
                            StringRedisTemplate redisTemplate,
                            ValueSerializer serializer,
                            RedisPlusAsyncExecutor asyncExecutor,
                            QueueRetryStrategy retryStrategy,
                            DeadLetterHandler<T> deadLetterHandler,
                            Duration pollTimeout,
                            int batchSize) {
        this(queueName, consumerGroup, streamKeyPrefix, messageType, redisTemplate, serializer,
                asyncExecutor, retryStrategy, deadLetterHandler, pollTimeout, batchSize, RedisPlusObserver.noop(),
                false, Duration.ofMinutes(5), 0);
    }

    public RedisStreamQueue(String queueName,
                            String consumerGroup,
                            String streamKeyPrefix,
                            Class<T> messageType,
                            StringRedisTemplate redisTemplate,
                            ValueSerializer serializer,
                            RedisPlusAsyncExecutor asyncExecutor,
                            QueueRetryStrategy retryStrategy,
                            DeadLetterHandler<T> deadLetterHandler,
                            Duration pollTimeout,
                            int batchSize,
                            RedisPlusObserver observer) {
        this(queueName, consumerGroup, streamKeyPrefix, messageType, redisTemplate, serializer,
                asyncExecutor, retryStrategy, deadLetterHandler, pollTimeout, batchSize, observer,
                false, Duration.ofMinutes(5), 0);
    }

    public RedisStreamQueue(String queueName,
                            String consumerGroup,
                            String streamKeyPrefix,
                            Class<T> messageType,
                            StringRedisTemplate redisTemplate,
                            ValueSerializer serializer,
                            RedisPlusAsyncExecutor asyncExecutor,
                            QueueRetryStrategy retryStrategy,
                            DeadLetterHandler<T> deadLetterHandler,
                            Duration pollTimeout,
                            int batchSize,
                            RedisPlusObserver observer,
                            boolean reclaimOnStart,
                            Duration pendingReclaimIdleTime) {
        this(queueName, consumerGroup, streamKeyPrefix, messageType, redisTemplate, serializer,
                asyncExecutor, retryStrategy, deadLetterHandler, pollTimeout, batchSize, observer,
                reclaimOnStart, pendingReclaimIdleTime, 0);
    }

    public RedisStreamQueue(String queueName,
                            String consumerGroup,
                            String streamKeyPrefix,
                            Class<T> messageType,
                            StringRedisTemplate redisTemplate,
                            ValueSerializer serializer,
                            RedisPlusAsyncExecutor asyncExecutor,
                            QueueRetryStrategy retryStrategy,
                            DeadLetterHandler<T> deadLetterHandler,
                            Duration pollTimeout,
                            int batchSize,
                            RedisPlusObserver observer,
                            boolean reclaimOnStart,
                            Duration pendingReclaimIdleTime,
                            long maxStreamLength) {
        this.queueName = queueName;
        this.streamKey = streamKeyPrefix + queueName;
        this.consumerGroup = consumerGroup;
        this.consumerName = "consumer-" + UUID.randomUUID().toString().substring(0, 8);
        this.messageType = messageType;
        this.redisTemplate = redisTemplate;
        this.serializer = serializer;
        if (asyncExecutor == null) {
            this.asyncExecutor = new DefaultRedisPlusAsyncExecutor("redis-plus-stream-queue", 1);
            this.ownsAsyncExecutor = true;
        } else {
            this.asyncExecutor = asyncExecutor;
            this.ownsAsyncExecutor = false;
        }
        this.retryStrategy = retryStrategy;
        this.deadLetterHandler = deadLetterHandler;
        this.pollTimeout = pollTimeout;
        this.batchSize = batchSize;
        this.observer = observer != null ? observer : RedisPlusObserver.noop();
        this.reclaimOnStart = reclaimOnStart;
        this.pendingReclaimIdleTime = pendingReclaimIdleTime != null ? pendingReclaimIdleTime : Duration.ofMinutes(5);
        this.maxStreamLength = maxStreamLength > 0 ? maxStreamLength : 0;
        ensureGroupExists();
    }

    // ── MessageQueue API ──────────────────────────────────────────────

    @Override
    public String send(T message) {
        try {
            return observer.observe(RedisPlusObservationType.QUEUE_SEND,
                    Map.of("queue.type", "stream"),
                    Map.of("queue.name", queueName),
                    () -> {
                        Map<String, String> fields = new HashMap<>();
                        fields.put(PAYLOAD_FIELD, serializer.serialize(message));
                        RecordId id = redisTemplate.opsForStream()
                                .add(MapRecord.create(streamKey, fields));
                        if (maxStreamLength > 0) {
                            trimStreamSafely();
                        }
                        return id != null ? id.getValue() : "";
                    });
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException("queue send failed", t);
        }
    }

    @Override
    public QueueDelivery<T> receive(Duration timeout) {
        Duration effectiveTimeout = timeout == null ? Duration.ZERO : timeout;
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                org.springframework.data.redis.connection.stream.Consumer.from(consumerGroup, consumerName),
                effectiveTimeout.isZero()
                        ? StreamReadOptions.empty().count(1)
                        : StreamReadOptions.empty().count(1).block(effectiveTimeout),
                StreamOffset.create(streamKey, ReadOffset.lastConsumed()));

        if (records == null || records.isEmpty()) {
            return null;
        }
        return toDelivery(records.get(0));
    }

    @Override
    public QueueSubscription subscribe(Consumer<T> consumer) {
        if (!running.compareAndSet(false, true)) {
            log.warn("[redis-plus] Stream 队列 {} 已有消费者在运行", queueName);
            return subscription();
        }
        asyncExecutor.execute("queue-stream-" + queueName, () -> {
            log.info("[redis-plus] Stream 队列消费者启动：group={}, consumer={}, stream={}",
                    consumerGroup, consumerName, streamKey);
            if (reclaimOnStart) {
                long reclaimed = reclaimPending(pendingReclaimIdleTime, consumer);
                if (reclaimed > 0) {
                    log.info("[redis-plus] 启动时回收 PEL 悬挂消息：queue={}, count={}", queueName, reclaimed);
                }
            }
            while (running.get()) {
                try {
                    List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                            org.springframework.data.redis.connection.stream.Consumer.from(consumerGroup, consumerName),
                            StreamReadOptions.empty().count(batchSize).block(pollTimeout),
                            StreamOffset.create(streamKey, ReadOffset.lastConsumed()));

                    if (records != null && !records.isEmpty()) {
                        for (MapRecord<String, Object, Object> record : records) {
                            processRecord(record, consumer);
                        }
                    }
                } catch (Exception e) {
                    log.error("[redis-plus] Stream 消费者异常，stream={}", streamKey, e);
                }
            }
            log.info("[redis-plus] Stream 队列消费者停止：{}", queueName);
        });
        return subscription();
    }

    @Override
    public String getQueueName() {
        return queueName;
    }

    @Override
    public long size() {
        Long len = redisTemplate.opsForStream().size(streamKey);
        return len != null ? len : 0L;
    }

    // ── 公共工具方法 ──────────────────────────────────────────────────

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

    /**（Pending Entry List）大小，用于监控积压。
     */
    public long pendingCount() {
        try {
            PendingMessagesSummary summary = redisTemplate.opsForStream()
                    .pending(streamKey, consumerGroup);
            return summary != null ? summary.getTotalPendingMessages() : 0L;
        } catch (Exception e) {
            log.warn("[redis-plus] 获取 PEL 失败，stream={}", streamKey, e);
            return 0L;
        }
    }

    /**
     * 回收因消费者崩溃而长期滞留在 PEL 中的消息并重新派发（XCLAIM 语义）。
     *
     * <p>实现原理：
     * <ol>
     *   <li>通过 {@code XPENDING} 查询整个消费组中所有 pending 消息；</li>
     *   <li>筛选空闲时长 &gt;= {@code idleTime} 的条目；</li>
     *   <li>通过 {@code XCLAIM} 将其转移到当前消费者名下并重新派发；</li>
     *   <li>成功消费后执行 XACK；失败时走重试/死信策略。</li>
     * </ol>
     *
     * @param idleTime 最短空闲时长阈值
     * @param consumer 重新派发时使用的消费者回调
     * @return 实际回收并派发的消息数量
     */
    @Override
    public long reclaimPending(Duration idleTime, java.util.function.Consumer<T> consumer) {
        if (idleTime == null || idleTime.isNegative() || idleTime.isZero()) {
            return 0L;
        }
        long claimed = 0L;
        try {
            PendingMessages pendingMessages = redisTemplate.opsForStream()
                    .pending(streamKey, consumerGroup, Range.unbounded(), batchSize);
            if (pendingMessages == null || !pendingMessages.iterator().hasNext()) {
                return 0L;
            }
            List<RecordId> toReclaim = new ArrayList<>();
            for (PendingMessage pm : pendingMessages) {
                Duration idle = pm.getElapsedTimeSinceLastDelivery();
                if (idle != null && idle.compareTo(idleTime) >= 0) {
                    toReclaim.add(pm.getId());
                }
            }
            if (toReclaim.isEmpty()) {
                return 0L;
            }
            List<MapRecord<String, Object, Object>> reclaimedRecords = redisTemplate.opsForStream()
                    .claim(streamKey, consumerGroup, consumerName, idleTime,
                            toReclaim.toArray(new RecordId[0]));
            if (reclaimedRecords != null) {
                for (MapRecord<String, Object, Object> record : reclaimedRecords) {
                    processRecord(record, consumer);
                    claimed++;
                }
            }
        } catch (Exception e) {
            log.warn("[redis-plus] PEL 回收失败，stream={}: {}", streamKey, e.getMessage());
        }
        return claimed;
    }

    // ── 内部工具 ──────────────────────────────────────────────────────

    private void acknowledge(RecordId id) {
        Long acknowledged = redisTemplate.opsForStream().acknowledge(streamKey, consumerGroup, id);
        if (acknowledged == null || acknowledged <= 0) {
            throw new IllegalStateException("ACK failed, id=" + id + ", stream=" + streamKey);
        }
    }

    /**
     * ACK without propagating failure — used after dead-lettering so the message always leaves PEL.
     */
    private void ackQuietly(RecordId id) {
        try {
            acknowledge(id);
        } catch (Exception e) {
            log.warn("[redis-plus] 死信 ACK 失败，消息留在 PEL，id={}, stream={}", id, streamKey, e);
        }
    }

    /**
     * 确保消费组存在（不存在时自动创建，从 Stream 起始位置消费全部消息）。
     *
     * <p>使用 {@link ReadOffset#from(String) ReadOffset.from("0-0")} 而非
     * {@code ReadOffset.latest()}，以保证 at-least-once 语义：
     * 若生产者在消费组创建之前已写入消息，这些历史消息也会被正确投递。
     * 消费组已存在时（BUSYGROUP）忽略此偏移，不重置已记录的消费位置。
     *
     * <p>非 {@code BUSYGROUP} 错误（如 Redis 不可用、key 类型错误等）会抛出
     * {@link IllegalStateException}，让构造器快速失败，避免创建出行为异常的"假队列"。
     */
    private void ensureGroupExists() {
        RecordId bootstrapId = null;
        try {
            redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.from("0-0"), consumerGroup);
            log.debug("[redis-plus] 消费组已创建：group={}, stream={}", consumerGroup, streamKey);
        } catch (Exception e) {
            // 消费组已存在时 Redis 抛出 BUSYGROUP 异常，忽略即可
            if (isBusyGroupError(e)) {
                return;
            }
            if (!isMissingStreamError(e)) {
                throw groupCreationFailure(e);
            }
            try {
                bootstrapId = redisTemplate.opsForStream()
                        .add(MapRecord.create(streamKey, Map.of(BOOTSTRAP_FIELD, consumerGroup)));
                redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.from("0-0"), consumerGroup);
                log.debug("[redis-plus] 通过 bootstrap stream 创建消费组成功：group={}, stream={}",
                        consumerGroup, streamKey);
            } catch (Exception retryEx) {
                if (!isBusyGroupError(retryEx)) {
                    throw groupCreationFailure(retryEx);
                }
            } finally {
                cleanupBootstrapRecord(bootstrapId);
            }
        }
    }

    private void trimStreamSafely() {
        Long trimmed = redisTemplate.execute(CONDITIONAL_TRIM_SCRIPT,
                Collections.singletonList(streamKey),
                consumerGroup,
                String.valueOf(maxStreamLength));
        if (trimmed != null && trimmed == 0L) {
            log.debug("[redis-plus] PEL 中存在挂起消息，跳过裁剪以保护未 ACK 的消息，stream={}", streamKey);
        }
    }

    private void cleanupBootstrapRecord(RecordId bootstrapId) {
        if (bootstrapId == null) {
            return;
        }
        try {
            redisTemplate.opsForStream().delete(streamKey, bootstrapId);
        } catch (Exception deleteEx) {
            log.warn("[redis-plus] bootstrap stream 记录清理失败，stream={}, id={}",
                    streamKey, bootstrapId, deleteEx);
        }
    }

    private boolean isBusyGroupError(Exception e) {
        String message = e.getMessage();
        return message != null && message.contains("BUSYGROUP");
    }

    private boolean isMissingStreamError(Exception e) {
        String message = e.getMessage();
        return message != null
                && (message.contains("requires the key to exist")
                || message.contains("no such key")
                || message.contains("NOGROUP"));
    }

    private IllegalStateException groupCreationFailure(Exception e) {
        return new IllegalStateException(
                String.format("[redis-plus] 创建消费组失败，group=%s, stream=%s: %s",
                        consumerGroup, streamKey, e.getMessage()), e);
    }

    private void processRecord(MapRecord<String, Object, Object> record, Consumer<T> consumer) {
        try {
            QueueDelivery<T> delivery = toDelivery(record);
            if (delivery == null) {
                return;
            }
            dispatchWithRetry(record.getId(), delivery.message(), consumer, 1);
        } catch (Exception e) {
            log.error("[redis-plus] Stream 消费异常，id={}, stream={}",
                    record.getId(), streamKey, e);
        }
    }

    private QueueDelivery<T> toDelivery(MapRecord<String, Object, Object> record) {
        Object payload = record.getValue().get(PAYLOAD_FIELD);
        if (payload == null) {
            acknowledge(record.getId());
            return null;
        }
        T message = serializer.deserialize(payload.toString(), messageType);
        return new StreamQueueDelivery(record.getId(), message);
    }

    private void dispatchWithRetry(RecordId recordId, T message, Consumer<T> consumer, int attempt) {
        // Step 1: invoke consumer — only consumer exceptions drive retry/DLQ logic
        try {
            observeConsume(message, consumer);
        } catch (Exception consumerEx) {
            Duration nextDelay = retryStrategy.nextDelay(attempt, consumerEx);
            if (nextDelay == null) {
                // Retries exhausted → dead-letter then ACK so message leaves PEL
                deadLetterHandler.handle(queueName, message, consumerEx);
                ackQuietly(recordId);
                return;
            }
            scheduleRetry("queue-stream-retry-" + queueName, nextDelay,
                    () -> dispatchWithRetry(recordId, message, consumer, attempt + 1));
            return;
        }

        // Step 2: consumer succeeded — ACK is a separate concern
        // If ACK fails (e.g. Redis transient error) we log a warning and leave the
        // message in PEL so reclaimPending() can recover it later.  We must NOT
        // retry the consumer, because the business operation already completed.
        try {
            acknowledge(recordId);
        } catch (Exception ackEx) {
            log.warn("[redis-plus] Stream ACK 失败，消息已成功消费但留在 PEL，id={}, stream={}; 可通过 reclaimPending() 回收",
                    recordId, streamKey, ackEx);
        }
    }

    private void observeConsume(T message, Consumer<T> consumer) throws Exception {
        try {
            observer.observe(RedisPlusObservationType.QUEUE_CONSUME,
                    Map.of("queue.type", "stream"),
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

    private class StreamQueueDelivery implements QueueDelivery<T> {

        private final RecordId recordId;
        private final T message;
        private final AtomicBoolean acknowledged = new AtomicBoolean(false);

        private StreamQueueDelivery(RecordId recordId, T message) {
            this.recordId = recordId;
            this.message = message;
        }

        @Override
        public T message() {
            return message;
        }

        @Override
        public DeliveryMode mode() {
            return DeliveryMode.PENDING_ACKNOWLEDGMENT;
        }

        @Override
        public void acknowledge() {
            if (acknowledged.compareAndSet(false, true)) {
                RedisStreamQueue.this.acknowledge(recordId);
            }
        }

        @Override
        public boolean isAcknowledged() {
            return acknowledged.get();
        }
    }

    private QueueSubscription subscription() {
        return new QueueSubscription() {
            @Override
            public void stop() {
                RedisStreamQueue.this.stop();
            }

            @Override
            public boolean isRunning() {
                return RedisStreamQueue.this.isRunning();
            }
        };
    }
}
