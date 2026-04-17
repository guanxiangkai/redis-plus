/**
 * redis-plus-idempotent — Redis 幂等能力模块
 *
 * 职责：幂等注解、执行器抽象、Redis 状态机实现与 Key SPI
 */
dependencies {
    api(project(":redis-plus-core"))

    // 公开实现与构造器会暴露 RedisTemplate 相关签名
    api(libs.bundles.idempotent.public.api)

    // 切面实现依赖 AspectJ
    compileOnly(libs.bundles.idempotent.impl.support)
}
