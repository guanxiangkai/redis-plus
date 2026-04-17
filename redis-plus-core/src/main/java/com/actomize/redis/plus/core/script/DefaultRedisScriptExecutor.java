package com.actomize.redis.plus.core.script;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认 Lua 脚本执行器实现
 *
 * <p>内部按 {@code resultType + scriptSha256} 缓存 {@link DefaultRedisScript} 实例，
 * 确保同一脚本复用同一对象，使 Spring Data Redis 的 EVALSHA 缓存（SHA1 → 服务端脚本缓存）
 * 持续生效，避免每次调用都重新上传脚本体。
 *
 * <p>缓存键采用脚本内容的 SHA-256 摘要，消除 {@code String.hashCode()} 的 32 位碰撞风险。
 */
public class DefaultRedisScriptExecutor implements RedisScriptExecutor {

    private final StringRedisTemplate redisTemplate;
    private final ConcurrentHashMap<String, DefaultRedisScript<?>> scriptCache = new ConcurrentHashMap<>();

    public DefaultRedisScriptExecutor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T execute(String script, Class<T> resultType, List<String> keys, Object... args) {
        String cacheKey = resultType.getName() + ":" + sha256Hex(script);
        DefaultRedisScript<T> redisScript = (DefaultRedisScript<T>) scriptCache.computeIfAbsent(
                cacheKey, k -> new DefaultRedisScript<>(script, resultType));
        return redisTemplate.execute(redisScript, keys, args);
    }

    private static String sha256Hex(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed present in all JDK distributions
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

