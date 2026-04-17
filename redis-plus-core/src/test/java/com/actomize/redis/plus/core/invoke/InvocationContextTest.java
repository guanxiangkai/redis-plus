package com.actomize.redis.plus.core.invoke;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link InvocationContext} 单元测试
 */
class InvocationContextTest {

    @Test
    void of_createsContext() throws Exception {
        Object target = "hello";
        Method method = String.class.getMethod("length");
        Object[] args = {};

        InvocationContext ctx = InvocationContext.of(target, method, args);

        assertSame(target, ctx.getTarget());
        assertSame(method, ctx.getMethod());
        assertEquals("length", ctx.getMethodName());
        assertEquals(0, ctx.getArgumentCount());
    }

    @Test
    void of_defensiveCopy() throws Exception {
        Method method = String.class.getMethod("charAt", int.class);
        Object[] args = {42};

        InvocationContext ctx = InvocationContext.of("hello", method, args);

        // 修改原数组不影响 context
        args[0] = 99;
        assertEquals(42, ctx.getArgument(0));
    }

    @Test
    void of_nullArgs_becomesEmpty() throws Exception {
        Method method = String.class.getMethod("length");
        InvocationContext ctx = InvocationContext.of("hello", method, null);
        assertEquals(0, ctx.getArgumentCount());
    }

    @Test
    void of_nullMethod_throws() {
        assertThrows(NullPointerException.class, () -> InvocationContext.of("hello", null, null));
    }

    @Test
    void getArguments_returnsCopy() throws Exception {
        Method method = String.class.getMethod("charAt", int.class);
        InvocationContext ctx = InvocationContext.of("hello", method, new Object[]{1});

        Object[] a1 = ctx.getArguments();
        Object[] a2 = ctx.getArguments();
        assertNotSame(a1, a2);
        assertEquals(a1[0], a2[0]);
    }
}

