package com.actomize.redis.plus.governance.observation;

/**
 * {@link RedisPlusObservationConvention} 默认实现
 *
 * <p>全部使用接口 {@code default} 方法的约定名称，无需覆盖任何方法。
 * 由 {@code redis-plus-starter} 在 {@code RedisPlusGovernanceAutoConfiguration} 中
 * 以 {@code @ConditionalOnMissingBean} 自动注册；
 * 用户可注册自定义实现覆盖命名约定。
 */
public class DefaultRedisPlusObservationConvention implements RedisPlusObservationConvention {
    // 全部依赖 interface 的 default 实现，无需重写任何方法
}
