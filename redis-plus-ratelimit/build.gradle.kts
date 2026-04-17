/**
 * redis-plus-ratelimit — Redis 限流能力模块
 *
 * 职责：固定窗口 / 滑动窗口 / 令牌桶 / 漏桶限流，
 *       限流注解、切面与算法 SPI
 */
dependencies {
    api(project(":redis-plus-core"))

    // 限流公开类型会暴露 RedisTemplate 相关签名
    api(libs.bundles.ratelimit.public.api)

    // 切面实现依赖 AspectJ
    compileOnly(libs.bundles.ratelimit.impl.support)
}
