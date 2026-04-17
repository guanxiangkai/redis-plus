package com.actomize.redis.plus.datasource;

import com.actomize.redis.plus.core.exception.RedisPlusException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link MultiRedisConnectionFactory} 单元测试
 */
class MultiRedisConnectionFactoryTest {

    @Test
    void determine_defaultToPrimary() {
        RedisConnectionFactory primary = mock(RedisConnectionFactory.class);
        RedisConnectionFactory slave = mock(RedisConnectionFactory.class);

        MultiRedisConnectionFactory multi = new MultiRedisConnectionFactory(
                Map.of("primary", primary, "slave", slave), "primary", false);

        assertSame(primary, multi.determine());
    }

    @Test
    void determine_routesByContext() {
        RedisConnectionFactory primary = mock(RedisConnectionFactory.class);
        RedisConnectionFactory slave = mock(RedisConnectionFactory.class);

        MultiRedisConnectionFactory multi = new MultiRedisConnectionFactory(
                Map.of("primary", primary, "slave", slave), "primary", false);

        RedisDataSourceContext.callWithSource("slave", () -> {
            assertSame(slave, multi.determine());
            return null;
        });
    }

    @Test
    void determine_strict_throwsOnUnknown() {
        RedisConnectionFactory primary = mock(RedisConnectionFactory.class);

        MultiRedisConnectionFactory multi = new MultiRedisConnectionFactory(
                Map.of("primary", primary), "primary", true);

        RedisDataSourceContext.callWithSource("unknown", () -> {
            assertThrows(RedisPlusException.class, multi::determine);
            return null;
        });
    }

    @Test
    void determine_nonStrict_fallbackToPrimary() {
        RedisConnectionFactory primary = mock(RedisConnectionFactory.class);

        MultiRedisConnectionFactory multi = new MultiRedisConnectionFactory(
                Map.of("primary", primary), "primary", false);

        RedisDataSourceContext.callWithSource("unknown", () -> {
            assertSame(primary, multi.determine());
            return null;
        });
    }

    @Test
    void getConnection_delegatesToDetermined() {
        RedisConnection mockConn = mock(RedisConnection.class);
        RedisConnectionFactory primary = mock(RedisConnectionFactory.class);
        when(primary.getConnection()).thenReturn(mockConn);

        MultiRedisConnectionFactory multi = new MultiRedisConnectionFactory(
                Map.of("primary", primary), "primary", false);

        assertSame(mockConn, multi.getConnection());
    }

    @Test
    void implementsRedisConnectionFactory() {
        RedisConnectionFactory primary = mock(RedisConnectionFactory.class);
        MultiRedisConnectionFactory multi = new MultiRedisConnectionFactory(
                Map.of("primary", primary), "primary", false);

        // 可以当作 RedisConnectionFactory 使用
        RedisConnectionFactory asInterface = multi;
        assertNotNull(asInterface);
    }

    @Test
    void constructor_missingPrimary_throws() {
        RedisConnectionFactory slave = mock(RedisConnectionFactory.class);
        assertThrows(RedisPlusException.class, () ->
                new MultiRedisConnectionFactory(Map.of("slave", slave), "primary", false));
    }

    @Test
    void sourceNames_returnsAllNames() {
        RedisConnectionFactory f1 = mock(RedisConnectionFactory.class);
        RedisConnectionFactory f2 = mock(RedisConnectionFactory.class);

        MultiRedisConnectionFactory multi = new MultiRedisConnectionFactory(
                Map.of("primary", f1, "slave", f2), "primary", false);

        assertTrue(multi.sourceNames().contains("primary"));
        assertTrue(multi.sourceNames().contains("slave"));
        assertEquals(2, multi.sourceNames().size());
    }

    @Test
    void routeStrategy_usedWhenNoContext() {
        RedisConnectionFactory primary = mock(RedisConnectionFactory.class);
        RedisConnectionFactory slave = mock(RedisConnectionFactory.class);

        MultiRedisConnectionFactory multi = new MultiRedisConnectionFactory(
                Map.of("primary", primary, "slave", slave), "primary", false,
                () -> "slave");

        assertSame(slave, multi.determine());
    }
}

