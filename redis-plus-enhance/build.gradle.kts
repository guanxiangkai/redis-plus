/**
 * redis-plus-enhance — 缓存增强能力模块
 *
 * 职责：布隆过滤器（Redis Bitmap 实现）、防缓存穿透 / 防击穿 / 防雪崩策略、
 *       批量读写操作编排（Pipeline / MGET / MSET）
 */
dependencies {
    api(project(":redis-plus-cache"))

    // BloomCheck 切面实现属于模块内细节；SpEL 求值由 core 统一承接
    compileOnly(libs.bundles.enhance.impl.support)
}

