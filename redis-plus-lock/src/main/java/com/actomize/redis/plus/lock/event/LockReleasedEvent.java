package com.actomize.redis.plus.lock.event;

import com.actomize.redis.plus.core.event.RedisPlusEvent;

import java.time.Duration;

/**
 * 锁释放事件
 *
 * <p>锁成功释放后由 {@link com.actomize.redis.plus.lock.aop.LockAspect} 发布。
 */
public class LockReleasedEvent extends RedisPlusEvent {

    /**
     * 锁持有时长
     */
    private final Duration heldTime;

    public LockReleasedEvent(String lockKey, Duration heldTime) {
        super(lockKey);
        this.heldTime = heldTime;
    }

    /**
     * 锁持有时长
     */
    public Duration getHeldTime() {
        return heldTime;
    }
}

