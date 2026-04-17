package com.actomize.redis.plus.core.serializer;

/**
 * 通用字符串值序列化器 SPI
 *
 * <p>将对象序列化为字符串并支持按类型反序列化，适用于缓存存储、幂等结果保存等场景。
 * 默认实现由 {@code redis-plus-starter} 提供（基于 Jackson JSON）。
 */
public interface ValueSerializer {

    /**
     * 将对象序列化为字符串。
     *
     * @param value 待序列化对象，不应为 {@code null}
     * @return 序列化后的字符串（通常为 JSON）
     */
    String serialize(Object value);

    /**
     * 将字符串反序列化为目标类型。
     *
     * @param data 序列化字符串
     * @param type 目标类型
     * @param <T>  目标类型泛型
     * @return 反序列化对象
     */
    <T> T deserialize(String data, Class<T> type);
}

