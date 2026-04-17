package com.actomize.redis.plus.core.invoke;

/**
 * 通用调用上下文 Key 解析契约。
 */
@FunctionalInterface
public interface InvocationKeyResolver {

    /**
     * 从调用上下文中解析业务 key。
     */
    String resolve(InvocationContext context, String expression);
}
