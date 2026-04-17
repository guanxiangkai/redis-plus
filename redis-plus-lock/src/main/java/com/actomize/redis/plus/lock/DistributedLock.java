package com.actomize.redis.plus.lock;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁统一抽象
 *
 * <p>支持可重入、超时等待、租约时长配置。底层通过 Redis Lua 脚本保证原子性。
 *
 * <p>基本用法：
 * <pre>
 * DistributedLock lock = lockFactory.getLock("order:pay:1001");
 * if (lock.tryLock(3, 30, TimeUnit.SECONDS)) {
 *     try {
 *         // 业务逻辑
 *     } finally {
 *         lock.unlock();
 *     }
 * }
 * </pre>
 */
public interface DistributedLock {

    /**
     * 尝试获取锁，等待最多 {@code waitTime} 个时间单位。
     *
     * @param waitTime  最大等待时间；{@code 0} 表示不等待
     * @param leaseTime 锁持有时间；{@code -1} 表示启用 WatchDog 自动续期
     * @param unit      时间单位
     * @return {@code true} 表示成功获取锁
     */
    boolean tryLock(long waitTime, long leaseTime, TimeUnit unit);

    /**
     * 不等待地尝试获取锁（leaseTime 使用配置默认值，启用 WatchDog）。
     */
    boolean tryLock();

    /**
     * 阻塞式获取锁，直到成功为止（启用 WatchDog）。
     */
    void lock();

    /**
     * 阻塞式获取锁，指定租约时长（不启用 WatchDog）。
     */
    void lock(long leaseTime, TimeUnit unit);

    /**
     * 释放锁。只有持有锁的线程才能释放；可重入锁每次调用计数减 1。
     *
     * @throws IllegalMonitorStateException 若当前线程未持有该锁
     */
    void unlock();

    /**
     * 判断当前线程是否持有此锁。
     */
    boolean isHeldByCurrentThread();

    /**
     * 判断锁是否被任意线程持有。
     */
    boolean isLocked();

    /**
     * 获取锁的剩余租约时间（毫秒），{@code -1} 表示永不过期或不存在。
     */
    long remainingLeaseTime();
}
