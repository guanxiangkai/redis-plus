package com.actomize.redis.plus.cache.spi;

/**
 * 缓存回源加载 SPI
 *
 * <p>L1+L2 缓存均未命中时，框架调用此接口从数据库/RPC 加载数据。
 * 返回 {@code null} 表示数据不存在，框架会缓存空值（Null Value）防穿透。
 *
 * @param <T> 值类型
 */
@FunctionalInterface
public interface CacheLoader<T> {
    /**
     * 加载数据。
     *
     * @param key 缓存 Key
     * @return 数据值；{@code null} 表示不存在
     */
    T load(String key);
}
