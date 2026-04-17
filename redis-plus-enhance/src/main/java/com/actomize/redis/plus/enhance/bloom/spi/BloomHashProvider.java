package com.actomize.redis.plus.enhance.bloom.spi;

/**
 * 布隆过滤器哈希函数 SPI
 *
 * <p>将元素映射到 {@code hashCount} 个哈希值，每个哈希值对应 Bitmap 中的一个 bit 位。
 * 默认实现提供内置 FNV-1a 双哈希策略，无需额外依赖。
 */
@FunctionalInterface
public interface BloomHashProvider {

    /**
     * 内置 FNV-1a 双哈希实现。
     * 使用 Kirsch-Mitzenmacher 优化：h_i = h1 + i * h2
     */
    static BloomHashProvider fnv1a() {
        return (element, hashCount, bitSetSize) -> {
            long[] result = new long[hashCount];
            byte[] bytes = element.getBytes(java.nio.charset.StandardCharsets.UTF_8);

            // FNV-1a hash 1
            long h1 = 2166136261L;
            for (byte b : bytes) {
                h1 ^= (b & 0xFF);
                h1 *= 1099511628211L;
            }

            // FNV-1a hash 2（种子不同）
            long h2 = 0xcbf29ce484222325L;
            for (byte b : bytes) {
                h2 ^= (b & 0xFF);
                h2 *= 1099511628211L;
            }

            for (int i = 0; i < hashCount; i++) {
                result[i] = Math.abs((h1 + (long) i * h2) % bitSetSize);
            }
            return result;
        };
    }

    // ── 内置工厂方法 ─────────────────────────────────────────────────

    /**
     * 计算元素的哈希值数组。
     *
     * @param element    待哈希的字符串（元素序列化后的字符串表示）
     * @param hashCount  哈希函数个数（由布隆过滤器的误判率配置决定）
     * @param bitSetSize Bitmap 总长度（bit 数），用于取模
     * @return 长度为 {@code hashCount} 的 bit 位索引数组
     */
    long[] hash(String element, int hashCount, long bitSetSize);
}

