package com.pps.smartenglish.shared.audit;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Chỉ ghi thêm (FR-ADM-05): không có setter, repository không có update/delete. */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private Long actorId;

    @Column(nullable = false, updatable = false)
    private String action;

    @Column(name = "target_type", nullable = false, updatable = false)
    private String targetType;

    @Column(name = "target_id", nullable = false, updatable = false)
    private String targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_value", updatable = false)
    private JsonNode beforeValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_value", updatable = false)
    private JsonNode afterValue;

    @Column(updatable = false)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {}

    AuditLog(long actorId, String action, String targetType, String targetId, JsonNode before, JsonNode after,
             String reason, Instant createdAt) {
        this.actorId = actorId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.beforeValue = before;
        this.afterValue = after;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getActorId() { return actorId; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public JsonNode getBeforeValue() { return beforeValue; }
    public JsonNode getAfterValue() { return afterValue; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
