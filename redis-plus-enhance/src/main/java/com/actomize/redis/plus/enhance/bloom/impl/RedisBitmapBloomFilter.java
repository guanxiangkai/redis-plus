package com.actomize.redis.plus.enhance.bloom.impl;

import com.actomize.redis.plus.enhance.bloom.BloomFilter;
import com.actomize.redis.plus.enhance.bloom.spi.BloomHashProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

/**
 * 基于 Redis Bitmap（SETBIT / GETBIT）的布隆过滤器实现
 *
 * <p>原理：使用 {@code hashCount} 个哈希函数将元素映射到 Bitmap 中的 {@code hashCount} 个 bit 位。
 * 查询时若所有 bit 位均为 1 则"可能存在"，否则"一定不存在"。
 *
 * <p>误判率公式：p ≈ (1 - e^(-k*n/m))^k，其中：
 * <ul>
 *   <li>k = hashCount（哈希函数个数）</li>
 *   <li>n = 预期插入元素数量</li>
 *   <li>m = bitSetSize（Bitmap 大小，bit 数）</li>
 * </ul>
 */
public class RedisBitmapBloomFilter<E> implements BloomFilter<E> {

    /**
     * 多位同时检查 Lua 脚本
     */
    private static final String CHECK_SCRIPT = """
            local key = KEYS[1]
            for i = 1, #ARGV do
                if redis.call('GETBIT', key, ARGV[i]) == 0 then
                    return 0
                end
            end
            return 1
            """;

    /**
     * 多位同时设置 Lua 脚本
     */
    private static final String SET_SCRIPT = """
            local key = KEYS[1]
            for i = 1, #ARGV do
                redis.call('SETBIT', key, ARGV[i], 1)
            end
            return 1
            """;

    private static final DefaultRedisScript<Long> CHECK = new DefaultRedisScript<>(CHECK_SCRIPT, Long.class);
    private static final DefaultRedisScript<Long> SET = new DefaultRedisScript<>(SET_SCRIPT, Long.class);

    private final String name;
    private final String redisKey;
    private final long bitSetSize;
    private final int hashCount;
    private final BloomHashProvider hashProvider;
    private final StringRedisTemplate redisTemplate;

    /**
     * @param name          过滤器名称
     * @param expectedCount 预期最大元素数量
     * @param falsePositive 目标误判率（如 0.01 表示 1%）
     * @param version       版本号（变更参数后递增，隔离旧 Bitmap 数据）
     * @param hashProvider  哈希函数提供者
     * @param redisTemplate Redis 操作模板
     */
    public RedisBitmapBloomFilter(String name,
                                  long expectedCount,
                                  double falsePositive,
                                  int version,
                                  BloomHashProvider hashProvider,
                                  StringRedisTemplate redisTemplate) {
        this.name = name;
        this.redisKey = "redis-plus:bloom:" + name + ":v" + version;
        this.hashProvider = hashProvider;
        this.redisTemplate = redisTemplate;

        // 最优 Bitmap 大小：m = -n*ln(p) / (ln2)^2
        this.bitSetSize = optimalBitSize(expectedCount, falsePositive);
        // 最优哈希函数数量：k = m/n * ln2
        this.hashCount = optimalHashCount(expectedCount, bitSetSize);
    }

    private static long optimalBitSize(long n, double p) {
        return (long) (-n * Math.log(p) / (Math.log(2) * Math.log(2)));
    }

    private static int optimalHashCount(long n, long m) {
        return Math.max(1, (int) Math.round((double) m / n * Math.log(2)));
    }

    @Override
    public boolean mightContain(E element) {
        long[] positions = positions(element);
        String[] args = toStringArgs(positions);
        Long result = redisTemplate.execute(CHECK, Collections.singletonList(redisKey), (Object[]) args);
        return result != null && result == 1L;
    }

    @Override
    public void put(E element) {
        long[] positions = positions(element);
        String[] args = toStringArgs(positions);
        redisTemplate.execute(SET, Collections.singletonList(redisKey), (Object[]) args);
    }

    // ── 工具方法 ─────────────────────────────────────────────────────

    @Override
    public void initialize(Iterable<E> elements) {
        byte[] keyBytes = redisKey.getBytes(StandardCharsets.UTF_8);
        redisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            for (E element : elements) {
                for (long pos : positions(element)) {
                    connection.stringCommands().setBit(keyBytes, pos, true);
                }
            }
            return null;
        });
    }

    @Override
    public String getName() {
        return name;
    }

    private long[] positions(E element) {
        return hashProvider.hash(element.toString(), hashCount, bitSetSize);
    }

    private String[] toStringArgs(long[] positions) {
        String[] args = new String[positions.length];
        for (int i = 0; i < positions.length; i++) {
            args[i] = String.valueOf(positions[i]);
        }
        return args;
    }
}

