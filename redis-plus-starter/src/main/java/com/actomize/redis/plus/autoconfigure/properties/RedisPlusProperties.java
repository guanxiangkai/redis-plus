package com.actomize.redis.plus.autoconfigure.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * redis-plus 统一配置属性
 *
 * <p>配置前缀：{@code redis-plus}
 *
 * <p>示例配置：
 * <pre>
 * redis-plus:
 *   lock:
 *     key-prefix: "myapp:lock:"
 *     default-lease: 30s
 *     default-wait: 5s
 *   cache:
 *     key-prefix: "myapp:cache:"
 *     default-ttl: 30m
 *     local:
 *       maximum-size: 10000
 *       ttl: 5m
 *   enhance:
 *     bloom:
 *       enabled: true
 *   ratelimit:
 *     key-prefix: "myapp:ratelimit:"
 *     token-bucket-refill-rate: 100
 *   idempotent:
 *     key-prefix: "myapp:idempotent:"
 *   queue:
 *     key-prefix: "myapp:queue:"
 *     default-consumer-group: "my-consumers"
 * </pre>
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "redis-plus")
public class RedisPlusProperties {

    @Valid
    @NestedConfigurationProperty
    private LockProperties lock = new LockProperties();

    @Valid
    @NestedConfigurationProperty
    private CacheProperties cache = new CacheProperties();

    @Valid
    @NestedConfigurationProperty
    private EnhanceProperties enhance = new EnhanceProperties();

    @Valid
    @NestedConfigurationProperty
    private DataSourceProperties datasource = new DataSourceProperties();

    @Valid
    @NestedConfigurationProperty
    private RateLimitProperties ratelimit = new RateLimitProperties();

    @Valid
    @NestedConfigurationProperty
    private IdempotentProperties idempotent = new IdempotentProperties();

    @Valid
    @NestedConfigurationProperty
    private QueueProperties queue = new QueueProperties();

    // ── 嵌套配置类 ────────────────────────────────────────────────────

    @Getter
    @Setter
    public static class LockProperties {
        /** 锁 Key 前缀 */
        @NotBlank
        private String keyPrefix = "redis-plus:lock:";
        /** 默认租约时长 */
        @NotNull
        private Duration defaultLease = Duration.ofSeconds(30);
        /** 默认等待时长 */
        @NotNull
        private Duration defaultWait = Duration.ofSeconds(5);
    }

    @Getter
    @Setter
    public static class CacheProperties {
        /** 缓存 Key 前缀（可选） */
        private String keyPrefix = "";
        /** 默认 TTL */
        @NotNull
        private Duration defaultTtl = Duration.ofMinutes(30);
        /** TTL 随机抖动比例（防雪崩，0 表示不抖动） */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double jitterRatio = 0.1;
        /**
         * 允许 Jackson 反序列化的额外包名前缀列表（扩展类型白名单）。
         * 示例：{@code ["com.example.", "org.myapp."]}
         */
        private List<String> allowedPackages = new ArrayList<>();

        @Valid
        @NestedConfigurationProperty
        private LocalCacheProperties local = new LocalCacheProperties();
    }

    @Getter
    @Setter
    public static class LocalCacheProperties {
        /** L1 最大缓存条目数 */
        @Positive
        private long maximumSize = 10_000;
        /** L1 缓存 TTL */
        @NotNull
        private Duration ttl = Duration.ofMinutes(5);
    }

    @Getter
    @Setter
    public static class EnhanceProperties {
        /** 布隆过滤器配置 */
        @Valid
        @NestedConfigurationProperty
        private BloomProperties bloom = new BloomProperties();
    }

    @Getter
    @Setter
    public static class BloomProperties {
        /** 是否启用布隆过滤器自动配置 */
        private boolean enabled = true;
        /** 预期元素数量 */
        @Positive
        private long expectedInsertions = 1_000_000;
        /** 目标误判率 */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double falsePositiveProbability = 0.01;
        /**
         * 过滤器版本号。修改 {@code expectedInsertions} 或 {@code falsePositiveProbability} 后
         * 递增此值，旧版本的 Redis Bitmap 数据将被自动隔离，避免误判率偏离预期。
         */
        @Positive
        private int version = 1;
    }

    @Getter
    @Setter
    public static class RateLimitProperties {
        /** 限流 Key 前缀 */
        @NotBlank
        private String keyPrefix = "redis-plus:ratelimit:";
        /** 令牌桶默认每个补充周期新增的令牌数 */
        @Positive
        private long tokenBucketRefillRate = 100;
    }

