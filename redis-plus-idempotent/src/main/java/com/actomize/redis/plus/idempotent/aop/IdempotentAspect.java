package com.actomize.redis.plus.idempotent.aop;

import com.actomize.redis.plus.core.aop.RedisPlusAspectOrder;
import com.actomize.redis.plus.core.invoke.InvocationContext;
import com.actomize.redis.plus.core.invoke.InvocationContexts;
import com.actomize.redis.plus.core.util.ExceptionUtils;
import com.actomize.redis.plus.idempotent.IdempotentExecutor;
import com.actomize.redis.plus.idempotent.annotation.Idempotent;
import com.actomize.redis.plus.idempotent.spi.IdempotentKeyResolver;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;

import java.time.Duration;

/**
 * 幂等 AOP 切面
 *
 * <p>拦截 {@link Idempotent} 注解，通过 {@link IdempotentExecutor} 保证方法幂等执行。
 */
@Aspect
@Order(RedisPlusAspectOrder.IDEMPOTENT)
public class IdempotentAspect {

    private final IdempotentExecutor executor;
    private final IdempotentKeyResolver keyResolver;

    public IdempotentAspect(IdempotentExecutor executor) {
        this(executor, null);
    }

    public IdempotentAspect(IdempotentExecutor executor,
                            IdempotentKeyResolver keyResolver) {
        this.executor = executor;
        this.keyResolver = keyResolver;
    }

    @Around("@annotation(idempotent)")
    Object around(ProceedingJoinPoint pjp, Idempotent idempotent) throws Throwable {
        InvocationContext context = invocationContext(pjp);
        String key = resolveSpel(context, idempotent.key());
        Duration ttl = Duration.of(idempotent.ttl(), idempotent.unit().toChronoUnit());

        return executor.executeOnce(key, ttl, () -> {
            try {
                return pjp.proceed();
            } catch (Throwable t) {
                ExceptionUtils.sneakyThrow(t);
                throw new AssertionError(); // unreachable
            }
        });
    }

    private InvocationContext invocationContext(ProceedingJoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        return InvocationContext.of(pjp.getTarget(), signature.getMethod(), pjp.getArgs());
    }

    private String resolveSpel(InvocationContext context, String expression) {
        return InvocationContexts.resolveKey(context, expression, keyResolver);
    }
}
