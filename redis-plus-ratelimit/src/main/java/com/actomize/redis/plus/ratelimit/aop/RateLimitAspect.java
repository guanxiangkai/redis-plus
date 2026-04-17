package com.actomize.redis.plus.ratelimit.aop;

import com.actomize.redis.plus.core.aop.RedisPlusAspectOrder;
import com.actomize.redis.plus.core.exception.RedisPlusException;
import com.actomize.redis.plus.core.invoke.InvocationContext;
import com.actomize.redis.plus.core.invoke.InvocationContexts;
import com.actomize.redis.plus.core.observation.RedisPlusObservationType;
import com.actomize.redis.plus.core.observation.RedisPlusObserver;
import com.actomize.redis.plus.ratelimit.RateLimitConfig;
import com.actomize.redis.plus.ratelimit.RateLimiter;
import com.actomize.redis.plus.ratelimit.annotation.RateLimit;
import com.actomize.redis.plus.ratelimit.impl.FixedWindowRateLimiter;
import com.actomize.redis.plus.ratelimit.impl.LeakyBucketRateLimiter;
import com.actomize.redis.plus.ratelimit.impl.SlidingWindowRateLimiter;
import com.actomize.redis.plus.ratelimit.impl.TokenBucketRateLimiter;
import com.actomize.redis.plus.ratelimit.spi.RateLimitKeyResolver;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;

import java.time.Duration;
import java.util.Map;

/**
 * 限流 AOP 切面
 *
 * <p>拦截 {@link RateLimit} 注解，根据配置算法选择对应的 {@link RateLimiter} 实现，
 * 并通过 {@link RedisPlusObserver} 接入统一观测。
 */
@Aspect
@Order(RedisPlusAspectOrder.RATELIMIT)
public class RateLimitAspect {

    private final SlidingWindowRateLimiter slidingWindow;
    private final FixedWindowRateLimiter fixedWindow;
    private final TokenBucketRateLimiter tokenBucket;
    private final LeakyBucketRateLimiter leakyBucket;
    private final RateLimitKeyResolver keyResolver;
    private final long defaultTokenBucketRefillTokens;
    private final RedisPlusObserver observer;

    public RateLimitAspect(SlidingWindowRateLimiter slidingWindow,
                           FixedWindowRateLimiter fixedWindow,
                           TokenBucketRateLimiter tokenBucket,
                           LeakyBucketRateLimiter leakyBucket,
                           long defaultTokenBucketRefillTokens) {
        this(slidingWindow, fixedWindow, tokenBucket, leakyBucket, null,
                defaultTokenBucketRefillTokens, RedisPlusObserver.noop());
    }

    public RateLimitAspect(SlidingWindowRateLimiter slidingWindow,
                           FixedWindowRateLimiter fixedWindow,
                           TokenBucketRateLimiter tokenBucket,
                           LeakyBucketRateLimiter leakyBucket,
                           RateLimitKeyResolver keyResolver,
                           long defaultTokenBucketRefillTokens) {
        this(slidingWindow, fixedWindow, tokenBucket, leakyBucket, keyResolver,
                defaultTokenBucketRefillTokens, RedisPlusObserver.noop());
    }

    public RateLimitAspect(SlidingWindowRateLimiter slidingWindow,
                           FixedWindowRateLimiter fixedWindow,
                           TokenBucketRateLimiter tokenBucket,
                           LeakyBucketRateLimiter leakyBucket,
                           RateLimitKeyResolver keyResolver,
                           long defaultTokenBucketRefillTokens,
                           RedisPlusObserver observer) {
        this.slidingWindow = slidingWindow;
        this.fixedWindow = fixedWindow;
        this.tokenBucket = tokenBucket;
        this.leakyBucket = leakyBucket;
        this.keyResolver = keyResolver;
        this.defaultTokenBucketRefillTokens = defaultTokenBucketRefillTokens;
        this.observer = observer != null ? observer : RedisPlusObserver.noop();
    }

    @Around("@annotation(rateLimit)")
    Object around(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        InvocationContext context = invocationContext(pjp);
        String key = resolveSpel(context, rateLimit.key());
        return observer.observe(RedisPlusObservationType.RATELIMIT,
                Map.of("algorithm", rateLimit.algorithm().name().toLowerCase()),
                Map.of("ratelimit.key", key),
                () -> {
                    boolean allowed = switch (rateLimit.algorithm()) {
                        case FIXED_WINDOW -> fixedWindow.tryAcquire(key, fixedWindowConfig(rateLimit));
                        case SLIDING_WINDOW -> slidingWindow.tryAcquire(key, slidingWindowConfig(rateLimit));
                        case TOKEN_BUCKET -> tokenBucket.tryAcquire(key, tokenBucketConfig(rateLimit));
                        case LEAKY_BUCKET -> leakyBucket.tryAcquire(key, leakyBucketConfig(rateLimit));
                    };

                    if (!allowed) {
                        throw new RedisPlusException("请求被限流，key=" + key + "，algorithm=" + rateLimit.algorithm().name());
                    }
                    return pjp.proceed();
                });
    }

    private RateLimitConfig.FixedWindow fixedWindowConfig(RateLimit rateLimit) {
        return new RateLimitConfig.FixedWindow(rateLimit.limit(), durationOf(rateLimit.window(), rateLimit.unit()));
    }

    private RateLimitConfig.SlidingWindow slidingWindowConfig(RateLimit rateLimit) {
        return new RateLimitConfig.SlidingWindow(rateLimit.limit(), durationOf(rateLimit.window(), rateLimit.unit()));
    }

    private RateLimitConfig.TokenBucket tokenBucketConfig(RateLimit rateLimit) {
        long capacity = rateLimit.capacity() > 0 ? rateLimit.capacity() : rateLimit.limit();
        long refillTokens = rateLimit.refillTokens() > 0
                ? rateLimit.refillTokens()
                : defaultTokenBucketRefillTokens;
        Duration refillPeriod = durationOf(rateLimit.refillPeriod(), rateLimit.refillUnit());
        return new RateLimitConfig.TokenBucket(capacity, refillTokens, refillPeriod);
    }

    private RateLimitConfig.LeakyBucket leakyBucketConfig(RateLimit rateLimit) {
        long capacity = rateLimit.capacity() > 0 ? rateLimit.capacity() : rateLimit.limit();
        long leakTokens = rateLimit.leakTokens() > 0 ? rateLimit.leakTokens() : rateLimit.limit();
        Duration leakPeriod = rateLimit.leakTokens() > 0
                ? durationOf(rateLimit.leakPeriod(), rateLimit.leakUnit())
                : durationOf(rateLimit.window(), rateLimit.unit());
        return new RateLimitConfig.LeakyBucket(capacity, leakTokens, leakPeriod);
    }

    private Duration durationOf(long value, java.util.concurrent.TimeUnit unit) {
        return Duration.of(value, unit.toChronoUnit());
    }

    private InvocationContext invocationContext(ProceedingJoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        return InvocationContext.of(pjp.getTarget(), signature.getMethod(), pjp.getArgs());
    }

    private String resolveSpel(InvocationContext context, String expression) {
        return InvocationContexts.resolveKey(context, expression, keyResolver);
    }
}
