package com.actomize.redis.plus.core.expire;

import java.time.Duration;

/**
 * TTL 过期策略 SPI
 *
 * <p>根据基准 TTL 计算实际存储时长，支持固定值、随机抖动等策略，
 * 有效防止大量 Key 同时过期引发缓存雪崩。
 *
 * <p>内置工厂方法：
 * <ul>
 *   <li>{@link #fixed()} — 固定 TTL，原样返回</li>
 *   <li>{@link #randomJitter(double)} — 在基准 TTL 基础上附加随机抖动</li>
 * </ul>
 */
@FunctionalInterface
public interface ExpireStrategy {

    /**
     * 固定 TTL 策略：原样返回基准值。
     */
    static ExpireStrategy fixed() {
        return baseTtl -> baseTtl;
    }

    // ── 内置工厂方法 ─────────────────────────────────────────────────

    /**
     * 随机抖动策略：在基准 TTL 基础上叠加 {@code [0, baseTtl * jitterRatio)} 的随机时长。
     *
     * @param jitterRatio 抖动比例，取值范围 {@code (0.0, 1.0]}；
     *                    例如 {@code 0.2} 表示最多增加 20% 的随机偏移
     */
    static ExpireStrategy randomJitter(double jitterRatio) {
        if (jitterRatio <= 0 || jitterRatio > 1) {
            throw new IllegalArgumentException("jitterRatio must be in (0.0, 1.0], got: " + jitterRatio);
        }
        return baseTtl -> {
            long jitterMs = (long) (baseTtl.toMillis() * jitterRatio * Math.random());
            return baseTtl.plusMillis(jitterMs);
        };
    }

    /**
     * 固定偏移策略：在基准 TTL 基础上固定增加 {@code offset}。
     *
     * @param offset 固定偏移量，不能为负
     */
    static ExpireStrategy fixedOffset(Duration offset) {
        return baseTtl -> baseTtl.plus(offset);
    }

    /**
     * 根据基准 TTL 计算实际存储时长。
     *
     * @param baseTtl 基准过期时间（来自注解或编程式配置）
     * @return 实际写入 Redis 的 TTL；返回 {@link Duration#ZERO} 表示永不过期（谨慎使用）
     */
    Duration computeTtl(Duration baseTtl);
}

