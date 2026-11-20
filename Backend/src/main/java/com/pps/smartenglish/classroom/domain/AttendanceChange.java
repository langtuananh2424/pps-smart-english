package com.pps.smartenglish.classroom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** FR-SES-04, D-124: lịch sử sửa điểm danh (giá trị cũ, người sửa, thời điểm). Chỉ thêm, không sửa. */
@Entity
@Table(name = "attendance_changes")
public class AttendanceChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, updatable = false)
    private Long sessionId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "old_status", nullable = false, updatable = false)
    private String oldStatus;

    @Column(name = "new_status", nullable = false, updatable = false)
    private String newStatus;

    @Column(name = "changed_by", nullable = false, updatable = false)
    private Long changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected AttendanceChange() {}

    public AttendanceChange(Long sessionId, Long userId, String oldStatus, String newStatus, Long changedBy, Instant changedAt) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
    }

    public Long getUserId() { return userId; }
    public String getOldStatus() { return oldStatus; }
    public String getNewStatus() { return newStatus; }
    public Long getChangedBy() { return changedBy; }
    public Instant getChangedAt() { return changedAt; }
}
