package com.actomize.redis.plus.cache.spi;

import com.actomize.redis.plus.core.invoke.InvocationContext;
import com.actomize.redis.plus.core.invoke.InvocationKeyResolver;

/**
 * 缓存 Key 解析 SPI
 *
 * <p>从方法调用上下文解析缓存 Key，默认实现基于 SpEL 表达式。
 */
@FunctionalInterface
public interface CacheKeyResolver extends InvocationKeyResolver {
    /**
     * 解析缓存 Key。
     *
     * @param context    方法调用上下文
     * @param expression 注解中配置的 SpEL 表达式或固定字符串
     * @return 解析后的 Key 片段
     */
    String resolve(InvocationContext context, String expression);
}
