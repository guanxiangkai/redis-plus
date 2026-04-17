package com.actomize.redis.plus.core.event;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEvent;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link RedisPlusEvent} 单元测试
 */
class RedisPlusEventTest {

    @Test
    void extendsApplicationEvent() {
        TestEvent event = new TestEvent("test-key");
        assertInstanceOf(ApplicationEvent.class, event);
    }

    @Test
    void getKey_returnsKey() {
        TestEvent event = new TestEvent("my-key");
        assertEquals("my-key", event.getKey());
    }

    @Test
    void getOccurredAt_notNull() {
        TestEvent event = new TestEvent("key");
        assertNotNull(event.getOccurredAt());
    }

    @Test
    void getSource_isKey() {
        TestEvent event = new TestEvent("my-key");
        assertEquals("my-key", event.getSource());
    }

    @Test
    void toString_containsClassName() {
        TestEvent event = new TestEvent("my-key");
        assertTrue(event.toString().contains("TestEvent"));
        assertTrue(event.toString().contains("my-key"));
    }

    static class TestEvent extends RedisPlusEvent {
        TestEvent(String key) {
            super(key);
        }
    }
}

