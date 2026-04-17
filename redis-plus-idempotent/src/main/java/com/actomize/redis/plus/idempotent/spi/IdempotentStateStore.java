package com.actomize.redis.plus.idempotent.spi;

import java.time.Duration;
import java.util.Optional;

/**
 * 幂等状态存储 SPI
 *
 * <p>定义幂等执行状态（PROCESSING / DONE / FAILED）的持久化与查询契约。
 * 默认实现基于 Redis String 类型。
 * 用户可注册自定义 Bean 替换为数据库存储、分布式缓存等方案。
 *
 * <p>状态语义：
 * <ul>
 *   <li>{@code PROCESSING} — 正在执行，其他线程应等待或快速失败</li>
 *   <li>{@code DONE:&lt;result&gt;} — 已完成，result 为序列化结果</li>
 *   <li>{@code FAILED} — 已失败，允许重试</li>
 * </ul>
 */
public interface IdempotentStateStore {

    /**
     * 尝试占位（原子 CAS：仅当 Key 不存在时设置 PROCESSING）。
     *
     * @param idempotentKey     幂等键（已包含前缀）
     * @param processingTimeout PROCESSING 状态保留时长；应设置为任务最大执行时间加一个安全缓冲，
     *                          与业务结果 TTL 无关。PROCESSING key 过期时视为超时，允许并发重试。
     * @return 若 Key 已存在则返回当前状态值；Key 不存在时设置成功返回 {@link Optional#empty()}
     */
    Optional<String> tryAcquire(String idempotentKey, Duration processingTimeout);

    /**
     * 将状态标记为已完成，存储序列化结果。
     *
     * @param idempotentKey 幂等键
     * @param resultValue   序列化后的结果字符串
     * @param ttl           状态保留时长
     */
    void markDone(String idempotentKey, String resultValue, Duration ttl);

    /**
     * 将状态标记为失败（允许重试）。
     *
     * @param idempotentKey 幂等键
     * @param failureTtl    失败状态保留时长（通常短于成功 TTL）
     */
    void markFailed(String idempotentKey, Duration failureTtl);

    /**
     * 查询当前状态（不修改状态）。
     *
     * @param idempotentKey 幂等键
     * @return 当前状态字符串；Key 不存在则返回 {@link Optional#empty()}
     */
    Optional<String> getStatus(String idempotentKey);

    /**
     * 原子 CAS：将状态从 FAILED 替换为 PROCESSING（并发重试保护）。
     *
     * <p>当多个并发请求同时发现上次状态为 FAILED 并尝试重试时，
     * 只有一个线程能成功将 Key 从 FAILED 原子地替换为 PROCESSING；
     * 其余线程应将此重试视为"正在处理中"并快速失败，避免并发重复执行。
     *
     * @param idempotentKey     幂等键（已包含前缀）
     * @param failedValue       当前 FAILED 状态序列化值（用于 CAS 比较，即 GET 返回值）
     * @param processingValue   目标 PROCESSING 状态序列化值
     * @param processingTimeout PROCESSING 状态保留时长
     * @return CAS 成功（本线程赢得重试权）返回 {@code true}；Key 已被其他线程替换则返回 {@code false}
     */
    boolean tryReacquireFromFailed(String idempotentKey, String failedValue,
                                   String processingValue, Duration processingTimeout);

    /**
     * 删除幂等状态（用于强制清理或测试）。
     *
     * @param idempotentKey 幂等键
     */
    void delete(String idempotentKey);
}

