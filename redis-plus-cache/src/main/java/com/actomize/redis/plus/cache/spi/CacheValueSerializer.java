package com.actomize.redis.plus.cache.spi;

import com.actomize.redis.plus.core.serializer.ValueSerializer;

/**
 * 缓存值序列化器 SPI
 *
 * <p>负责将缓存值对象序列化为字符串存入 Redis，以及从 Redis 字符串反序列化为目标对象。
 * 继承 {@link ValueSerializer} 以统一序列化抽象；
 * 默认实现由 {@code redis-plus-starter} 提供（基于 Jackson JSON）。
 */
public interface CacheValueSerializer extends ValueSerializer {

    /**
     * 将对象序列化为字符串。
     *
     * @param value 待序列化对象，不为 {@code null}
     * @return 序列化后的字符串
     */
    String serialize(Object value);

    /**
     * 将字符串反序列化为目标类型对象。
     *
     * @param data 序列化字符串
     * @param type 目标类型
     * @param <T>  目标类型泛型
     * @return 反序列化对象
     */
    <T> T deserialize(String data, Class<T> type);
}

