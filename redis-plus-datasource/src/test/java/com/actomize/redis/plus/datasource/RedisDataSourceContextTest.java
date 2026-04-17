package com.actomize.redis.plus.datasource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link RedisDataSourceContext} 单元测试
 */
class RedisDataSourceContextTest {

    @Test
    void get_returnsNullWhenEmpty() {
        assertNull(RedisDataSourceContext.get());
    }

    @Test
    void callWithSource_setsAndRestoresContext() {
        String result = RedisDataSourceContext.callWithSource("slave", () -> {
            assertEquals("slave", RedisDataSourceContext.get());
            return "ok";
        });
        assertEquals("ok", result);
        assertNull(RedisDataSourceContext.get());
    }

    @Test
    void nestedCallWithSource_stackBehavior() {
        RedisDataSourceContext.callWithSource("master", () -> {
            assertEquals("master", RedisDataSourceContext.get());
            RedisDataSourceContext.callWithSource("slave", () -> {
                assertEquals("slave", RedisDataSourceContext.get());
                return null;
            });
            assertEquals("master", RedisDataSourceContext.get());
            return null;
        });
        assertNull(RedisDataSourceContext.get());
    }

    @Test
    void is_checksCurrentSource() {
        assertFalse(RedisDataSourceContext.is("master"));
        RedisDataSourceContext.callWithSource("master", () -> {
            assertTrue(RedisDataSourceContext.is("master"));
            assertFalse(RedisDataSourceContext.is("slave"));
            return null;
        });
    }

    @Test
    void callWithSource_blankName_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                RedisDataSourceContext.callWithSource("", () -> null));
        assertThrows(IllegalArgumentException.class, () ->
                RedisDataSourceContext.callWithSource(null, () -> null));
    }

    @Test
    void runWithSource_setsContext() {
        RedisDataSourceContext.runWithSource("cache", () -> {
            assertEquals("cache", RedisDataSourceContext.get());
        });
        assertNull(RedisDataSourceContext.get());
    }
}

