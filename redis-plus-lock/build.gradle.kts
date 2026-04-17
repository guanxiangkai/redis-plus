/**
 * redis-plus-lock — 分布式读写锁模块
 *
 * 职责：可重入分布式锁、读写锁、自动续期（WatchDog）、锁降级、AOP 注解接入
 */
dependencies {
    api(project(":redis-plus-core"))

    // RedisLockFactory / LockAspect / LockKeyResolver 的公开签名会暴露这些类型
    api(libs.bundles.lock.public.api)

    // 切面实现属于模块内细节；SpEL 求值能力由 core 统一承接
    compileOnly(libs.bundles.lock.impl.support)
}

