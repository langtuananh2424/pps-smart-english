package com.pps.smartenglish.classroom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** FR-CLS-06: một lần mở sớm unit cho cả lớp (userId null) hoặc một học viên. Thu hồi bằng revokedAt, không xóa. */
@Entity
@Table(name = "unlock_overrides")
public class UnlockOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "class_id", nullable = false, updatable = false)
    private Long classId;

    @Column(name = "topic_id", nullable = false, updatable = false)
    private Long topicId;

    @Column(name = "user_id", updatable = false)
    private Long userId;

    @Column(name = "granted_by", nullable = false, updatable = false)
    private Long grantedBy;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** 'lớp:unit:học viên|ALL' khi còn hiệu lực, null khi đã thu hồi: unique của cột này chặn mở trùng. */
    @Column(name = "live_key")
    private String liveKey;

    protected UnlockOverride() {}

    public UnlockOverride(Long classId, Long topicId, Long userId, Long grantedBy, Instant grantedAt) {
        this.classId = classId;
        this.topicId = topicId;
        this.userId = userId;
        this.grantedBy = grantedBy;
        this.grantedAt = grantedAt;
        this.liveKey = classId + ":" + topicId + ":" + (userId == null ? "ALL" : userId);
    }

    public Long getId() { return id; }
    public Long getClassId() { return classId; }
    public Long getTopicId() { return topicId; }
    public Long getUserId() { return userId; }
    public Long getGrantedBy() { return grantedBy; }
    public Instant getGrantedAt() { return grantedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public boolean isRevoked() { return revokedAt != null; }

    public void revoke(Instant at) {
        this.revokedAt = at;
        this.liveKey = null;
    }
}
