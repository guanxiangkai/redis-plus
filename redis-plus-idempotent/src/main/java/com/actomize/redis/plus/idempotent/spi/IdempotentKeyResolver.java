package com.actomize.redis.plus.idempotent.spi;

import com.actomize.redis.plus.core.invoke.InvocationContext;
import com.actomize.redis.plus.core.invoke.InvocationKeyResolver;

/**
 * 幂等键解析 SPI
 *
 * <p>从方法调用上下文中解析出幂等唯一键，用于在 Redis 中标识"同一请求"。
 * 默认实现基于 Spring SpEL 表达式求值。
 *
 * <p>幂等键设计建议：
 * <ul>
 *   <li>包含业务操作类型：{@code "order:create"}</li>
 *   <li>包含唯一业务标识：{@code "#request.clientOrderId"}</li>
 *   <li>组合示例：{@code "'order:create:' + #request.clientOrderId"}</li>
 * </ul>
 *
 * <p>自定义示例（从 HTTP 请求头读取幂等 Key）：
 * <pre>
 * {@literal @}Bean
 * public IdempotentKeyResolver headerIdempotentKeyResolver() {
 *     return (context, expression) -> {
 *         HttpServletRequest req = ((ServletRequestAttributes)
 *             RequestContextHolder.getRequestAttributes()).getRequest();
 *         return req.getHeader("X-Idempotency-Key");
 *     };
 * }
 * </pre>
 */
@FunctionalInterface
public interface IdempotentKeyResolver extends InvocationKeyResolver {

    /**
     * 解析幂等键。
     *
     * @param context    方法调用上下文，可访问目标方法签名与实际参数
     * @param expression 注解中配置的 SpEL 表达式或固定字符串
     * @return 幂等唯一键（不含命名空间前缀）
     */
    String resolve(InvocationContext context, String expression);
}
