package com.actomize.redis.plus.cache.aop;

import com.actomize.redis.plus.cache.ThreeLevelCacheTemplate;
import com.actomize.redis.plus.cache.annotation.ThreeLevelCacheEvict;
import com.actomize.redis.plus.cache.annotation.ThreeLevelCacheable;
import com.actomize.redis.plus.cache.spi.CacheKeyResolver;
import com.actomize.redis.plus.core.aop.RedisPlusAspectOrder;
import com.actomize.redis.plus.core.invoke.InvocationContext;
import com.actomize.redis.plus.core.invoke.InvocationContexts;
import com.actomize.redis.plus.core.util.ExceptionUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;

import java.time.Duration;

/**
 * 三级缓存 AOP 切面
 *
 * <p>拦截 {@link ThreeLevelCacheable} 和 {@link ThreeLevelCacheEvict} 注解，
 * 将方法返回值类型作为反序列化目标类型。
 */
@Aspect
@Order(RedisPlusAspectOrder.CACHE)
public class ThreeLevelCacheAspect {

    private final ThreeLevelCacheTemplate cacheTemplate;
    private final CacheKeyResolver keyResolver;

    public ThreeLevelCacheAspect(ThreeLevelCacheTemplate cacheTemplate) {
        this(cacheTemplate, null);
    }

    public ThreeLevelCacheAspect(ThreeLevelCacheTemplate cacheTemplate,
                                 CacheKeyResolver keyResolver) {
        this.cacheTemplate = cacheTemplate;
        this.keyResolver = keyResolver;
    }

    @Around("@annotation(cacheable)")
    Object aroundCacheable(ProceedingJoinPoint pjp, ThreeLevelCacheable cacheable) throws Throwable {
        InvocationContext context = invocationContext(pjp);
        String cacheKey = resolveSpel(context, cacheable.key());
        Duration ttl = Duration.of(cacheable.ttl(), cacheable.timeUnit().toChronoUnit());
        Duration localTtl = cacheable.localTtl() > 0
                ? Duration.of(cacheable.localTtl(), cacheable.timeUnit().toChronoUnit())
                : Duration.ZERO;

        @SuppressWarnings("unchecked")
        Class<Object> returnType = (Class<Object>) ((MethodSignature) pjp.getSignature()).getReturnType();

        return cacheTemplate.get(cacheable.name(), cacheKey, returnType, ttl, localTtl, k -> {
            try {
                return pjp.proceed();
            } catch (Throwable t) {
                ExceptionUtils.sneakyThrow(t);
                throw new AssertionError(); // unreachable
            }
        });
    }

    @Around("@annotation(evict)")
    Object aroundEvict(ProceedingJoinPoint pjp, ThreeLevelCacheEvict evict) throws Throwable {
        // beforeInvocation = true：先删缓存再执行方法
        if (evict.beforeInvocation()) {
            doEvict(pjp, evict);
        }
        try {
            return pjp.proceed();
        } finally {
            // beforeInvocation = false（默认）：方法执行后删缓存
            if (!evict.beforeInvocation()) {
                doEvict(pjp, evict);
            }
        }
    }

    private void doEvict(ProceedingJoinPoint pjp, ThreeLevelCacheEvict evict) {
        InvocationContext context = invocationContext(pjp);
        if (evict.allEntries()) {
            cacheTemplate.clear(evict.name());
        } else {
            String cacheKey = resolveSpel(context, evict.key());
            cacheTemplate.evict(evict.name(), cacheKey);
        }
    }

    private InvocationContext invocationContext(ProceedingJoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        return InvocationContext.of(pjp.getTarget(), signature.getMethod(), pjp.getArgs());
    }

    private String resolveSpel(InvocationContext context, String expression) {
        return InvocationContexts.resolveKey(context, expression, keyResolver);
    }

}
