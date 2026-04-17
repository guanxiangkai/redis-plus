/**
 * redis-plus-queue — Redis 消息队列能力模块
 *
 * 职责：Redis List / Stream 队列抽象、默认实现与重试/死信 SPI
 */
dependencies {
    api(project(":redis-plus-core"))

    // 队列公开类型与实现构造器会暴露 RedisTemplate 相关签名
    api(libs.bundles.queue.public.api)
}
