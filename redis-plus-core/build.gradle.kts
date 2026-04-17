/**
 * redis-plus-core — 核心基础设施层
 *
 * 职责：统一异常体系、Key 命名规范、序列化抽象、脚本执行器、
 *       过期策略、事件模型、指标 SPI、SPI 扩展点注册
 */
dependencies {
    // 公共 API：StringRedisTemplate（DefaultRedisScriptExecutor）+ spring-context（RedisPlusEvent 继承 ApplicationEvent）
    api(libs.bundles.core.public.api)

    // 实现支撑：Spring SpEL 求值（spring-core + spring-expression），spring-context 已在 public-api 中
    compileOnly(libs.bundles.core.impl.support)

    // 配置元数据处理器（生成 IDE 提示，annotationProcessor）
    annotationProcessor(libs.spring.boot.configuration.processor)
}

