package com.actomize.redis.plus.lock.spi;

import com.actomize.redis.plus.lock.LockDefinition;

/**
 * 锁获取失败处理 SPI
 *
 * <p>当超过 {@code waitTime} 仍未获取到锁时，框架调用此接口决定后续行为。
 * 内置策略：
 * <ul>
 *   <li>{@link #throwException()} — 抛出异常（默认）</li>
 *   <li>{@link #returnFallback(Object)} — 返回降级值</li>
 * </ul>
 */
@FunctionalInterface
public interface LockFailureHandler {

    /**
     * 锁获取失败时抛出 {@link com.actomize.redis.plus.core.exception.RedisLockException}（默认行为）
     */
    static LockFailureHandler throwException() {
        return definition -> {
            throw new com.actomize.redis.plus.core.exception.RedisLockException(
                    "获取分布式锁超时，lockName=" + definition.name()
                            + "，waitTime=" + definition.waitTime());
        };
    }

    // ── 内置工厂方法 ─────────────────────────────────────────────────

    /**
     * 锁获取失败时返回指定的降级值（适用于非关键路径）
     */
    static LockFailureHandler returnFallback(Object fallback) {
        return definition -> fallback;
    }

    /**
     * 处理锁获取失败。
     *
     * @param definition 锁元数据（包含 Key、等待时长等信息）
     * @return 降级返回值；若选择抛出异常则直接 throw，此返回值将被忽略
     */
    Object handle(LockDefinition definition);
}

