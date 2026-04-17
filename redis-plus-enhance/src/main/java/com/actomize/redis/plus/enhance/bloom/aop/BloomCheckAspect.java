package com.actomize.redis.plus.enhance.bloom.aop;

import com.actomize.redis.plus.core.exception.RedisCacheException;
import com.actomize.redis.plus.core.invoke.InvocationContext;
import com.actomize.redis.plus.core.invoke.InvocationContexts;
import com.actomize.redis.plus.enhance.bloom.BloomFilter;
import com.actomize.redis.plus.enhance.bloom.annotation.BloomCheck;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.util.Map;

/**
 * 布隆过滤器前置检查 AOP 切面
 *
 * <p>拦截 {@link BloomCheck} 注解，在方法执行前通过布隆过滤器过滤肯定不存在的 Key。
 */
@Aspect
public class BloomCheckAspect {

    /**
     * 所有已注册的布隆过滤器（key = filter 名称）
     */
    private final Map<String, BloomFilter<String>> filters;

    public BloomCheckAspect(Map<String, BloomFilter<String>> filters) {
        this.filters = filters;
    }

    @Around("@annotation(bloomCheck)")
    Object around(ProceedingJoinPoint pjp, BloomCheck bloomCheck) throws Throwable {
        String filterName = bloomCheck.filter();
        BloomFilter<String> filter = filters.get(filterName);

        if (filter == null) {
            // 过滤器未注册，直接放行（宽容策略）
            return pjp.proceed();
        }

        InvocationContext context = invocationContext(pjp);
        String key = resolveSpel(context, bloomCheck.key());

        if (!filter.mightContain(key)) {
            // 过滤器判断"一定不存在"
            return switch (bloomCheck.onMiss()) {
                case THROW_EXCEPTION -> throw new RedisCacheException(
                        "布隆过滤器拒绝：filter=" + filterName + ", key=" + key);
                case RETURN_NULL -> null;
            };
        }

        return pjp.proceed();
    }

    private InvocationContext invocationContext(ProceedingJoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        return InvocationContext.of(pjp.getTarget(), signature.getMethod(), pjp.getArgs());
    }

    private String resolveSpel(InvocationContext context, String expression) {
        return InvocationContexts.resolveKey(context, expression);
    }
}
