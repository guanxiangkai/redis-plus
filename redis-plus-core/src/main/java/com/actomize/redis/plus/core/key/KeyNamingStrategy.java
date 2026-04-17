package com.actomize.redis.plus.core.key;

/**
 * Key 命名策略 SPI
 *
 * <p>负责将业务语义（命名空间 + 多个片段）拼装为 Redis Key 字符串。
 * 默认实现为 {@link DefaultKeyNamingStrategy}（冒号分隔），
 * 用户可注册自定义 Bean 覆盖。
 *
 * <p>示例：
 * <pre>
 *   namespace = "order", parts = ["detail", "1001"]
 *   → "order:detail:1001"
 * </pre>
 */
@FunctionalInterface
public interface KeyNamingStrategy {

    /**
     * 生成 Redis Key。
     *
     * @param namespace 业务命名空间（模块前缀），如 {@code "lock"}、{@code "cache:order"}
     * @param parts     构成 Key 的各个片段，通常来自方法参数或注解 SpEL 表达式
     * @return 完整的 Redis Key 字符串
     */
    String resolve(String namespace, String... parts);
}

