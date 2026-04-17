package com.actomize.redis.plus.cache.local;

import com.actomize.redis.plus.cache.spi.LocalCacheProvider;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 {@link ConcurrentHashMap} 的 L1 本地缓存兜底实现
 *
 * <p>不依赖 Caffeine，当 Caffeine 未在 classpath 时由 starter 自动使用此实现。
 * <b>注意：此实现不支持 TTL 过期，缓存条目永不自动失效，仅适用于开发/测试场景。
 * 生产环境强烈建议引入 Caffeine。</b>
 */
public class ConcurrentMapLocalCacheProvider implements LocalCacheProvider {

    private final ConcurrentHashMap<String, Object> store = new ConcurrentHashMap<>();

    @Override
    public Object get(String key) {
        return store.get(key);
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        store.put(key, value);
    }

    @Override
    public void evict(String key) {
        store.remove(key);
    }

    @Override
    public void clear() {
        store.clear();
    }

    @Override
    public void clearByPrefix(String keyPrefix) {
        store.keySet().removeIf(k -> k.startsWith(keyPrefix));
    }
}

