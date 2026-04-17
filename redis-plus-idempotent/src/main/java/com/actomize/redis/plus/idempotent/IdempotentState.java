package com.actomize.redis.plus.idempotent;

import java.time.Instant;

/**
 * 幂等操作的持久化状态信封
 *
 * <p>取代原先脆弱的字符串协议（{@code PROCESSING} / {@code DONE:json} / {@code FAILED}），
 * 改用结构化 JSON 存储，与序列化器无关，可被任意 JSON 工具读写。
 *
 * <p>存储示例：
 * <pre>{@code
 * {
 *   "status": "DONE",
 *   "resultJson": "{\"id\":1,\"name\":\"Alice\"}",
 *   "resultType": "com.example.UserVO",
 *   "createdAt": "2025-01-01T12:00:00Z"
 * }
 * }</pre>
 */
public final class IdempotentState {

    public enum Status {
        PROCESSING, DONE, FAILED
    }

    private Status status;
    /** 业务结果的 JSON 序列化字符串；仅 DONE 状态有效 */
    private String resultJson;
    /** 业务结果的完整类名；供反序列化时还原具体类型 */
    private String resultType;
    /** 状态写入时间 */
    private Instant createdAt;

    // ── 工厂方法 ─────────────────────────────────────────────────────

    public static IdempotentState processing() {
        IdempotentState s = new IdempotentState();
        s.status = Status.PROCESSING;
        s.createdAt = Instant.now();
        return s;
    }

    public static IdempotentState done(String resultJson, String resultType) {
        if (resultJson != null && resultType == null) {
            throw new IllegalArgumentException("resultType 不能为 null，当 resultJson 不为 null 时必须指定结果类型");
        }
        if (resultJson == null && resultType != null) {
            throw new IllegalArgumentException("resultJson 为 null 时 resultType 也必须为 null");
        }
        IdempotentState s = new IdempotentState();
        s.status = Status.DONE;
        s.resultJson = resultJson;
        s.resultType = resultType;
        s.createdAt = Instant.now();
        return s;
    }

    public static IdempotentState failed() {
        IdempotentState s = new IdempotentState();
        s.status = Status.FAILED;
        s.createdAt = Instant.now();
        return s;
    }

    // ── 访问器（供 Jackson 序列化/反序列化使用） ─────────────────────

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }

    public String getResultType() { return resultType; }
    public void setResultType(String resultType) { this.resultType = resultType; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
