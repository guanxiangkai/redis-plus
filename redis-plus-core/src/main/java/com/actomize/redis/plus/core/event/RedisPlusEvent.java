package com.actomize.redis.plus.core.event;

import org.springframework.context.ApplicationEvent;

import java.time.Instant;

/**
 * Redis Plus 框架事件基类
 *
 * <p>继承 Spring {@link ApplicationEvent}，所有模块发布的业务事件都继承此类，
 * 可通过 Spring {@code @EventListener} 进行类型化监听：
 *
 * <pre>
 * {@literal @}EventListener
 * public void onLockAcquired(LockAcquiredEvent event) {
 *     log.info("锁获取成功: key={}", event.getKey());
 * }
 * </pre>
 */
public abstract class RedisPlusEvent extends ApplicationEvent {

    /**
     * 事件发生时间戳
     */
    private final Instant occurredAt;

    /**
     * 关联的 Redis Key（如锁名、缓存 Key 等）
     */
    private final String key;

    protected RedisPlusEvent(String key) {
        super(key);
        this.key = key;
        this.occurredAt = Instant.now();
    }

    public String getKey() {
        return key;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{key='" + key + "', occurredAt=" + occurredAt + '}';
    }
}

