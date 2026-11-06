package com.pps.smartenglish.ai.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Một dòng hàng đợi AI. Mọi chuyển trạng thái đi bằng UPDATE có điều kiện trong AiJobRepository, không sửa entity. */
@Entity
@Table(name = "ai_jobs")
public class AiJob extends Auditable {

    public static final String PENDING = "PENDING";
    public static final String RUNNING = "RUNNING";
    public static final String DONE = "DONE";
    public static final String FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, updatable = false)
    private String kind;

    @Column(nullable = false)
    private String status = PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(updatable = false)
    private JsonNode input;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode output;

    private String error;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "run_after", nullable = false)
    private Instant runAfter;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "session_id", updatable = false)
    private Long sessionId;

    @Column(name = "attempt_id", updatable = false)
    private Long attemptId;

    @Column(nullable = false, updatable = false)
    private boolean charged;

    protected AiJob() {}

    public AiJob(Long userId, AiJobKind kind, JsonNode input, Long sessionId, Long attemptId, boolean charged, Instant now) {
        this.userId = userId;
        this.kind = kind.name();
        this.input = input;
        this.sessionId = sessionId;
        this.attemptId = attemptId;
        this.charged = charged;
        this.runAfter = now;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public AiJobKind getKind() { return AiJobKind.valueOf(kind); }
    public String getStatus() { return status; }
    public JsonNode getInput() { return input; }
    public JsonNode getOutput() { return output; }
    public String getError() { return error; }
    public int getAttempts() { return attempts; }
    public Long getSessionId() { return sessionId; }
    public Long getAttemptId() { return attemptId; }
    public boolean isCharged() { return charged; }

    public boolean isTerminal() { return DONE.equals(status) || FAILED.equals(status); }
}
