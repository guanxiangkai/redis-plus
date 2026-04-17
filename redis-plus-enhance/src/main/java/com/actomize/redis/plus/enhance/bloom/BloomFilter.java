package com.actomize.redis.plus.enhance.bloom;

/**
 * 布隆过滤器统一抽象
 *
 * <p>用于在缓存查询前快速过滤"肯定不存在"的 Key，从根源上防止缓存穿透。
 * 默认实现为基于 Redis SETBIT/GETBIT 的 {@link com.actomize.redis.plus.enhance.bloom.impl.RedisBitmapBloomFilter}。
 *
 * @param <E> 元素类型
 */
public interface BloomFilter<E> {

    /**
     * 检查元素是否可能存在于过滤器中。
     *
     * @param element 待检查元素
     * @return {@code false} 表示一定不存在；{@code true} 表示可能存在（存在误判率）
     */
    boolean mightContain(E element);

    /**
     * 将元素加入布隆过滤器。
     *
     * @param element 待加入元素
     */
    void put(E element);

    /**
     * 批量初始化布隆过滤器（通常在应用启动时预热）。
     *
     * @param elements 初始化数据集
     */
    void initialize(Iterable<E> elements);

    /**
     * 获取过滤器名称（用于 Redis Key 命名）。
     */
    String getName();
}

