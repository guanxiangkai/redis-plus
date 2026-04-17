package com.actomize.redis.plus.queue.spi;

/**
 * 消息序列化/反序列化 SPI
 *
 * <p>定义消息在写入 Redis（String）与业务对象之间的转换契约。
 * 默认基于 Jackson JSON 实现，用户可注册自定义 Bean 替换。
 *
 * @param <T> 消息类型
 */
public interface MessageConverter<T> {

    /**
     * 将业务消息对象序列化为字符串（写入 Redis 时调用）。
     *
     * @param message 消息对象（不可为 null）
     * @return 序列化后的字符串
     */
    String serialize(T message);

    /**
     * 将字符串反序列化为业务消息对象（从 Redis 读取时调用）。
     *
     * @param raw         原始字符串
     * @param messageType 目标类型
     * @return 反序列化后的消息对象
     */
    T deserialize(String raw, Class<T> messageType);
}

