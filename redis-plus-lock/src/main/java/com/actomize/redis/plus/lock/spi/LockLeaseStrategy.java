package com.actomize.redis.plus.lock.spi;

import com.actomize.redis.plus.lock.LockDefinition;

/**
 * 锁租约与续期策略 SPI
 *
 * <p>决定锁的租约时长以及 WatchDog 续期间隔。
 * 默认实现为固定时长策略（30s 租约，10s 续期）。
 */
public interface LockLeaseStrategy {

    /**
     * 根据锁定义计算实际租约时长（毫秒）。
     *
     * @param definition 锁元数据
     * @return 租约时长（毫秒）；返回 {@code -1} 表示使用全局默认值
     */
    long leaseTimeMillis(LockDefinition definition);

    /**
     * 全局默认租约时长（毫秒）。
     */
    default long defaultLeaseMillis() {
        return -1L;
    }

    /**
     * WatchDog 续期间隔（毫秒）。续期线程以此间隔定期重置 TTL。
     * 通常建议设为租约时长的 1/3。
     *
     * @param leaseTimeMillis 当前租约时长
     * @return 续期间隔（毫秒）
     */
    default long renewIntervalMillis(long leaseTimeMillis) {
        return leaseTimeMillis / 3;
    }
}
