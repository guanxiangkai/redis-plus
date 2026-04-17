package com.actomize.redis.plus.core.serializer;

/**
 * Redis Plus 序列化 SPI
 *
 * <p>统一对象与字节数组之间的转换接口。框架内部不直接依赖具体序列化库，
 * 默认在 {@code redis-plus-starter} 中提供基于 Jackson 的实现；
 * 高性能场景可切换为 Kryo / Protostuff 等二进制序列化方案。
 *
 * @param <T> 序列化目标类型
 */
public interface RedisPlusSerializer<T> {

    /**
     * 将对象序列化为字节数组。
     *
     * @param value 待序列化对象，可为 {@code null}
     * @return 字节数组；若 {@code value} 为 {@code null} 则返回 {@code null}
     */
    byte[] serialize(T value);

    /**
     * 将字节数组反序列化为对象。
     *
     * @param bytes 字节数组，可为 {@code null}
     * @param type  目标类型
     * @return 反序列化对象；若 {@code bytes} 为 {@code null} 则返回 {@code null}
     */
    T deserialize(byte[] bytes, Class<T> type);
}

