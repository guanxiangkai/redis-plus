package com.actomize.redis.plus.idempotent.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 幂等注解
 *
 * <p>标注在方法上，框架自动保证相同 Key 的方法只执行一次，
 * 重复请求直接返回缓存的历史结果。
 *
 * <p>示例：
 * <pre>
 * // 基于请求参数的幂等
 * {@literal @}Idempotent(key = "'order:create:' + #request.clientOrderId", ttl = 24)
 * public OrderResult createOrder(OrderRequest request) { ... }
 *
 * // 更短的幂等窗口（防止接口重试）
 * {@literal @}Idempotent(key = "'sms:send:' + #phone", ttl = 60, unit = TimeUnit.SECONDS)
 * public void sendSms(String phone) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /**
     * 幂等键，支持 SpEL 表达式。
     * 建议包含业务场景标识，如 {@code "'order:pay:' + #orderId"}
     */
    String key();

    /**
     * 幂等状态保留时长（默认 24）。
     * 过期后允许再次执行。
     */
    long ttl() default 24;

    /**
     * 时间单位（默认小时）。
     */
    TimeUnit unit() default TimeUnit.HOURS;
}

