package com.actomize.redis.plus.lock;

import java.time.Duration;

/**
 * 锁元数据描述
 *
 * @param name      锁名（Redis Key 中的业务部分）
 * @param leaseTime 租约时长；{@link Duration#ZERO} 或负值表示启用 WatchDog 自动续期
 * @param waitTime  最大等待时间；{@link Duration#ZERO} 表示不等待（tryLock 语义）
 * @param reentrant 是否允许同一锁主体重入（默认 true）
 */
public record LockDefinition(
        String name,
        Duration leaseTime,
        Duration waitTime,
        boolean reentrant
) {
    /**
     * 默认租约时长（30 秒，超时未续期则自动释放）
     */
    public static final Duration DEFAULT_LEASE_TIME = Duration.ofSeconds(30);

    /**
     * 默认等待超时（5 秒）
     */
    public static final Duration DEFAULT_WAIT_TIME = Duration.ofSeconds(5);

    public static LockDefinition of(String name) {
        return new LockDefinition(name, DEFAULT_LEASE_TIME, DEFAULT_WAIT_TIME, true);
    }

    public static LockDefinition of(String name, Duration leaseTime, Duration waitTime) {
        return new LockDefinition(name, leaseTime, waitTime, true);
    }

    /**
     * 是否启用 WatchDog（leaseTime 为负或零时自动续期）
     */
    public boolean isWatchDogEnabled() {
        return leaseTime == null || leaseTime.isNegative() || leaseTime.isZero();
    }
}

