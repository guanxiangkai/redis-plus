package com.actomize.redis.plus.lock.impl;

import com.actomize.redis.plus.core.invoke.InvocationContext;
import com.actomize.redis.plus.core.invoke.InvocationContextSpelSupport;
import com.actomize.redis.plus.lock.spi.LockKeyResolver;

/**
 * 基于 Spring SpEL 的默认锁 Key 解析实现
 *
 * <p>若表达式不含 {@code #} 或 {@code '} 则视为字面量直接返回；
 * 否则通过 SpEL 求值，将方法参数名暴露为 {@code #paramName} 变量。
 *
 * <p>示例表达式：
 * <pre>
 *   "#orderId"          → 方法参数 orderId 的 toString
 *   "'inventory:' + #skuId"  → 字符串拼接
 *   "#user.id"          → 对象属性访问
 * </pre>
 */
public class SpelLockKeyResolver implements LockKeyResolver {

    @Override
    public String resolve(InvocationContext context, String expression) {
        String resolved = InvocationContextSpelSupport.resolveToString(context, expression);
        return resolved != null ? resolved : "";
    }
}
