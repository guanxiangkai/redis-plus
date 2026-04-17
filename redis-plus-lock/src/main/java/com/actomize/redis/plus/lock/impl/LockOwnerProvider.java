package com.actomize.redis.plus.lock.impl;

import java.util.UUID;

/**
 * 生成稳定的锁持有者标识。
 *
 * <p>同一 JVM 实例内，同一线程应得到相同 owner，
 * 以保证通过工厂重复获取同名锁时仍具备稳定的可重入语义。
 */
final class LockOwnerProvider {

    private final String instanceId;

    LockOwnerProvider() {
        this(UUID.randomUUID().toString().replace("-", ""));
    }

    LockOwnerProvider(String instanceId) {
        this.instanceId = instanceId;
    }

    String currentOwner() {
        return instanceId + ":" + Thread.currentThread().threadId();
    }
}
