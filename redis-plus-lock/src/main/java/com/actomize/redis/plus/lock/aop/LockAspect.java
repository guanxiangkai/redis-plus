package com.actomize.redis.plus.lock.aop;

import com.actomize.redis.plus.core.aop.RedisPlusAspectOrder;
import com.actomize.redis.plus.core.invoke.InvocationContext;
import com.actomize.redis.plus.core.invoke.InvocationContexts;
import com.actomize.redis.plus.core.observation.RedisPlusObservationType;
import com.actomize.redis.plus.core.observation.RedisPlusObserver;
import com.actomize.redis.plus.lock.DistributedLock;
import com.actomize.redis.plus.lock.DistributedReadWriteLock;
import com.actomize.redis.plus.lock.LockDefinition;
import com.actomize.redis.plus.lock.annotation.RedisLock;
import com.actomize.redis.plus.lock.annotation.RedisReadLock;
import com.actomize.redis.plus.lock.annotation.RedisWriteLock;
import com.actomize.redis.plus.lock.event.LockAcquiredEvent;
import com.actomize.redis.plus.lock.event.LockFailedEvent;
import com.actomize.redis.plus.lock.event.LockReleasedEvent;
import com.actomize.redis.plus.lock.impl.RedisLockFactory;
import com.actomize.redis.plus.lock.spi.LockEventListener;
import com.actomize.redis.plus.lock.spi.LockEventPublisher;
import com.actomize.redis.plus.lock.spi.LockFailureHandler;
import com.actomize.redis.plus.lock.spi.LockKeyResolver;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁 AOP 切面
 *
 * <p>拦截 {@link RedisLock}、{@link RedisReadLock}、{@link RedisWriteLock} 注解，
 * 在方法执行前后自动管理分布式锁的获取与释放，并发布锁生命周期事件、
 * 调用 {@link LockEventListener} SPI 回调，同时通过 {@link RedisPlusObserver} 接入统一观测。
 *
 * <p>扩展点：
 * <ul>
 *   <li>{@link LockKeyResolver} — 自定义锁 Key 解析（默认 SpEL）</li>
 *   <li>{@link LockFailureHandler} — 自定义锁获取失败处理（默认抛异常）</li>
 *   <li>{@link LockEventListener} — 监听锁生命周期事件（可注册多个）</li>
 * </ul>
 */
@Aspect
@Order(RedisPlusAspectOrder.LOCK)
public class LockAspect {

    private static final Logger log = LoggerFactory.getLogger(LockAspect.class);

    private final RedisLockFactory lockFactory;
    private final LockKeyResolver keyResolver;
    private final LockFailureHandler failureHandler;
    private final Duration defaultWait;
    private final RedisPlusObserver observer;
    private final List<LockEventListener> eventListeners;
    private final LockEventPublisher eventPublisher;

    public LockAspect(RedisLockFactory lockFactory,
                      LockKeyResolver keyResolver,
                      LockFailureHandler failureHandler,
                      List<LockEventListener> eventListeners,
                      LockEventPublisher eventPublisher) {
        this(lockFactory, keyResolver, failureHandler,
                LockDefinition.DEFAULT_WAIT_TIME, RedisPlusObserver.noop(), eventListeners, eventPublisher);
    }

    public LockAspect(RedisLockFactory lockFactory,
                      LockKeyResolver keyResolver,
                      LockFailureHandler failureHandler,
                      Duration defaultWait,
                      List<LockEventListener> eventListeners,
                      LockEventPublisher eventPublisher) {
        this(lockFactory, keyResolver, failureHandler, defaultWait,
                RedisPlusObserver.noop(), eventListeners, eventPublisher);
    }

    public LockAspect(RedisLockFactory lockFactory,
                      LockKeyResolver keyResolver,
                      LockFailureHandler failureHandler,
                      Duration defaultWait,
                      RedisPlusObserver observer,
                      List<LockEventListener> eventListeners,
                      LockEventPublisher eventPublisher) {
        this.lockFactory = lockFactory;
        this.keyResolver = keyResolver;
        this.failureHandler = failureHandler;
        this.defaultWait = defaultWait;
        this.observer = observer != null ? observer : RedisPlusObserver.noop();
        this.eventListeners = eventListeners != null ? List.copyOf(eventListeners) : Collections.emptyList();
        this.eventPublisher = eventPublisher;
    }

    // ── Around 切面 ──────────────────────────────────────────────────

    @Around("@annotation(redisLock)")
    Object aroundLock(ProceedingJoinPoint pjp, RedisLock redisLock) throws Throwable {
        InvocationContext context = invocationContext(pjp);
        String lockKey = resolveKey(context, redisLock.key());
        LockDefinition definition = toDefinition(lockKey, redisLock.waitTime(), redisLock.leaseTime(), redisLock.timeUnit());
        DistributedLock lock = lockFactory.getLock(definition);
        return doWithLock(pjp, lock, definition);
    }

