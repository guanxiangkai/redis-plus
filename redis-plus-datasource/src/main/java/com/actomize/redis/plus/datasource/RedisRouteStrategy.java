package com.actomize.redis.plus.datasource;

/**
 * Redis 数据源路由策略 SPI
 *
 * <p>决定当前请求应路由到哪个数据源。优先级：
 * <ol>
 *   <li>{@link RedisDataSourceContext#get()} 返回的上下文值（由 {@code @RedisDS} 切面设置）</li>
 *   <li>此 SPI 的自定义实现（如从请求头、JWT、租户信息路由）</li>
 *   <li>配置的 primary 数据源（兜底）</li>
 * </ol>
 */
@FunctionalInterface
public interface RedisRouteStrategy {

    /**
     * 解析当前请求应使用的数据源名称。
     *
     * @return 数据源名称；返回 {@code null} 则路由到 primary
     */
    String determineSource();
}

