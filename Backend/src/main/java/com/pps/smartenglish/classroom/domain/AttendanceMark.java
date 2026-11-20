package com.pps.smartenglish.classroom.domain;

import com.pps.smartenglish.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** FR-SES-04: điểm danh tay của một người trong một buổi; sửa thì đổi giá trị và ghi AttendanceChange. */
@Entity
@Table(name = "attendance_marks")
public class AttendanceMark extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, updatable = false)
    private Long sessionId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false)
    private String status;

    @Column(name = "marked_by", nullable = false)
    private Long markedBy;

    @Column(name = "marked_at", nullable = false)
    private Instant markedAt;

    protected AttendanceMark() {}

    public AttendanceMark(Long sessionId, Long userId, String status, Long markedBy, Instant markedAt) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.status = status;
        this.markedBy = markedBy;
        this.markedAt = markedAt;
    }

    public Long getSessionId() { return sessionId; }
    public Long getUserId() { return userId; }
    public String getStatus() { return status; }
    public Long getMarkedBy() { return markedBy; }
    public Instant getMarkedAt() { return markedAt; }

    public void edit(String status, Long by, Instant at) {
        this.status = status;
        this.markedBy = by;
        this.markedAt = at;
    }
}
