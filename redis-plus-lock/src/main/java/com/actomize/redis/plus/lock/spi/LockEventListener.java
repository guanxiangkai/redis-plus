package com.actomize.redis.plus.lock.spi;

import com.actomize.redis.plus.lock.event.LockAcquiredEvent;
import com.actomize.redis.plus.lock.event.LockFailedEvent;
import com.actomize.redis.plus.lock.event.LockReleasedEvent;

/**
 * 锁事件监听 SPI
 *
 * <p>注册此接口的 Spring Bean 可监听分布式锁的获取、释放、失败等生命周期事件，
 * 常用于告警、审计、指标补充等场景。
 *
 * <p>默认所有方法均为空实现（no-op），子类按需覆盖。
 *
 * <p>使用示例：
 * <pre>
 * {@literal @}Component
 * public class AuditLockEventListener implements LockEventListener {
 *
 *     {@literal @}Override
 *     public void onAcquired(LockAcquiredEvent event) {
 *         log.info("锁获取成功: key={}, 等待耗时={}ms",
 *                 event.getKey(), event.getWaitTime().toMillis());
 *     }
 *
 *     {@literal @}Override
 *     public void onFailed(LockFailedEvent event) {
 *         alertService.send("锁竞争失败: " + event.getKey());
 *     }
 * }
 * </pre>
 */
public interface LockEventListener {

    /**
     * 锁成功获取回调。
     *
     * @param event 锁获取事件（含 Key 和等待耗时）
     */
    default void onAcquired(LockAcquiredEvent event) {
    }

    /**
     * 锁释放回调。
     *
     * @param event 锁释放事件（含 Key 和持有时长）
     */
    default void onReleased(LockReleasedEvent event) {
    }

    /**
     * 锁获取失败回调（等待超时或竞争失败）。
     *
     * @param event 锁失败事件（含 Key 和失败原因）
     */
    default void onFailed(LockFailedEvent event) {
    }
}

