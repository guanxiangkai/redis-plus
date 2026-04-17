package com.actomize.redis.plus.enhance.batch;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 批量缓存操作接口
 *
 * <p>通过 Redis Pipeline / MGET / MSET 等批量命令减少网络往返，
 * 适用于批量查询、批量写入、批量失效场景。
 */
public interface BatchCacheOperations {

    /**
     * 批量查询缓存（未命中的 Key 返回 {@code null} 值）。
     *
     * @param cacheName 缓存名
     * @param keys      Key 列表
     * @param type      值类型
     * @param <T>       值泛型
     * @return Key → 值的映射；不存在的 Key 其值为 {@code null}
     */
    <T> Map<String, T> multiGet(String cacheName, List<String> keys, Class<T> type);

    /**
     * 批量写入缓存（同时写 L1 + L2）。
     *
     * @param cacheName 缓存名
     * @param entries   Key → 值的映射
     * @param ttl       统一 TTL
     */
    <T> void multiSet(String cacheName, Map<String, T> entries, Duration ttl);

    /**
     * 批量失效缓存（同时删除 L1 + L2）。
     *
     * @param cacheName 缓存名
     * @param keys      Key 列表
     */
    void multiEvict(String cacheName, List<String> keys);
}

