package com.actomize.redis.plus.datasource;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

/**
 * Redis 数据源路由上下文
 *
 * <p>使用线程隔离的栈式上下文保存当前数据源名称，
 * 无需手动清理外层状态，兼容嵌套调用与虚拟线程场景。
 *
 * <p>使用方式：
 * <pre>
 * // 编程式切换
 * RedisDataSourceContext.callWithSource("slave",
 *     () -> redisTemplate.opsForValue().get("key"));
 *
 * // 注解式切换（由 RedisDSAspect 自动调用）
 * {@literal @}RedisDS("slave")
 * public String getValue(String key) { ... }
 * </pre>
 */
public final class RedisDataSourceContext {

    private static final ThreadLocal<Deque<String>> SOURCE_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    private RedisDataSourceContext() {
    }

    /**
     * 在给定数据源上下文中执行带返回值的逻辑。
     */
    public static <T> T callWithSource(String sourceName, Supplier<T> supplier) {
        Deque<String> stack = enter(sourceName);
        try {
            return supplier.get();
        } finally {
            exit(stack);
        }
    }

    /**
     * 在给定数据源上下文中执行无返回值逻辑。
     */
    public static void runWithSource(String sourceName, Runnable runnable) {
        Deque<String> stack = enter(sourceName);
        try {
            runnable.run();
        } finally {
            exit(stack);
        }
    }

    /**
     * 在给定数据源上下文中执行带返回值的逻辑（允许抛出受检异常）。
     */
    public static <T> T callWithSourceThrowing(String sourceName, ThrowingSupplier<T> supplier) throws Throwable {
        Deque<String> stack = enter(sourceName);
        try {
            return supplier.get();
        } finally {
            exit(stack);
        }
    }

    /**
     * 在给定数据源上下文中执行无返回值逻辑（允许抛出受检异常）。
     */
    public static void runWithSourceThrowing(String sourceName, ThrowingRunnable runnable) throws Throwable {
        Deque<String> stack = enter(sourceName);
        try {
            runnable.run();
        } finally {
            exit(stack);
        }
    }

    /**
     * 获取当前绑定的数据源名称，未设置时返回 {@code null}。
     */
    public static String get() {
        Deque<String> stack = SOURCE_STACK.get();
        return stack.isEmpty() ? null : stack.peek();
    }

    /**
     * 判断当前是否已切换到指定数据源。
     */
    public static boolean is(String sourceName) {
        return sourceName != null && sourceName.equals(get());
    }

    private static Deque<String> enter(String sourceName) {
        if (sourceName == null || sourceName.isBlank()) {
            throw new IllegalArgumentException("sourceName must not be blank");
        }
        Deque<String> stack = SOURCE_STACK.get();
        stack.push(sourceName);
        return stack;
    }

    private static void exit(Deque<String> stack) {
        stack.pop();
        if (stack.isEmpty()) {
            SOURCE_STACK.remove();
        }
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Throwable;
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Throwable;
    }
}

