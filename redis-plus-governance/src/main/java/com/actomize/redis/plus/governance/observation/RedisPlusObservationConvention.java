package com.actomize.redis.plus.governance.observation;

/**
 * Redis Plus 统一埋点命名约定 SPI
 *
 * <p>定义框架所有 Observation（Span/Trace）的名称和 KeyValues 约定，
 * 与 Micrometer Observation API 配合，保证 Trace 数据在 Zipkin / Jaeger / OTLP
 * 等后端中具有一致的命名规范。
 *
 * <p>主要约定：
 * <ul>
 *   <li>分布式锁操作：{@code redis.plus.lock} — 高基数 tag: {@code lock.key}</li>
 *   <li>三级缓存查询：{@code redis.plus.cache.get} — 高基数 tag: {@code cache.name}, {@code cache.key}</li>
 *   <li>限流检查：{@code redis.plus.ratelimit} — 高基数 tag: {@code ratelimit.key}</li>
 *   <li>幂等执行：{@code redis.plus.idempotent} — 高基数 tag: {@code idempotent.key}</li>
 *   <li>队列消费：{@code redis.plus.queue.consume} — 高基数 tag: {@code queue.name}</li>
 * </ul>
 *
 * <p>使用示例（自定义 Observation 名称）：
 * <pre>
 * {@literal @}Bean
 * public RedisPlusObservationConvention customConvention() {
 *     return new RedisPlusObservationConvention() {
 *         public String lockObservationName()  { return "myapp.redis.lock"; }
 *         public String cacheGetObservationName() { return "myapp.redis.cache"; }
 *     };
 * }
 * </pre>
 */
public interface RedisPlusObservationConvention {

    /**
     * Observation 名称：分布式锁操作
     */
    default String lockObservationName() {
        return "redis.plus.lock";
    }

    /**
     * Observation 名称：三级缓存查询
     */
    default String cacheGetObservationName() {
        return "redis.plus.cache.get";
    }

    /**
     * Observation 名称：三级缓存写入
     */
    default String cachePutObservationName() {
        return "redis.plus.cache.put";
    }

    /**
     * Observation 名称：缓存失效
     */
    default String cacheEvictObservationName() {
        return "redis.plus.cache.evict";
    }

    /**
     * Observation 名称：限流检查
     */
    default String rateLimitObservationName() {
        return "redis.plus.ratelimit";
    }

    /**
     * Observation 名称：幂等执行
     */
    default String idempotentObservationName() {
        return "redis.plus.idempotent";
    }

    /**
     * Observation 名称：队列消费
     */
    default String queueConsumeObservationName() {
        return "redis.plus.queue.consume";
    }

    /**
     * Observation 名称：队列发送
     */
    default String queueSendObservationName() {
        return "redis.plus.queue.send";
    }
}

