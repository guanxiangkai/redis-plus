package com.actomize.redis.plus.datasource.aop;

import com.actomize.redis.plus.core.aop.RedisPlusAspectOrder;
import com.actomize.redis.plus.datasource.RedisDataSourceContext;
import com.actomize.redis.plus.datasource.annotation.RedisDS;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

/**
 * {@link RedisDS} 注解 AOP 切面
 *
 * <p>在目标方法执行期间通过 {@link RedisDataSourceContext#callWithSourceThrowing(String, RedisDataSourceContext.ThrowingSupplier)}
 * 临时切换数据源，
 * 方法返回（含异常）后自动恢复，不会影响外层作用域。
 *
 * <p>Order 设为 {@link org.springframework.core.Ordered#HIGHEST_PRECEDENCE} + 1，
 * 确保在事务切面之前执行（连接工厂在事务开始前已路由到正确数据源）。
 */
@Aspect
@Order(RedisPlusAspectOrder.DATASOURCE)
public class RedisDSAspect {

    @Around("@annotation(redisDS)")
    Object around(ProceedingJoinPoint pjp, RedisDS redisDS) throws Throwable {
        return RedisDataSourceContext.callWithSourceThrowing(redisDS.value(), pjp::proceed);
    }

    @Around("!@annotation(com.actomize.redis.plus.datasource.annotation.RedisDS) "
            + "&& @within(redisDS)")
    Object aroundClass(ProceedingJoinPoint pjp, RedisDS redisDS) throws Throwable {
        return RedisDataSourceContext.callWithSourceThrowing(redisDS.value(), pjp::proceed);
    }
}
