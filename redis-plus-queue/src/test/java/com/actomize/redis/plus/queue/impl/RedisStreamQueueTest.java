package com.actomize.redis.plus.queue.impl;

import com.actomize.redis.plus.core.serializer.ValueSerializer;
import com.actomize.redis.plus.queue.spi.DeadLetterHandler;
import com.actomize.redis.plus.queue.spi.QueueRetryStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisStreamQueueTest {

    private StringRedisTemplate redisTemplate;
    private StreamOperations<String, Object, Object> streamOps;
    private ValueSerializer serializer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        streamOps = mock(StreamOperations.class);
        serializer = mock(ValueSerializer.class);
        when(redisTemplate.opsForStream()).thenReturn((StreamOperations) streamOps);
    }

    @Test
    void ensureGroupExists_bootstrapsMissingStream() {
        RecordId bootstrapId = RecordId.of("1-0");
        when(streamOps.createGroup("stream:orders", ReadOffset.from("0-0"), "group-a"))
                .thenThrow(new RuntimeException("ERR The XGROUP command requires the key to exist"))
                .thenReturn("OK");
        when(streamOps.add(any(MapRecord.class))).thenReturn(bootstrapId);

        new RedisStreamQueue<>(
                "orders", "group-a", "stream:", String.class, redisTemplate, serializer,
                null, QueueRetryStrategy.noRetry(), DeadLetterHandler.logAndDiscard(),
                Duration.ofSeconds(1), 10);

        verify(streamOps, times(2)).createGroup("stream:orders", ReadOffset.from("0-0"), "group-a");
        verify(streamOps).add(argThat(record ->
                "stream:orders".equals(record.getStream())
                        && "group-a".equals(record.getValue().get("__redis_plus_bootstrap__"))));
        verify(streamOps).delete("stream:orders", bootstrapId);
    }

    @Test
    void ensureGroupExists_busyGroupSkipsBootstrap() {
        when(streamOps.createGroup("stream:orders", ReadOffset.from("0-0"), "group-a"))
                .thenThrow(new RuntimeException("BUSYGROUP Consumer Group name already exists"));

        new RedisStreamQueue<>(
                "orders", "group-a", "stream:", String.class, redisTemplate, serializer,
                null, QueueRetryStrategy.noRetry(), DeadLetterHandler.logAndDiscard(),
                Duration.ofSeconds(1), 10);

        verify(streamOps, never()).add(any(MapRecord.class));
        verify(streamOps, never()).delete(anyString(), any(RecordId.class));
    }

    @Test
    void send_usesAtomicConditionalTrimScript() {
        when(streamOps.createGroup("stream:orders", ReadOffset.from("0-0"), "group-a")).thenReturn("OK");
        when(serializer.serialize("payload")).thenReturn("payload-json");
        when(streamOps.add(any(MapRecord.class))).thenReturn(RecordId.of("2-0"));
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any())).thenReturn(0L);

        RedisStreamQueue<String> queue = new RedisStreamQueue<>(
                "orders", "group-a", "stream:", String.class, redisTemplate, serializer,
                null, QueueRetryStrategy.noRetry(), DeadLetterHandler.logAndDiscard(),
                Duration.ofSeconds(1), 10, null, false, Duration.ofMinutes(5), 128);

        queue.send("payload");

        ArgumentCaptor<DefaultRedisScript<Long>> scriptCaptor = ArgumentCaptor.forClass(DefaultRedisScript.class);
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(scriptCaptor.capture(), keysCaptor.capture(), eq("group-a"), eq("128"));
        assertTrue(scriptCaptor.getValue().getScriptAsString().contains("XPENDING"));
        assertTrue(scriptCaptor.getValue().getScriptAsString().contains("XTRIM"));
        assertEquals(List.of("stream:orders"), keysCaptor.getValue());
        verify(streamOps, never()).trim(anyString(), anyLong(), anyBoolean());
    }
}
