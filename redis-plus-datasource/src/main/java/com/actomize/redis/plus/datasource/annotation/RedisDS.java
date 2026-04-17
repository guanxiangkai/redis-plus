package com.actomize.redis.plus.datasource.annotation;

import java.lang.annotation.*;

/**
 * 多 Redis 数据源切换注解
 *
 * <p>标注在方法或类上，AOP 切面会在方法执行期间将 {@link com.actomize.redis.plus.datasource.RedisDataSourceContext}
 * 切换到指定数据源，方法返回后自动恢复（栈式上下文保证）。
 *
 * <p>示例：
 * <pre>
 * {@literal @}RedisDS("slave")
 * public String getFromSlave(String key) {
 *     return redisTemplate.opsForValue().get(key);
 * }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RedisDS {

    /**
     * 目标数据源名称。
     * 必须与配置中 {@code redis-plus.datasource.sources} 的键名对应。
     */
    String value();
}

