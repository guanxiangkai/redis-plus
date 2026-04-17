package com.actomize.redis.plus.core.util;

/**
 * 异常工具类
 *
 * <p>提供 sneaky throw 支持：利用泛型擦除绕过编译器受检异常检查，
 * 允许在不修改方法签名的 lambda / 函数式接口场景中直接重新抛出任意异常。
 *
 * <p>调用后 JVM 抛出原始异常，调用处的 {@code throws} 声明中无需包含该异常类型。
 * 调用后方法不会正常返回；调用方须在后面添加 {@code throw new AssertionError()}
 * 以使编译器满意。
 */
public final class ExceptionUtils {

    private ExceptionUtils() {
    }

    /**
     * 以"sneaky"方式重新抛出 {@code Throwable}。
     *
     * <p><b>注意</b>：该方法仅在无法修改方法签名的场景中使用；
     * 正常业务逻辑应通过标准受检异常或 {@link RuntimeException} 传递错误。
     *
     * @param t 要重新抛出的异常（不得为 null）
     */
    @SuppressWarnings("unchecked")
    public static <T extends Throwable> void sneakyThrow(Throwable t) throws T {
        throw (T) t;
    }
}
