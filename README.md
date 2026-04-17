<p align="center">
  <h1 align="center">Redis Plus</h1>
  <p align="center">🚀 基于 JDK 25 + Spring Boot 4.0.5 的企业级 Redis 增强框架</p>
  <p align="center">
    <img src="https://img.shields.io/badge/JDK-25-blue" alt="JDK 25"/>
    <img src="https://img.shields.io/badge/Spring%20Boot-4.0.5-green" alt="Spring Boot 4.0.5"/>
    <img src="https://img.shields.io/badge/License-Apache%202.0-orange" alt="License"/>
    <img src="https://img.shields.io/badge/Version-1.0.1-brightgreen" alt="Version"/>
  </p>
</p>

---

## 📖 项目简介

`redis-plus` 是一个面向 Spring 生态的模块化 Redis 增强框架，聚焦以下几类常见企业能力：

- **分布式锁**：统一锁工厂 + 注解式切面接入，支持互斥锁、读写锁、WatchDog 自动续期
- **三级缓存**：L1 本地缓存（Caffeine/Map）+ L2 Redis 缓存 + L3 回源保护，内置防穿透/防击穿
- **缓存增强**：布隆过滤器（`@BloomCheck`）、批量读写、防穿透/防雪崩策略 SPI
- **业务保障**：限流（`@RateLimit`）、幂等（`@Idempotent`）、Redis List/Stream 消息队列
- **治理运维**：Micrometer 指标、Actuator 健康检查、锁/缓存全链路可观测性
- **多 Redis 数据源**：`@RedisDS` 注解路由 + 编程式 `RedisDataSourceContext` 切换

设计目标：

- **模块化按需引入**：每个能力单独成模块，可拆可合
- **Starter 优先**：Spring Boot 项目推荐直接使用 `redis-plus-starter`
- **可选依赖降级**：不引入 Caffeine 时退化为 `ConcurrentHashMap`；不引入 Micrometer/Actuator 时自动跳过指标与健康检查
- **SPI 可扩展**：所有核心能力均提供 SPI 扩展点，用户可替换默认实现

---

## 🏗️ 模块结构

| 模块                      | 职责                                                                                       |
|-------------------------|------------------------------------------------------------------------------------------|
| `redis-plus-core`       | 核心基础设施：统一异常、Key 规范、序列化抽象、TTL 策略、事件模型（`ApplicationEvent`）、指标 SPI                          |
| `redis-plus-lock`       | 分布式锁、读写锁、WatchDog、锁降级、锁事件、`LockKeyResolver`/`LockFailureHandler`/`LockEventListener` SPI |
| `redis-plus-datasource` | 多 Redis 数据源切换、路由连接工厂（实现 `RedisConnectionFactory`）、读写路由、租户命名空间扩展                          |
| `redis-plus-cache`      | L1 Caffeine/Map + L2 Redis + L3 回源保护的三级缓存，内置防穿透/防击穿；通过 `CacheLoadProtection` SPI 解耦锁依赖   |
| `redis-plus-enhance`    | 布隆过滤器（`@BloomCheck`）、批量缓存操作、防穿透/防击穿/防雪崩策略 SPI                                            |
| `redis-plus-ratelimit`  | 限流（`@RateLimit`，固定窗口/滑动窗口/令牌桶/漏桶）、限流算法 SPI                                               |
| `redis-plus-idempotent` | 幂等（`@Idempotent`）、幂等执行器与状态存储 SPI                                                         |
| `redis-plus-queue`      | Redis List/Stream 队列、受控订阅运行时、非阻塞重试、同步拉取 delivery/ACK 语义                                  |
| `redis-plus-governance` | Micrometer 指标（锁/缓存/限流/幂等全覆盖）、Actuator 健康检查、高可用治理扩展                                       |
| `redis-plus-starter`    | Spring Boot 自动装配入口，聚合全部能力模块                                                              |

---

## ✨ 支持的注解

| 注解                      | 模块           | 说明                 |
|-------------------------|--------------|--------------------|
| `@RedisLock`            | `lock`       | 互斥分布式锁             |
| `@RedisReadLock`        | `lock`       | 分布式读锁（读写锁中的读锁）     |
| `@RedisWriteLock`       | `lock`       | 分布式写锁（读写锁中的写锁）     |
| `@RedisDS`              | `datasource` | 方法/类级多数据源路由        |
| `@ThreeLevelCacheable`  | `cache`      | 三级缓存查询             |
| `@ThreeLevelCacheEvict` | `cache`      | 三级缓存失效             |
| `@BloomCheck`           | `enhance`    | 布隆过滤器拦截非法 Key      |
| `@RateLimit`            | `ratelimit`  | 限流（支持多算法）          |
| `@Idempotent`           | `idempotent` | 幂等控制（基于 Redis 状态机） |

