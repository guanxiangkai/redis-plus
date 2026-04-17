package com.actomize.redis.plus.lock;

/**
 * 分布式读写锁抽象
 *
 * <p>读锁允许多个线程并发持有；写锁互斥所有其他锁。
 * 适用于"读多写少"场景（缓存回源保护、热点数据刷新等）。
 *
 * <p>使用示例：
 * <pre>
 * DistributedReadWriteLock rwLock = lockFactory.getReadWriteLock("inventory:SKU-001");
 *
 * // 读操作
 * DistributedLock readLock = rwLock.readLock();
 * readLock.lock();
 * try { ... } finally { readLock.unlock(); }
 *
 * // 写操作
 * DistributedLock writeLock = rwLock.writeLock();
 * writeLock.lock();
 * try { ... } finally { writeLock.unlock(); }
 * </pre>
 */
public interface DistributedReadWriteLock {

    /**
     * 获取读锁实例。同一把读写锁可以同时被多个线程的读锁持有。
     */
    DistributedLock readLock();

    /**
     * 获取写锁实例。写锁为互斥锁，持有写锁时不允许其他读/写锁。
     */
    DistributedLock writeLock();
}

