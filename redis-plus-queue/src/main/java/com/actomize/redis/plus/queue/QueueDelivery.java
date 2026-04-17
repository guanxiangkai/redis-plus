package com.actomize.redis.plus.queue;

/**
 * 队列投递结果。
 *
 * <p>用于统一同步拉取时的交付语义：
 * List 队列为“已出队、ack 为 no-op”，Stream 队列为“收到但需显式 ack”。
 *
 * @param <T> 消息类型
 */
public interface QueueDelivery<T> {

    /**
     * 当前消息内容。
     */
    T message();

    /**
     * 当前消息交付模式。
     */
    DeliveryMode mode();

    /**
     * 投递模式。
     */
    enum DeliveryMode {
        /**
         * 消息已从底层队列弹出，ack 仅表示本地处理确认。
         */
        ALREADY_DEQUEUED,
        /**
         * 消息仍处于待确认状态，需要显式 ack 才会从底层队列完成确认。
         */
        PENDING_ACKNOWLEDGMENT
    }

    /**
     * 确认消费完成。
     */
    void acknowledge();

    /**
     * 当前消息是否已确认。
     */
    boolean isAcknowledged();
}
