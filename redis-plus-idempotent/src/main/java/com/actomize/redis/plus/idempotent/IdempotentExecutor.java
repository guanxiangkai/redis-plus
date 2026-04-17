package com.actomize.redis.plus.idempotent;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 幂等执行器统一抽象
 *
 * <p>保证相同幂等 Key 的业务逻辑只执行一次，重复请求直接返回缓存结果。
 * 状态机：{@code PENDING → PROCESSING → COMPLETED / FAILED}
 *
 * <p>使用示例：
 * <pre>
 * // 编程式
 * OrderResult result = idempotentExecutor.executeOnce(
 *     "order:pay:" + orderId, Duration.ofHours(24),
 *     () -> paymentService.pay(orderId));
 *
 * // 注解式（由 AOP 切面驱动）
 * {@literal @}Idempotent(key = "'order:pay:' + #orderId", ttl = 24, unit = HOURS)
 * public OrderResult payOrder(Long orderId) { ... }
 * </pre>
 */
public interface IdempotentExecutor {

    /**
     * 幂等执行业务逻辑。
     *
     * @param idempotentKey 幂等键（唯一标识本次业务操作）
     * @param ttl           幂等状态保留时长（过期后允许再次执行）
     * @param task          业务逻辑
     * @param <T>           返回值类型
     * @return 本次执行结果，或缓存的历史结果
     */
    <T> T executeOnce(String idempotentKey, Duration ttl, Supplier<T> task);
}

