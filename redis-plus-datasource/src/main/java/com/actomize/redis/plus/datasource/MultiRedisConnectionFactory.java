package com.actomize.redis.plus.datasource;

import com.actomize.redis.plus.core.exception.RedisPlusException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisSentinelConnection;

import java.util.Map;
import java.util.Set;

/**
 * 多 Redis 连接工厂路由器
 *
 * <p>实现 {@link RedisConnectionFactory} 接口，可直接注入到 {@code RedisTemplate} / {@code StringRedisTemplate}，
 * 透明地根据 {@link RedisDataSourceContext} 中的数据源名称路由到对应连接工厂。
 * 类似 Spring 的 {@code AbstractRoutingDataSource} 模式。
 *
 * <p>提供 {@link #destroy()} 生命周期方法：应用关闭时可销毁内部持有的所有连接工厂，
 * 确保 Lettuce 连接资源正常释放。
 */
public class MultiRedisConnectionFactory implements RedisConnectionFactory, DisposableBean {

    private final Map<String, RedisConnectionFactory> factories;
    private final String primary;
    private final boolean strict;
    /**
     * 可选路由策略：优先级低于数据源上下文，高于 primary 兜底
     */
    private final RedisRouteStrategy routeStrategy;

    /**
     * @param factories 所有可用数据源映射（name → factory）
     * @param primary   默认数据源名称
     * @param strict    严格模式：数据源名称不存在时抛异常而不是回退 primary
     */
    public MultiRedisConnectionFactory(Map<String, RedisConnectionFactory> factories,
                                       String primary,
                                       boolean strict) {
        this(factories, primary, strict, null);
    }

    /**
     * @param factories     所有可用数据源映射（name → factory）
     * @param primary       默认数据源名称
     * @param strict        严格模式：数据源名称不存在时抛异常而不是回退 primary
     * @param routeStrategy 自定义路由策略（可为 null）
     */
    public MultiRedisConnectionFactory(Map<String, RedisConnectionFactory> factories,
                                       String primary,
                                       boolean strict,
                                       RedisRouteStrategy routeStrategy) {
        if (!factories.containsKey(primary)) {
            throw new RedisPlusException("多 Redis 数据源配置错误：primary '" + primary + "' 未在数据源列表中定义");
        }
        this.factories = Map.copyOf(factories);
        this.primary = primary;
        this.strict = strict;
        this.routeStrategy = routeStrategy;
    }

    // ── RedisConnectionFactory 接口实现（委托给路由结果） ─────────────────

    @Override
    public RedisConnection getConnection() {
        return determine().getConnection();
    }

    @Override
    public RedisClusterConnection getClusterConnection() {
        return determine().getClusterConnection();
    }

    @Override
    public boolean getConvertPipelineAndTxResults() {
        return determine().getConvertPipelineAndTxResults();
    }

    @Override
    public RedisSentinelConnection getSentinelConnection() {
        return determine().getSentinelConnection();
    }

    @Override
    public DataAccessException translateExceptionIfPossible(RuntimeException ex) {
        return determine().translateExceptionIfPossible(ex);
    }

    // ── 路由核心逻辑 ─────────────────────────────────────────────────

    /**
     * 根据当前路由上下文返回对应连接工厂。
     *
     * <p>路由优先级：
     * <ol>
     *   <li>{@link RedisDataSourceContext#get()} 返回的上下文值（由 {@code @RedisDS} 切面设置）</li>
     *   <li>{@link RedisRouteStrategy#determineSource()}（用户自定义，如从请求头/JWT 路由）</li>
     *   <li>{@link #primary}（兜底默认数据源）</li>
     * </ol>
     *
     * @return 目标 {@link RedisConnectionFactory}
     */
    public RedisConnectionFactory determine() {
        // 1. 数据源上下文（@RedisDS 切面）
        String name = RedisDataSourceContext.get();

        // 2. 自定义路由策略
        if ((name == null || name.isBlank()) && routeStrategy != null) {
            name = routeStrategy.determineSource();
        }

        if (name == null || name.isBlank()) {
            return factories.get(primary);
        }
        RedisConnectionFactory factory = factories.get(name);
        if (factory == null) {
            if (strict) {
                throw new RedisPlusException("未找到 Redis 数据源：'" + name + "'，已配置的数据源：" + factories.keySet());
            }
            return factories.get(primary);
        }
        return factory;
    }

    /**
     * 获取所有已注册的数据源名称（只读视图）。
     */
    public Set<String> sourceNames() {
        return factories.keySet();
    }

    /**
     * 获取 primary 数据源名称。
     */
    public String getPrimary() {
        return primary;
    }

    /**
     * 应用关闭时销毁所有内部持有的连接工厂，释放 Lettuce/连接池资源。
     * 若 factory 是用户外部传入的 Spring Bean，Spring 容器也会独立调用其 destroy()，
     * 而 {@link org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory#destroy()} 是幂等的，重复调用安全。
     */
    @Override
    public void destroy() throws Exception {
        for (RedisConnectionFactory factory : factories.values()) {
            if (factory instanceof DisposableBean db) {
                db.destroy();
            }
        }
    }
}
