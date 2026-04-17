package com.actomize.redis.plus.ratelimit.spi;

import com.actomize.redis.plus.core.invoke.InvocationContext;
import com.actomize.redis.plus.core.invoke.InvocationKeyResolver;

/**
 * 限流 Key 解析 SPI
 *
 * <p>从方法调用上下文中解析出用于限流的 Key 字符串。
 * 默认实现基于 Spring SpEL 表达式求值。
 * 用户可注册自定义 Bean 替换默认行为，例如从 HTTP 请求头中提取用户 ID。
 *
 * <p>限流 Key 通常包含业务语义，如：
 * <pre>
 *   "user:42:api:order:create"      → 用户级别限流
 *   "api:order:create"              → 接口级别限流
 *   "ip:192.168.1.1:api:query"     → IP 级别限流
 * </pre>
 */
@FunctionalInterface
public interface RateLimitKeyResolver extends InvocationKeyResolver {

    /**
     * 解析限流 Key。
     *
     * @param context    方法调用上下文，可访问目标方法签名与实际参数
     * @param expression 注解中配置的 SpEL 表达式或固定字符串
     * @return 解析后的限流 Key（不含命名空间前缀）
     */
    String resolve(InvocationContext context, String expression);
}