---

## ✨ Starter 自动装配内容

当前 `redis-plus-starter` 注册以下自动装配：

| 自动装配                                   | 触发条件                                                        | 说明                                                                                                        |
|----------------------------------------|-------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| `RedisPlusLockAutoConfiguration`       | classpath 中存在 `StringRedisTemplate`                         | 注册 `RedisLockFactory`、`LockAspect`、`SpelLockKeyResolver`（默认）、`DefaultLockLeaseStrategy`（默认）并将默认等待时间接入注解语义 |
| `RedisPlusCacheAutoConfiguration`      | classpath 中存在 `StringRedisTemplate`                         | 注册本地缓存提供者、缓存模板、TTL 策略、缓存 AOP 切面                                                                           |
| `RedisPlusDataSourceAutoConfiguration` | 默认总会装配                                                      | 单数据源模式下兜底包装 Spring 连接工厂；配置 `redis-plus.datasource.sources.*` 时自动切换多数据源，激活 `@RedisDS` 路由切面                 |
| `RedisPlusEnhanceAutoConfiguration`    | 默认装配，布隆切面受 `redis-plus.enhance.bloom.enabled`（默认 `true`）控制  | 注册 `RedisBitmapBloomFilter`（默认布隆过滤器）、`BloomCheckAspect`、`RedisBatchCacheOperations`                       |
| `RedisPlusRateLimitAutoConfiguration`  | classpath 中存在 `StringRedisTemplate`                         | 注册固定窗口/滑动窗口/令牌桶/漏桶四种限流器与 `RateLimitAspect`                                                                |
| `RedisPlusIdempotentAutoConfiguration` | classpath 中存在 `StringRedisTemplate`                         | 注册 `RedisIdempotentExecutor`、`IdempotentAspect`                                                           |
| `RedisPlusQueueAutoConfiguration`      | classpath 中存在 `StringRedisTemplate`                         | 注册 `RedisQueueFactory` 与受控异步执行器，创建带非阻塞重试 / 死信 / 批量轮询配置的队列实例                                               |
| `RedisPlusGovernanceAutoConfiguration` | classpath 中存在 `io.micrometer.core.instrument.MeterRegistry` | 注册 `MicrometerRedisPlusMetrics`（替换 Noop 指标）；classpath 中存在 Actuator 健康类时额外注册 `RedisPlusHealthContributor`  |

---

## 📦 获取依赖

### 环境要求

| 项目          | 版本                |
|-------------|-------------------|
| JDK         | `25`              |
| Spring Boot | `4.0.5+`          |
| Gradle      | `9.x`（Kotlin DSL） |

> 构建已固定使用 JDK 25 toolchain；当前源码不再依赖 `--enable-preview`。

### Gradle（推荐）

```kotlin
dependencies {
    // 推荐：Spring Boot 项目直接接入 starter
  implementation("com.actomize:redis-plus-starter:1.0.1")

    // 可选：启用 L1 Caffeine 本地缓存（不引入则退化为 ConcurrentHashMap）
    implementation("com.github.ben-manes.caffeine:caffeine")

    // 可选：启用 Micrometer 指标 / 健康检查
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // 可选：Prometheus 指标格式
    implementation("io.micrometer:micrometer-registry-prometheus")
}
```

### 按模块引入

```kotlin
dependencies {
    implementation("com.actomize:redis-plus-core:1.0.1")
    implementation("com.actomize:redis-plus-lock:1.0.1")
    implementation("com.actomize:redis-plus-cache:1.0.1")
    implementation("com.actomize:redis-plus-enhance:1.0.1")
    implementation("com.actomize:redis-plus-ratelimit:1.0.1")
    implementation("com.actomize:redis-plus-idempotent:1.0.1")
    implementation("com.actomize:redis-plus-queue:1.0.1")
    implementation("com.actomize:redis-plus-governance:1.0.1")
    implementation("com.actomize:redis-plus-datasource:1.0.1")
}
```

### Maven

```xml

<dependency>
    <groupId>com.actomize</groupId>
    <artifactId>redis-plus-starter</artifactId>
    <version>1.0.1</version>
</dependency>
```

---

