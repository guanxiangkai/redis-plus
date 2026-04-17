package com.actomize.redis.plus.core.key;

/**
 * Key 命名空间标准化工具。
 */
public final class KeyNamespaceUtils {

    private KeyNamespaceUtils() {
    }

    /**
     * 将带尾部分隔符的前缀标准化为命名空间。
     *
     * <p>例如：{@code "redis-plus:lock:" -> "redis-plus:lock"}。
     */
    public static String namespace(String prefix, String fallback) {
        String candidate = (prefix == null || prefix.isBlank()) ? fallback : prefix.trim();
        while (candidate.endsWith(":")) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        return candidate.isBlank() ? fallback : candidate;
    }
}
