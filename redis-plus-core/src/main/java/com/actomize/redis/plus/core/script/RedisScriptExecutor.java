package com.actomize.redis.plus.core.script;

import java.util.List;

/**
 * Redis Lua 脚本执行器抽象
 *
 * <p>封装 Spring Data Redis {@code RedisScript} 执行，
 * 支持脚本执行前后增强（如日志、耗时统计、熔断等）。
 *
 * <p>默认实现为 {@link DefaultRedisScriptExecutor}，直接委托给
 * {@link org.springframework.data.redis.core.StringRedisTemplate}。
 *
 * <p>扩展示例（增加耗时日志）：
 * <pre>
 * {@literal @}Bean
 * public RedisScriptExecutor loggingScriptExecutor(StringRedisTemplate tpl) {
 *     return new DefaultRedisScriptExecutor(tpl) {
 *         {@literal @}Override
 *         public &lt;T&gt; T execute(String script, Class&lt;T&gt; resultType, List&lt;String&gt; keys, Object... args) {
 *             long start = System.currentTimeMillis();
 *             try {
 *                 return super.execute(script, resultType, keys, args);
 *             } finally {
 *                 log.debug("Lua 执行耗时 {} ms", System.currentTimeMillis() - start);
 *             }
 *         }
 *     };
 * }
 * </pre>
 */
public interface RedisScriptExecutor {

    /**
     * 执行 Lua 脚本。
     *
     * @param script     Lua 脚本内容
     * @param resultType 返回值类型（{@code Long.class}、{@code String.class}、{@code List.class} 等）
     * @param keys       KEYS 参数列表
     * @param args       ARGV 参数列表
     * @param <T>        返回值类型
     * @return 脚本执行结果
     */
    <T> T execute(String script, Class<T> resultType, List<String> keys, Object... args);

    /**
     * 执行无 KEY、无 ARGV 的简单脚本（如 {@code return 1}）。
     *
     * @param script     Lua 脚本内容
     * @param resultType 返回值类型
     * @param <T>        返回值类型
     * @return 脚本执行结果
     */
    default <T> T execute(String script, Class<T> resultType) {
        return execute(script, resultType, List.of());
    }
}

