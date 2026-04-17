package com.actomize.redis.plus.governance.metrics.spi;

import io.micrometer.core.instrument.Tags;

/**
 * 自定义指标标签贡献 SPI
 *
 * <p>允许业务系统向 redis-plus 上报的所有指标中追加自定义维度标签，
 * 例如租户 ID、环境标识、应用版本等，实现多维度聚合分析。
 *
 * <p>注册为 Spring Bean 后，{@link com.actomize.redis.plus.governance.metrics.MicrometerRedisPlusMetrics}
 * 会在每次上报指标时调用所有贡献者并合并标签。
 *
 * <p>使用示例：
 * <pre>
 * {@literal @}Bean
 * public MetricsTagContributor tenantTagContributor() {
 *     return () -> Tags.of("tenant", TenantContext.getCurrentTenantId(),
 *                          "env", System.getProperty("spring.profiles.active", "default"));
 * }
 * </pre>
 */
@FunctionalInterface
public interface MetricsTagContributor {

    /**
     * 提供需要追加到指标上的额外标签集合。
     *
     * @return 追加标签；返回 {@link Tags#empty()} 表示不追加
     */
    Tags contribute();
}

