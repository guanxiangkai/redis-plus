package com.actomize.redis.plus.core.invoke;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;

/**
 * 方法调用上下文
 *
 * <p>用于在不直接暴露 AOP 框架类型（如 AspectJ {@code JoinPoint}）的前提下，
 * 向 SPI 与扩展点传递目标对象、方法和实参数组等调用信息。
 */
public final class InvocationContext {

    private final Object target;
    private final Method method;
    private final Object[] arguments;

    private InvocationContext(Object target, Method method, Object[] arguments) {
        this.target = target;
        this.method = method;
        this.arguments = arguments;
    }

    /**
     * 创建调用上下文。
     */
    public static InvocationContext of(Object target, Method method, Object[] arguments) {
        Objects.requireNonNull(method, "method must not be null");
        Object[] safeArgs = arguments == null ? new Object[0] : Arrays.copyOf(arguments, arguments.length);
        return new InvocationContext(target, method, safeArgs);
    }

    /**
     * 返回目标对象；静态方法场景下可能为 {@code null}。
     */
    public Object getTarget() {
        return target;
    }

    /**
     * 返回被调用的方法。
     */
    public Method getMethod() {
        return method;
    }

    /**
     * 返回调用参数副本。
     */
    public Object[] getArguments() {
        return Arrays.copyOf(arguments, arguments.length);
    }

    /**
     * 返回指定位置的参数。
     *
     * @throws IndexOutOfBoundsException 如果下标越界
     */
    public Object getArgument(int index) {
        if (index < 0 || index >= arguments.length) {
            throw new IndexOutOfBoundsException(
                    "Argument index " + index + " out of bounds for method '"
                    + method.getName() + "' with " + arguments.length + " argument(s)");
        }
        return arguments[index];
    }

    /**
     * 返回参数个数。
     */
    public int getArgumentCount() {
        return arguments.length;
    }

    /**
     * 返回方法名。
     */
    public String getMethodName() {
        return method.getName();
    }
}

