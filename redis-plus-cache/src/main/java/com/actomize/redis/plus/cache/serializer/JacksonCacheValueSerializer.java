package com.actomize.redis.plus.cache.serializer;

import com.actomize.redis.plus.cache.spi.CacheValueSerializer;
import com.actomize.redis.plus.core.exception.RedisPlusException;
import com.actomize.redis.plus.core.serializer.ValueSerializer;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import java.util.Collections;
import java.util.List;

/**
 * 基于 Jackson 的统一序列化器
 *
 * <p>同时实现 {@link CacheValueSerializer}（缓存模块接口）和
 * {@link ValueSerializer}（core 模块通用接口），消除冗余 Bean。
 * 使用 Jackson DefaultTyping 将类型信息嵌入 JSON，确保反序列化时能还原正确的目标类型。
 * 由 {@code RedisPlusCacheAutoConfiguration} 自动注册为两种类型的 Bean。
 *
 * <p><b>安全说明</b>：使用 {@link BasicPolymorphicTypeValidator} 白名单策略，
 * 仅允许反序列化 {@code com.actomize.redis.plus.}、{@code java.util.}、
 * {@code java.lang.} 和 {@code java.time.} 等已知包内的类型，
 * 防止 Jackson 反序列化 RCE 漏洞（CVE 系列 gadget chain）。
 * 如需扩展允许的包，请继承本类并覆盖 {@link #buildTypeValidator()}。
 */
public class JacksonCacheValueSerializer implements CacheValueSerializer, ValueSerializer {

    private final ObjectMapper objectMapper;
    private final List<String> additionalPackages;

    public JacksonCacheValueSerializer(ObjectMapper objectMapper) {
        this(objectMapper, Collections.emptyList());
    }

    /**
     * @param objectMapper       Jackson ObjectMapper（将被 copy 以避免污染原实例）
     * @param additionalPackages 额外允许反序列化的包名前缀，用于扩展类型白名单
     */
    public JacksonCacheValueSerializer(ObjectMapper objectMapper, List<String> additionalPackages) {
        this.additionalPackages = Collections.unmodifiableList(additionalPackages);
        // 复制 ObjectMapper 并启用 DefaultTyping，避免影响业务 ObjectMapper 配置
        this.objectMapper = objectMapper.copy()
                .activateDefaultTyping(
                        buildTypeValidator(),
                        ObjectMapper.DefaultTyping.NON_FINAL,
                        JsonTypeInfo.As.PROPERTY);
    }

    @Override
    public String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new RedisPlusException("缓存值序列化失败：" + value.getClass().getName(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialize(String data, Class<T> type) {
        if (data == null || data.isBlank()) return null;
        try {
            return objectMapper.readValue(data, type);
        } catch (Exception e) {
            throw new RedisPlusException("缓存值反序列化失败，targetType=" + type.getName(), e);
        }
    }

    /**
     * 构造类型白名单验证器。内置框架包 + 通过配置扩展的用户包。
     * 子类可覆盖此方法进行更细粒度的控制。
     */
    protected BasicPolymorphicTypeValidator buildTypeValidator() {
        BasicPolymorphicTypeValidator.Builder builder = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.actomize.redis.plus.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.lang.")
                .allowIfSubType("java.time.")
                .allowIfSubType("java.math.");
        for (String pkg : additionalPackages) {
            builder.allowIfSubType(pkg);
        }
        return builder.build();
    }
}
