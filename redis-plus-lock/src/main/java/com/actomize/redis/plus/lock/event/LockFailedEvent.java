package com.actomize.redis.plus.lock.event;

import com.actomize.redis.plus.core.event.RedisPlusEvent;

/**
 * 锁获取失败事件
 *
 * <p>等待超时或竞争失败时由 {@link com.actomize.redis.plus.lock.aop.LockAspect} 发布。
 */
public class LockFailedEvent extends RedisPlusEvent {

    /**
     * 失败原因描述
     */
    private final String reason;

    public LockFailedEvent(String lockKey, String reason) {
        super(lockKey);
        this.reason = reason;
    }

    /**
     * 失败原因描述
     */
    public String getReason() {
        return reason;
    }
}

