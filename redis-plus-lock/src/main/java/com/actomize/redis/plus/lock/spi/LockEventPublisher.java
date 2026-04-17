package com.actomize.redis.plus.lock.spi;

/**
 * 锁事件发布 SPI
 *
 * <p>用于将锁生命周期事件桥接到具体事件总线（如 Spring ApplicationEventPublisher），
 * 从而避免在公开 API 中直接暴露外部事件框架类型。
 */
@FunctionalInterface
public interface LockEventPublisher {

    /**
     * 发布锁事件。
     *
     * @param event 锁生命周期事件对象
     */
    void publish(Object event);
}
