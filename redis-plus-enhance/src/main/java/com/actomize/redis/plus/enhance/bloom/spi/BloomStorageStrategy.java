package com.actomize.redis.plus.enhance.bloom.spi;

/**
 * 布隆过滤器存储策略 SPI
 *
 * <p>定义布隆过滤器底层 bit 检查与设置操作的抽象，允许按需选择存储后端。
 * 操作均为多 bit 批量原子接口，以支持 Lua 脚本原子执行或 RedisBloom 模块命令。
 *
 * <ul>
 *   <li>{@link StorageType#BITMAP} — 使用 Redis 原生 SETBIT/GETBIT Lua 脚本，
 *       兼容性最好，所有 Redis 版本均支持。默认实现。</li>
 *   <li>{@link StorageType#REDISBLOOM} — 使用 RedisBloom 模块（RedisStack），
 *       支持可扩展布隆过滤器（BF.ADD / BF.EXISTS），性能更佳但需要安装模块。</li>
 *   <li>{@link StorageType#HYBRID} — 本地内存 + Redis 双层存储，
 *       本地缓存热点判断，Redis 兜底全量存储。</li>
 * </ul>
 */
public interface BloomStorageStrategy {

    /**
     * 原子检查：所有给定 bit 位是否均为 1（即"可能存在"）。
     *
     * @param filterKey  Redis Key（布隆过滤器名称）
     * @param bitOffsets bit 偏移量数组
     * @return {@code true} 表示所有 bit 均为 1
     */
    boolean checkBits(String filterKey, long[] bitOffsets);

    /**
     * 原子设置：将所有给定 bit 位设置为 1。
     *
     * @param filterKey  Redis Key
     * @param bitOffsets bit 偏移量数组
     */
    void setBits(String filterKey, long[] bitOffsets);

    /**
     * 获取当前存储策略类型。
     */
    StorageType type();

    // ── 存储类型枚举 ─────────────────────────────────────────────────

    enum StorageType {
        /**
         * Redis 原生 SETBIT/GETBIT（默认，兼容所有版本）
         */
        BITMAP,
        /**
         * RedisBloom 模块（需要 Redis Stack 或安装 RedisBloom 模块）
         */
        REDISBLOOM,
        /**
         * 本地内存 + Redis 双层（本地判断热点，Redis 兜底全量）
         */
        HYBRID
    }
}

