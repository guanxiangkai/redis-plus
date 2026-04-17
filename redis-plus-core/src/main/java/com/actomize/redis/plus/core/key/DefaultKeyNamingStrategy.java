package com.actomize.redis.plus.core.key;

import java.util.StringJoiner;

/**
 * 默认 Key 命名策略：使用冒号（{@code :}）分隔各片段。
 *
 * <p>示例：
 * <pre>
 *   resolve("lock", "inventory", "SKU-001") → "lock:inventory:SKU-001"
 *   resolve("cache", "user", "42")           → "cache:user:42"
 * </pre>
 */
public class DefaultKeyNamingStrategy implements KeyNamingStrategy {

    private static final String SEPARATOR = ":";

    @Override
    public String resolve(String namespace, String... parts) {
        if (parts == null || parts.length == 0) {
            return namespace;
        }
        StringJoiner joiner = new StringJoiner(SEPARATOR);
        joiner.add(namespace);
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                joiner.add(part);
            }
        }
        return joiner.toString();
    }
}

