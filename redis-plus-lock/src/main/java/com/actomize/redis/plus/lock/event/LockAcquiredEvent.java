package com.actomize.redis.plus.lock.event;

import com.actomize.redis.plus.core.event.RedisPlusEvent;

import java.time.Duration;

/**
 * 锁成功获取事件
 *
 * <p>锁获取成功后由 {@link com.actomize.redis.plus.lock.aop.LockAspect} 发布，
 * 可通过 {@link com.actomize.redis.plus.lock.spi.LockEventListener} 或
 * Spring {@code ApplicationEventPublisher} 订阅。
 */
public class LockAcquiredEvent extends RedisPlusEvent {

    /**
     * 等待获取锁耗费的时长
     */
    private final Duration waitTime;

    public LockAcquiredEvent(String lockKey, Duration waitTime) {
        super(lockKey);
        this.waitTime = waitTime;
    }

    /**
     * 等待获取锁耗费的时长
     */
    public Duration getWaitTime() {
        return waitTime;
    }
}

