package com.actomize.redis.plus.queue.spi;

/**
 * 死信处理 SPI
 *
 * <p>当消息重试次数耗尽或被判定为不可重试时，框架调用此接口进行死信处理。
 * 常见实现：写入死信队列、告警、丢弃并记录日志等。
 *
 * @param <T> 消息类型
 */
@FunctionalInterface
public interface DeadLetterHandler<T> {

    /**
     * 日志记录实现：将死信消息以 ERROR 级别打印并丢弃。
     */
    @SuppressWarnings("unchecked")
    static <T> DeadLetterHandler<T> logAndDiscard() {
        org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DeadLetterHandler.class);
        return (queueName, message, cause) ->
                log.error("[redis-plus] 死信消息，queue={}, message={}", queueName, message, cause);
    }

    // ── 内置工厂 ─────────────────────────────────────────────────────

    /**
     * 处理死信消息。
     *
     * @param queueName 来源队列名称
     * @param message   死信消息对象
     * @param cause     导致死信的最终异常
     */
    void handle(String queueName, T message, Exception cause);
}