## ⚙️ 配置说明

`redis-plus-starter` 当前真实绑定的配置前缀是：

```yaml
redis-plus:
  datasource:    # 多数据源（可选）
  lock:          # 分布式锁
  cache:         # 三级缓存
  enhance:       # 缓存增强（布隆 + 批量）
  ratelimit:     # 限流模块
  idempotent:    # 幂等模块
  queue:         # 队列模块
```

完整参考见：[`配置事例.yml`](配置事例.yml)

一个最小可用示例：

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      database: 0
      timeout: 3s

redis-plus:
  lock:
    key-prefix: "demo:lock:"
    default-lease: 30s
    default-wait: 5s
  cache:
    key-prefix: "demo:cache:"
    default-ttl: 30m
    jitter-ratio: 0.1
    local:
      maximum-size: 10000
      ttl: 5m
  enhance:
    bloom:
      enabled: true
      expected-insertions: 1000000
      false-positive-probability: 0.01
  ratelimit:
    key-prefix: "demo:ratelimit:"
    token-bucket-refill-rate: 100
  idempotent:
    key-prefix: "demo:idempotent:"
  queue:
    key-prefix: "demo:queue:"
    default-consumer-group: my-consumers
    max-retry-attempts: 3
    batch-size: 10
    poll-timeout: 2s
```

说明：

- **未引入 Caffeine**：三级缓存 L1 自动退化为 `ConcurrentHashMap`
- **`@ThreeLevelCacheable.localTtl` 已生效**：L1 TTL 会取 `localTtl` 与 L2 剩余 TTL 的较小值；未配置时与 L2 保持一致
- **未引入 Actuator / Micrometer**：治理模块相关 Bean 不会强行装配
- **`@RateLimit` 参数语义已升级**：
  - 固定窗口 / 滑动窗口：使用 `limit + window + unit`
  - 令牌桶：推荐显式使用 `capacity + refillTokens + refillPeriod + refillUnit`
  - 漏桶：推荐显式使用 `capacity + leakTokens + leakPeriod + leakUnit`
  - 兼容回退：令牌桶未指定 `capacity` 时回退到 `limit`，未指定 `refillTokens` 时回退到
    `redis-plus.ratelimit.token-bucket-refill-rate`
- **锁默认值已真正接线**：`@RedisLock` / `@RedisReadLock` / `@RedisWriteLock` 未显式指定 `waitTime` 时，
  会回退到 `redis-plus.lock.default-wait`；租约由 `LockLeaseStrategy` 统一决策
- **锁降级（写→读）已正式支持**：同一线程持有写锁时，可直接获取同名读锁（`readLock().tryLock()`），
  实现写降级为读；其他线程仍无法获取读锁，直至写锁持有方完全释放
- **`@RedisLock` 切面异常传播已修复**：若业务方法抛出异常且锁同时过期（WatchDog 续期失败），
  原始业务异常会正确传播，不再被 `unlock()` 的 `IllegalMonitorStateException` 屏蔽；
  unlock 失败会以 WARN 日志记录
- **Lua 脚本与 Key 命名已统一复用 core 抽象**：锁与限流路径会走 `RedisScriptExecutor`，
  统一 Key 拼装会走 `KeyNamingStrategy`；锁工厂内置 `DefaultRedisScript` SHA1 缓存，
  避免每次执行重新传输脚本内容
- **队列配置已接入默认工厂**：`redis-plus.queue.key-prefix` 会派生出 List/Stream 前缀，
  `redis-plus.queue.default-consumer-group` 会成为 `RedisQueueFactory#createStreamQueue(queueName, type)` 的默认消费组；
  `max-retry-attempts`、`batch-size`、`poll-timeout` 会直接作用于消费运行时
- **队列 `stop()` 已绑定线程池生命周期**：使用默认（无参/最小参数）构造器时，队列会自持异步执行器；
  调用 `stop()` 后执行器会被同步关闭，不再泄漏后台线程。传入自定义 `asyncExecutor` 时生命周期由调用方管理
- **订阅模型已升级**：`MessageQueue#subscribe(...)` 现在返回 `QueueSubscription`，
  由统一异步执行器托管，停止语义可控
- **同步拉取语义已收紧**：`MessageQueue#receive(...)` 现在返回 `QueueDelivery`；
  可通过 `QueueDelivery#mode()` 判断交付模式：
  `ALREADY_DEQUEUED`（List，消息已出队，ack 为 no-op）和
  `PENDING_ACKNOWLEDGMENT`（Stream，需显式 `acknowledge()` 才会真正 XACK）
