/**
 * redis-plus-starter — Spring Boot 自动装配与注解接入层
 *
 * 职责：自动装配全部模块 Bean、配置属性绑定、AOP 注解切面注册、
 *       健康检查与 Micrometer 指标注册
 */
dependencies {
    // 传递所有功能模块（api 级别，使用方引入 starter 即可获取全部能力）
    api(project(":redis-plus-core"))
    api(project(":redis-plus-lock"))
    api(project(":redis-plus-datasource"))
    api(project(":redis-plus-cache"))
    api(project(":redis-plus-enhance"))
    api(project(":redis-plus-ratelimit"))
    api(project(":redis-plus-idempotent"))
    api(project(":redis-plus-queue"))
    api(project(":redis-plus-governance"))

    // Starter 自身自动装配代码直接使用的运行时实现依赖
    implementation(libs.bundles.starter.impl.support)

    // 自动配置中创建 JacksonCacheValueSerializer Bean 需要 ObjectMapper 类型
    compileOnly(libs.jackson.databind)

    // 配置属性 @Validated 约束注解（JSR-303 API；实际校验器由用户应用提供）
    compileOnly(libs.jakarta.validation.api)

    // 配置元数据处理器（生成 IDE 配置提示）
    annotationProcessor(libs.spring.boot.configuration.processor)
}
