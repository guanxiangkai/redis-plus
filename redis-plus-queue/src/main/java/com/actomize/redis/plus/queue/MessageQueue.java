package com.actomize.redis.plus.queue;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * Redis 消息队列统一抽象
 *
 * <p>支持基于 Redis List（简单队列）和 Redis Stream（消费组模式）的两种实现。
 *
 * @param <T> 消息类型
 */
public interface MessageQueue<T> {

    /**
     * 发送消息。
     *
     * @param message 消息对象
     * @return 消息 ID
     */
    String send(T message);

    /**
     * 同步接收一条消息（阻塞等待）。
     *
     * @param timeout 最大等待时间；{@link Duration#ZERO} 表示非阻塞
     * @return 消息交付对象；超时则返回 {@code null}
     */
    QueueDelivery<T> receive(Duration timeout);

    /**
     * 注册异步消费者。
     *
     * @param consumer 消息处理回调
     */
    QueueSubscription subscribe(Consumer<T> consumer);

    /**
     * 获取队列名称。
     */
    String getQueueName();

    /**
     * 获取队列当前积压消息数量。
     */
    long size();

    /**
     * 回收因消费者崩溃而长期滞留在 PEL（Pending Entry List）中的消息并重新派发。
     *
     * <p>仅 Redis Stream 实现支持此操作；List 队列默认返回 {@code 0}。
     *
     * <p>使用场景：消费者进程异常退出后，其持有的未 ACK 消息不会自动转移。
     * 调用此方法可将空闲时长超过 {@code idleTime} 的消息认领到当前消费者并重新处理。
     *
     * @param idleTime 最短空闲时长阈值；超过此时长未 ACK 的消息才会被认领
     * @param consumer 重新派发时使用的消费者回调
     * @return 实际回收并派发的消息数量
     */
    default long reclaimPending(Duration idleTime, Consumer<T> consumer) {
        return 0L;
    }
}
