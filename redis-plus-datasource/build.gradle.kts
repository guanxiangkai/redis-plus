/**
 * redis-plus-datasource — 多 Redis 数据源切换模块
 *
 * 职责：多连接工厂管理、主从/读写路由、租户命名空间隔离、数据源上下文切换
 */
dependencies {
    api(project(":redis-plus-core"))

    // MultiRedisConnectionFactory 的公开签名会暴露这些类型
    api(libs.bundles.datasource.public.api)

    // RedisDS 切面实现、生命周期销毁、@Order 与可选路由注入属于实现细节
    compileOnly(libs.bundles.datasource.impl.support)
}

