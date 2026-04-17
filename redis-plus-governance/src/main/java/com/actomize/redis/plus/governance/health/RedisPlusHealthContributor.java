package com.actomize.redis.plus.governance.health;

import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.util.Map;

/**
 * Redis Plus 健康检查贡献者
 *
 * <p>对所有已注册的 {@link RedisConnectionFactory} 执行 PING 检测，
 * 聚合结果汇报至 Spring Boot Actuator 的 {@code /actuator/health} 端点。
 */
public class RedisPlusHealthContributor extends AbstractHealthIndicator {

    private final Map<String, RedisConnectionFactory> factories;

    public RedisPlusHealthContributor(Map<String, RedisConnectionFactory> factories) {
        super("Redis Plus health check failed");
        this.factories = factories;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        boolean allUp = true;
        for (Map.Entry<String, RedisConnectionFactory> entry : factories.entrySet()) {
            String name = entry.getKey();
            boolean isUp = pingFactory(entry.getValue());
            builder.withDetail(name, isUp ? "UP" : "DOWN");
            if (!isUp) allUp = false;
        }
        if (allUp) {
            builder.up();
        } else {
            builder.down();
        }
    }

    private boolean pingFactory(RedisConnectionFactory factory) {
        try (var conn = factory.getConnection()) {
            String pong = conn.ping();
            return "PONG".equalsIgnoreCase(pong);
        } catch (Exception e) {
            return false;
        }
    }
}

