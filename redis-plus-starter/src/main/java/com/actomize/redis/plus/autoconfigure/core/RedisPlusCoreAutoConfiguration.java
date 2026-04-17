package com.actomize.redis.plus.autoconfigure.core;

import com.actomize.redis.plus.core.async.DefaultRedisPlusAsyncExecutor;
import com.actomize.redis.plus.core.async.RedisPlusAsyncExecutor;
import com.actomize.redis.plus.core.key.DefaultKeyNamingStrategy;
import com.actomize.redis.plus.core.key.KeyNamingStrategy;
import com.actomize.redis.plus.core.observation.RedisPlusObserver;
import com.actomize.redis.plus.core.script.DefaultRedisScriptExecutor;
import com.actomize.redis.plus.core.script.RedisScriptExecutor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis Plus 核心基础设施自动装配
 *
 * <p>注册以下核心基础 Bean（均为 {@code @ConditionalOnMissingBean}，用户可覆盖）：
 * <ul>
 *   <li>{@link KeyNamingStrategy} — Key 命名策略（默认冒号分隔）</li>
 *   <li>{@link RedisScriptExecutor} — Lua 脚本执行器</li>
 * </ul>
 *
 * <p>同时启用 AspectJ 动态代理（{@code proxyTargetClass = true}），
 * 确保锁、缓存、限流等 AOP 切面对普通类也能正常织入。
 */
@AutoConfiguration
@ConditionalOnClass(StringRedisTemplate.class)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class RedisPlusCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(KeyNamingStrategy.class)
    public KeyNamingStrategy keyNamingStrategy() {
        return new DefaultKeyNamingStrategy();
    }

    @Bean
    @ConditionalOnMissingBean(RedisScriptExecutor.class)
    public RedisScriptExecutor redisScriptExecutor(StringRedisTemplate stringRedisTemplate) {
        return new DefaultRedisScriptExecutor(stringRedisTemplate);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(RedisPlusAsyncExecutor.class)
    public RedisPlusAsyncExecutor redisPlusAsyncExecutor() {
        return new DefaultRedisPlusAsyncExecutor();
    }

    @Bean
    @ConditionalOnMissingBean(RedisPlusObserver.class)
    public RedisPlusObserver redisPlusObserver() {
        return RedisPlusObserver.noop();
    }
}
