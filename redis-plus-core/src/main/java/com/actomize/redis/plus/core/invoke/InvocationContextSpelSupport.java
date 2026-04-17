package com.actomize.redis.plus.core.invoke;

import com.actomize.redis.plus.core.exception.RedisPlusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

/**
 * 基于 {@link InvocationContext} 的 SpEL 求值支持工具。
 *
 * <p>统一处理各模块中重复出现的表达式求值逻辑，避免在 lock / cache / enhance /
 * integration 中重复维护 {@code MethodBasedEvaluationContext}、参数名发现器与解析器样板代码。
 */
public final class InvocationContextSpelSupport {

    private static final Logger log = LoggerFactory.getLogger(InvocationContextSpelSupport.class);

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final DefaultParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    private InvocationContextSpelSupport() {
    }

    /**
     * 将表达式解析为字符串结果。
     *
     * <p>规则：
     * <ul>
     *   <li>{@code null} → 返回 {@code null}</li>
     *   <li>空白字符串 → 返回空字符串</li>
     *   <li>不包含 {@code #} 且不包含单引号 {@code '} → 视为字面量直接返回</li>
     *   <li>其余情况按 SpEL 求值；求值失败则记录 warn 日志并抛出 {@link RedisPlusException}</li>
     * </ul>
     *
     * @throws RedisPlusException 当 SpEL 表达式解析或求值失败时
     */
    public static String resolveToString(InvocationContext context, String expression) {
        if (expression == null) {
            return null;
        }
        if (expression.isBlank()) {
            return "";
        }
        if (!requiresEvaluation(expression)) {
            return expression;
        }
        try {
            EvaluationContext evaluationContext = new MethodBasedEvaluationContext(
                    context.getTarget(), context.getMethod(), context.getArguments(), NAME_DISCOVERER);
            Object value = PARSER.parseExpression(expression).getValue(evaluationContext);
            if (value == null) {
                log.warn("[redis-plus] SpEL 表达式求值结果为 null，表达式: {}", expression);
            }
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.warn("[redis-plus] SpEL 表达式求值失败，表达式: {}", expression, e);
            throw new RedisPlusException("SpEL 表达式求值失败: " + expression, e);
        }
    }

    /**
     * 判断表达式是否需要进入 SpEL 引擎。
     */
    public static boolean requiresEvaluation(String expression) {
        return expression != null && (expression.contains("#") || expression.contains("'"));
    }
}
