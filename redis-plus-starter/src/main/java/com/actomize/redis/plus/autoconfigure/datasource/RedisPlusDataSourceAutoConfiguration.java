package com.actomize.redis.plus.autoconfigure.datasource;

import com.actomize.redis.plus.autoconfigure.core.RedisPlusCoreAutoConfiguration;
import com.actomize.redis.plus.autoconfigure.properties.RedisPlusProperties;
import com.actomize.redis.plus.datasource.MultiRedisConnectionFactory;
import com.actomize.redis.plus.datasource.RedisRouteStrategy;
import com.actomize.redis.plus.datasource.aop.RedisDSAspect;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 多数据源自动装配
 *
 * <p>优先级：
 * <ol>
 *   <li>用户自定义 {@link MultiRedisConnectionFactory} Bean（最高优先级，直接使用）</li>
 *   <li>YAML 配置了 {@code redis-plus.datasource.sources.*}（从配置自动构建多数据源）</li>
 *   <li>回退到包装 {@code spring.data.redis.*} 单数据源（兜底）</li>
 * </ol>
 */
@AutoConfiguration(after = RedisPlusCoreAutoConfiguration.class)
@EnableConfigurationProperties(RedisPlusProperties.class)
public class RedisPlusDataSourceAutoConfiguration {

    /**
     * 注册数据源路由切面（@RedisDS 注解支持）。
     * MultiRedisConnectionFactory 始终存在，切面始终激活；单数据源时 @RedisDS 路由到 primary，无副作用。
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisDSAspect redisDSAspect() {
        return new RedisDSAspect();
    }

    /**
     * 构建 {@link MultiRedisConnectionFactory}。
     *
     * <ul>
     *   <li>若 {@code redis-plus.datasource.sources} 非空：为每个数据源创建独立的
     *       {@link LettuceConnectionFactory}（含连接池配置），组装为多数据源路由工厂。</li>
     *   <li>否则：将 Spring Boot {@code spring.data.redis.*} 自动配置的单数据源包装为
     *       名称为 "primary" 的路由工厂。</li>
     * </ul>
     */
    @Bean(destroyMethod = "destroy")
    @ConditionalOnMissingBean(MultiRedisConnectionFactory.class)
    public MultiRedisConnectionFactory multiRedisConnectionFactory(
            RedisPlusProperties properties,
            @Autowired(required = false) RedisConnectionFactory defaultFactory,
            ObjectProvider<RedisRouteStrategy> routeStrategyProvider) {

        RedisPlusProperties.DataSourceProperties ds = properties.getDatasource();
        Map<String, RedisPlusProperties.RedisSourceProperties> sources = ds.getSources();
        RedisRouteStrategy routeStrategy = routeStrategyProvider.getIfAvailable();

        if (!sources.isEmpty()) {
            // ── YAML 多数据源模式 ──
            Map<String, RedisConnectionFactory> factories = new LinkedHashMap<>();
            sources.forEach((name, src) -> factories.put(name, buildLettuceFactory(src)));
            return new MultiRedisConnectionFactory(factories, ds.getPrimary(), ds.isStrict(), routeStrategy);
        }

        // ── 单数据源兜底：包装 spring.data.redis.* 自动配置的 factory ──
        if (defaultFactory == null) {
            throw new IllegalStateException(
                    "未找到 Redis 连接工厂。请配置 spring.data.redis.* 使用单数据源，" +
                            "或配置 redis-plus.datasource.sources.* 使用多数据源。");
        }
        return new MultiRedisConnectionFactory(Map.of("primary", defaultFactory), "primary", false, routeStrategy);
    }

    /**
     * 根据单个数据源属性构建 {@link LettuceConnectionFactory}。
     * 调用 {@code afterPropertiesSet()} 完成连接工厂初始化（等同于 Spring 容器生命周期回调）。
     */
    private LettuceConnectionFactory buildLettuceFactory(RedisPlusProperties.RedisSourceProperties src) {
        RedisStandaloneConfiguration standaloneConfig = new RedisStandaloneConfiguration();
        standaloneConfig.setHostName(src.getHost());
        standaloneConfig.setPort(src.getPort());
        standaloneConfig.setDatabase(src.getDatabase());
        if (src.getPassword() != null && !src.getPassword().isBlank()) {
            standaloneConfig.setPassword(RedisPassword.of(src.getPassword()));
        }

        LettuceConnectionFactory factory;
        RedisPlusProperties.PoolProperties pool = src.getPool();

        if (pool.isEnabled()) {
            GenericObjectPoolConfig<io.lettuce.core.api.StatefulConnection<?, ?>> poolConfig =
                    new GenericObjectPoolConfig<>();
            poolConfig.setMaxTotal(pool.getMaxActive());
            poolConfig.setMaxIdle(pool.getMaxIdle());
            poolConfig.setMinIdle(pool.getMinIdle());
            poolConfig.setMaxWait(pool.getMaxWait());

            LettucePoolingClientConfiguration clientConfig = LettucePoolingClientConfiguration.builder()
                    .commandTimeout(src.getTimeout())
                    .poolConfig(poolConfig)
                    .build();
            factory = new LettuceConnectionFactory(standaloneConfig, clientConfig);
        } else {
            LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                    .commandTimeout(src.getTimeout())
                    .build();
            factory = new LettuceConnectionFactory(standaloneConfig, clientConfig);
        }

        factory.afterPropertiesSet();
        return factory;
    }
}
