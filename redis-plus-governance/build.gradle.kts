/**
 * redis-plus-governance — 治理与运维能力模块
 *
 * 职责：Micrometer 指标实现、Spring Boot Actuator 健康检查、
 *       分片路由辅助、高可用抽象（Sentinel / Cluster）
 */
dependencies {
    api(project(":redis-plus-core"))

    // MetricsTagContributor / RedisPlusHealthContributor 的公开签名会暴露这些类型
    api(libs.bundles.governance.public.api)
}