    @Around("@annotation(readLock)")
    Object aroundReadLock(ProceedingJoinPoint pjp, RedisReadLock readLock) throws Throwable {
        InvocationContext context = invocationContext(pjp);
        String lockKey = resolveKey(context, readLock.key());
        LockDefinition definition = toDefinition(lockKey, readLock.waitTime(), readLock.leaseTime(), readLock.timeUnit());
        DistributedReadWriteLock rwLock = lockFactory.getReadWriteLock(definition);
        return doWithLock(pjp, rwLock.readLock(), definition);
    }

    @Around("@annotation(writeLock)")
    Object aroundWriteLock(ProceedingJoinPoint pjp, RedisWriteLock writeLock) throws Throwable {
        InvocationContext context = invocationContext(pjp);
        String lockKey = resolveKey(context, writeLock.key());
        LockDefinition definition = toDefinition(lockKey, writeLock.waitTime(), writeLock.leaseTime(), writeLock.timeUnit());
        DistributedReadWriteLock rwLock = lockFactory.getReadWriteLock(definition);
        return doWithLock(pjp, rwLock.writeLock(), definition);
    }

    // ── 核心获取/释放流程 ─────────────────────────────────────────────

    private Object doWithLock(ProceedingJoinPoint pjp, DistributedLock lock,
                              LockDefinition definition) throws Throwable {
        String lockKey = definition.name();
        return observer.observe(RedisPlusObservationType.LOCK,
                Map.of(),
                Map.of("lock.key", lockKey),
                () -> {
                    Instant waitStart = Instant.now();
                    long leaseMillis = definition.isWatchDogEnabled() ? -1L : definition.leaseTime().toMillis();
                    boolean acquired = lock.tryLock(definition.waitTime().toMillis(), leaseMillis, TimeUnit.MILLISECONDS);
                    Duration waited = Duration.between(waitStart, Instant.now());

                    if (!acquired) {
                        LockFailedEvent failedEvent = new LockFailedEvent(lockKey, "等待超时");
                        publishEvent(failedEvent);
                        notifyListeners(l -> l.onFailed(failedEvent));

                        LockFailureHandler handler = failureHandler != null ? failureHandler : LockFailureHandler.throwException();
                        return handler.handle(definition);
                    }

                    LockAcquiredEvent acquiredEvent = new LockAcquiredEvent(lockKey, waited);
                    publishEvent(acquiredEvent);
                    notifyListeners(l -> l.onAcquired(acquiredEvent));

                    Instant holdStart = Instant.now();
                    Throwable businessEx = null;
                    try {
                        return pjp.proceed();
                    } catch (Throwable t) {
                        businessEx = t;
                        throw t;
                    } finally {
                        try {
                            lock.unlock();
                        } catch (IllegalMonitorStateException e) {
                            if (businessEx != null) {
                                // 锁已过期（WatchDog 续期失败），不屏蔽原始业务异常
                                log.warn("[redis-plus] 锁释放失败（锁已过期），原始业务异常将继续传播，lockKey={}", lockKey, e);
                            } else {
                                throw e;
                            }
                        }
                        Duration held = Duration.between(holdStart, Instant.now());
                        LockReleasedEvent releasedEvent = new LockReleasedEvent(lockKey, held);
                        publishEvent(releasedEvent);
                        notifyListeners(l -> l.onReleased(releasedEvent));
                    }
                });
    }

    // ── Key 解析 ─────────────────────────────────────────────────────

    private InvocationContext invocationContext(ProceedingJoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        return InvocationContext.of(pjp.getTarget(), signature.getMethod(), pjp.getArgs());
    }

    private String resolveKey(InvocationContext context, String expression) {
        return InvocationContexts.resolveKey(context, expression, keyResolver);
    }

    private LockDefinition toDefinition(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit) {
        Duration resolvedWait = waitTime < 0
                ? defaultWait
                : Duration.of(waitTime, timeUnit.toChronoUnit());
        Duration resolvedLease = leaseTime < 0
                ? Duration.ZERO
                : Duration.of(leaseTime, timeUnit.toChronoUnit());
        return new LockDefinition(lockKey, resolvedLease, resolvedWait, true);
    }

    // ── 工具方法 ─────────────────────────────────────────────────────

    private void publishEvent(Object event) {
        if (eventPublisher != null) {
            try {
                eventPublisher.publish(event);
            } catch (Exception ex) {
                log.warn("[redis-plus] 锁事件发布失败，event={}", event, ex);
            }
        }
    }

    private void notifyListeners(java.util.function.Consumer<LockEventListener> action) {
        eventListeners.forEach(l -> {
            try {
                action.accept(l);
            } catch (Exception ex) {
                log.warn("[redis-plus] 锁事件监听器回调失败，listener={}", l.getClass().getName(), ex);
            }
        });
    }
}
