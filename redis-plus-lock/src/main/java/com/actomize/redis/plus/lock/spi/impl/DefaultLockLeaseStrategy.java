package com.actomize.redis.plus.lock.spi.impl;

import com.actomize.redis.plus.lock.LockDefinition;
import com.actomize.redis.plus.lock.spi.LockLeaseStrategy;

/**
 * 默认锁租约策略：使用 {@link LockDefinition} 中声明的租约时长，
 * 若未指定则回退到全局默认值（30 秒）。
 *
 * <p>WatchDog 续期间隔默认为租约时长的 1/3（继承自接口默认方法）。
 *
 * @param defaultLeaseMillis 全局默认租约时长（毫秒）
 */
public record DefaultLockLeaseStrategy(long defaultLeaseMillis) implements LockLeaseStrategy {

    public DefaultLockLeaseStrategy() {
        this(30_000L);
    }

    /**
     * @param defaultLeaseMillis 全局默认租约时长（毫秒）
     */
    public DefaultLockLeaseStrategy {
    }

    @Override
    public long leaseTimeMillis(LockDefinition definition) {
        if (definition.leaseTime() == null
                || definition.leaseTime().isNegative()
                || definition.leaseTime().isZero()) {
            return -1L; // 启用 WatchDog
        }
        return definition.leaseTime().toMillis();
    }

    /**
     * 全局默认租约时长（毫秒），供外部查询。
     */
    @Override
    public long defaultLeaseMillis() {
        return defaultLeaseMillis;
    }
}

