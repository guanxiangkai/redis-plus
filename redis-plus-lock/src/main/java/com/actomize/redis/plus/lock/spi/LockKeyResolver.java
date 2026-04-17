package com.actomize.redis.plus.lock.spi;

import com.actomize.redis.plus.core.invoke.InvocationContext;
import com.actomize.redis.plus.core.invoke.InvocationKeyResolver;

/**
 * 锁 Key 解析 SPI
 *
 * <p>从方法调用上下文（参数、注解、线程上下文等）中解析出用于构造 Redis Lock Key 的字符串。
 * 默认实现基于 Spring SpEL 表达式求值。
 */
@FunctionalInterface
public interface LockKeyResolver extends InvocationKeyResolver {

    /**
     * 解析锁 Key。
     *
     * @param context    方法调用上下文，可访问目标方法与实际参数
     * @param expression 注解中配置的 SpEL 表达式或固定字符串
     * @return 解析后的锁 Key 片段（不含命名空间前缀）
     */
    String resolve(InvocationContext context, String expression);
}
