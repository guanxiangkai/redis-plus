package com.actomize.redis.plus.enhance.batch.impl;

import com.actomize.redis.plus.cache.ThreeLevelCacheTemplate;
import com.actomize.redis.plus.cache.spi.CacheValueSerializer;
import com.actomize.redis.plus.cache.spi.LocalCacheProvider;
import com.actomize.redis.plus.enhance.batch.BatchCacheOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 Redis Pipeline + MGET 的批量缓存操作实现
 *
 * <p>读操作使用 MGET 一次获取多个 Key，减少网络往返。
 * 写操作使用 Pipeline 批量 SET，避免逐个调用。
 */
public class RedisBatchCacheOperations implements BatchCacheOperations {

    private final ThreeLevelCacheTemplate cacheTemplate;
    private final LocalCacheProvider l1;
    private final StringRedisTemplate redisTemplate;
    private final CacheValueSerializer serializer;

    public RedisBatchCacheOperations(ThreeLevelCacheTemplate cacheTemplate,
                                     LocalCacheProvider l1,
                                     StringRedisTemplate redisTemplate,
                                     CacheValueSerializer serializer) {
        this.cacheTemplate = cacheTemplate;
        this.l1 = l1;
        this.redisTemplate = redisTemplate;
        this.serializer = serializer;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Map<String, T> multiGet(String cacheName, List<String> keys, Class<T> type) {
        Map<String, T> result = new LinkedHashMap<>(keys.size());

        // ── 先检查 L1 ──
        List<String> l2Keys = new ArrayList<>();
        List<String> fullKeys = new ArrayList<>();

        for (String key : keys) {
            String fullKey = cacheTemplate.buildCacheKey(cacheName, key);
            Object l1Val = l1.get(fullKey);
            if (l1Val != null) {
                result.put(key, cacheTemplate.isNullMarker(l1Val) ? null : (T) l1Val);
            } else {
                l2Keys.add(key);
                fullKeys.add(fullKey);
            }
        }

        if (l2Keys.isEmpty()) return result;

        // ── 批量查询 L2（MGET）──
        List<String> l2Vals = redisTemplate.opsForValue().multiGet(fullKeys);
        if (l2Vals != null) {
            for (int i = 0; i < l2Keys.size(); i++) {
                String key = l2Keys.get(i);
                String fullKey = fullKeys.get(i);
                String raw = l2Vals.get(i);
                if (raw == null) {
                    result.put(key, null);
                } else if (cacheTemplate.isNullValue(raw)) {
                    cacheTemplate.putNullMarker(cacheName, key, Duration.ofMinutes(5));
                    result.put(key, null);
                } else {
                    T deserialized = serializer.deserialize(raw, type);
                    l1.put(fullKey, deserialized, Duration.ofMinutes(5));
                    result.put(key, deserialized);
                }
            }
        }

        return result;
    }

    @Override
    public <T> void multiSet(String cacheName, Map<String, T> entries, Duration ttl) {
        // ── Pipeline 批量写 L2 ──
        redisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            entries.forEach((key, value) -> {
                String fullKey = cacheTemplate.buildCacheKey(cacheName, key);
                byte[] rawKey = redisTemplate.getStringSerializer().serialize(fullKey);
                byte[] rawVal = redisTemplate.getStringSerializer().serialize(serializer.serialize(value));
                if (rawKey != null && rawVal != null) {
                    connection.stringCommands().setEx(rawKey, ttl.getSeconds(), rawVal);
                }
            });
            return null;
        });

        // ── 同步写 L1 ──
        entries.forEach((key, value) -> l1.put(cacheTemplate.buildCacheKey(cacheName, key), value, ttl));
    }

    @Override
    public void multiEvict(String cacheName, List<String> keys) {
        List<String> fullKeys = keys.stream()
                .map(k -> cacheTemplate.buildCacheKey(cacheName, k))
                .toList();

        // L1 逐条删除
        fullKeys.forEach(l1::evict);

        // L2 批量删除
        redisTemplate.delete(fullKeys);
    }
}