- **重试不再阻塞消费线程**：默认队列运行时会通过统一异步执行器调度重试，而不是在消费者线程里 `sleep`
- **幂等 FAILED 重试已序列化**：当幂等状态为 `FAILED` 时，多个并发请求的重试通过 Redis CAS 原子竞争，
  只有一个线程可将状态切换为 `PROCESSING` 并执行业务；其余线程返回"正在处理中"，幂等语义得到保证
- **观测主路径已统一**：lock / ratelimit / idempotent 等运行时路径已统一收敛到 `RedisPlusObserver`；
  `RedisPlusMetrics` 仅作为 cache/governance 过渡适配层保留
- **多 Redis 数据源**：
    - `redis-plus.datasource.sources` 下的每个 **map key 就是路由标签**，与 `@RedisDS` 注解值一一对应
    - `database` 字段是 Redis DB 编号（0~15），不是路由标签
    - `primary` 指定无注解时的默认数据源（必须在 `sources` 中存在）
  - `MultiRedisConnectionFactory` 实现了 `RedisConnectionFactory`，可直接注入到 `RedisTemplate`
    - 也支持自行注册 `MultiRedisConnectionFactory` Bean（编程式高级用法）
- **事件监听**：
  - `RedisPlusEvent` 继承 Spring `ApplicationEvent`，支持类型化 `@EventListener` 监听
  - 示例：`@EventListener public void on(LockAcquiredEvent e) { ... }`

---

## 🔌 SPI 扩展点

所有 SPI 接口均通过 Spring `@ConditionalOnMissingBean` 注册默认实现，用户只需注册同类型 Bean 即可覆盖。

| SPI 接口                        | 模块           | 说明                    | 默认实现                                                             |
|-------------------------------|--------------|-----------------------|------------------------------------------------------------------|
| `LockKeyResolver`             | `lock`       | 锁 Key SpEL 解析         | `SpelLockKeyResolver`                                            |
| `LockLeaseStrategy`           | `lock`       | 锁租约时长策略               | `DefaultLockLeaseStrategy`                                       |
| `LockFailureHandler`          | `lock`       | 锁获取失败处理（抛异常/降级）       | 抛 `RedisLockException`                                           |
| `LockEventListener`           | `lock`       | 锁生命周期事件回调             | 无（注册即生效）                                                         |
| `LocalCacheProvider`          | `cache`      | L1 本地缓存容器             | `CaffeineLocalCacheProvider` / `ConcurrentMapLocalCacheProvider` |
| `CacheLoadProtection`         | `cache`      | 缓存回源保护（防击穿互斥锁）        | 有 lock 模块时用分布式锁，否则 JVM 本地锁                                       |
| `CacheValueSerializer`        | `cache`      | 缓存值序列化                | `JacksonCacheValueSerializer`                                    |
| `BloomHashProvider`           | `enhance`    | 布隆过滤器哈希函数             | FNV-1a 双哈希                                                       |
| `AvalancheProtectionStrategy` | `enhance`    | 雪崩防护（TTL 随机抖动）        | 基于 `ExpireStrategy.randomJitter`                                 |
| `MetricsTagContributor`       | `governance` | 自定义 Micrometer 指标 tag | 无（注册即生效）                                                         |

---

## 🧩 仓库内部依赖约定（面向贡献者）

本仓库已统一使用 version catalog bundles 来组织 Gradle 依赖，当前核心约定如下：

- `*-infra`：基础必需依赖，通常对应 `api`
- `*-optional`：可选集成依赖，通常对应 `compileOnly`
- `*-runtime`：运行时聚合依赖，通常对应 `implementation`

例如：

- `libs.bundles.redis.infra`
- `libs.bundles.redis.aop.optional`
- `libs.bundles.starter.runtime`
- `libs.bundles.starter.optional`

---

## 🚀 本地构建

```bash
./gradlew build
```

如只想验证 Starter：

```bash
./gradlew :redis-plus-starter:build
```

---

## 📋 版本信息

| 属性       | 值              |
|----------|----------------|
| Group    | `com.actomize` |
| Version  | `1.0.1`    |
| JDK      | `25`           |
| Encoding | `UTF-8`        |

---

## 📄 开源协议

本项目基于 [Apache License 2.0](LICENSE) 开源协议发布。


---

<p align="center">
  Made with ❤️ by <a href="https://github.com/guanxiangkai">guanxiangkai</a>
</p>