    @Getter
    @Setter
    public static class IdempotentProperties {
        /** 幂等 Key 前缀 */
        @NotBlank
        private String keyPrefix = "redis-plus:idempotent:";
        /**
         * PROCESSING 状态保留时长。
         *
         * <p>应设置为业务方法最大执行时间加一个安全缓冲（默认 10 分钟）。
         * 与业务结果 TTL 无关：结果 TTL 控制 DONE 状态多久过期；
         * 此配置控制"正在处理中"标记多久超时，超时后允许并发重试。
         * 过短会导致慢任务被重复执行，过长会导致超时任务长时间无法重试。
         */
        @NotNull
        private Duration processingTimeout = Duration.ofMinutes(10);
    }

    @Getter
    @Setter
    public static class QueueProperties {
        /** 队列 Key 前缀基名 */
        @NotBlank
        private String keyPrefix = "redis-plus:queue:";
        /** 默认消费组名称 */
        @NotBlank
        private String defaultConsumerGroup = "redis-plus-consumers";
        /** 消费失败时最大重试次数 */
        @Min(0)
        private int maxRetryAttempts = 3;
        /** Stream 队列每次批量拉取消息数 */
        @Positive
        private int batchSize = 10;
        /**
         * 消费轮询阻塞等待时长
         */
        @NotNull
        private Duration pollTimeout = Duration.ofSeconds(2);
        /**
         * 是否在消费者启动时自动回收 PEL 中的悬挂消息（仅 Stream 队列有效）。
         * 开启后，{@code subscribe()} 启动时会先执行一次 {@code reclaimPending(pendingReclaimIdleTime, consumer)}。
         *
         * <p>默认 {@code true}：Stream 队列声明 at-least-once 语义，消费者崩溃后重启必须回收
         * PEL 中的悬挂消息，否则这些消息将永久丢失。如需旧行为（忽略 PEL），可显式设为 false。
         */
        private boolean reclaimOnStart = true;
        /**
         * 自动回收 PEL 消息的最短空闲阈值（仅 {@code reclaimOnStart=true} 时生效）。
         * 空闲时长超过此值的 pending 消息才会被认领并重新派发。
         */
        @NotNull
        private Duration pendingReclaimIdleTime = Duration.ofMinutes(5);
        /**
         * Stream 队列最大消息数上限。大于 0 时，每次 {@code send()} 后自动执行
         * {@code XTRIM MAXLEN ~} 近似裁剪，将 Stream 长度控制在该值附近；
         * 设为 0（默认）则不裁剪，Stream 无限增长。
         */
        @Min(0)
        private long maxStreamLength = 0;
    }

    // ── 多数据源配置 ──────────────────────────────────────────────────

    /**
     * 多 Redis 数据源配置。
     *
     * <p>当 {@code sources} 不为空时，starter 自动为每个数据源创建 {@link org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory}
     * 并组装到 {@link com.actomize.redis.plus.datasource.MultiRedisConnectionFactory}；
     * 当 {@code sources} 为空时，退化为包装 {@code spring.data.redis.*} 的单数据源。
     */
    @Getter
    @Setter
    public static class DataSourceProperties {
        /**
         * 多数据源定义：key 为数据源名称（与 {@code @RedisDS} 注解值对应），value 为连接配置。
         * 为空则退化为单数据源模式（使用 spring.data.redis.* 配置）。
         */
        private Map<String, RedisSourceProperties> sources = new LinkedHashMap<>();
        /** 默认数据源名称，必须在 sources 中存在 */
        @NotBlank
        private String primary = "primary";
        /** 严格模式：路由到未定义数据源时抛异常而非回退 primary */
        private boolean strict = false;
    }

    /**
     * 单个 Redis 数据源的连接配置（对应 sources 下每一项）。
     */
    @Getter
    @Setter
    public static class RedisSourceProperties {
        @NotBlank
        private String host = "localhost";
        @Positive
        private int port = 6379;
        private String password = "";
        @Min(0)
        private int database = 0;
        @NotNull
        private Duration timeout = Duration.ofSeconds(3);
        @NotNull
        private Duration connectTimeout = Duration.ofSeconds(3);

        @Valid
        @NestedConfigurationProperty
        private PoolProperties pool = new PoolProperties();
    }

    /**
     * 连接池配置（对应每个数据源下的 pool 节点）。
     */
    @Getter
    @Setter
    public static class PoolProperties {
        private boolean enabled = true;
        @Positive
        private int maxActive = 16;
        @Positive
        private int maxIdle = 8;
        @Min(0)
        private int minIdle = 2;
        @NotNull
        private Duration maxWait = Duration.ofSeconds(3);
    }
}
