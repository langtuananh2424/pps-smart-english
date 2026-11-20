package com.pps.smartenglish.classroom.domain;

import com.pps.smartenglish.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** FR-SES-01, FR-SES-02: buổi học nhóm của lớp hoặc buổi kèm 1-1 giữa một trợ giảng và một học viên. */
@Entity
@Table(name = "class_sessions")
public class ClassSession extends Auditable {

    public static final String GROUP = "GROUP";
    public static final String TUTORING = "TUTORING";
    public static final String SCHEDULED = "SCHEDULED";
    public static final String CANCELLED = "CANCELLED";
    public static final String ONLINE = "ONLINE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "class_id", nullable = false, updatable = false)
    private Long classId;

    @Column(nullable = false, updatable = false)
    private String kind;

    @Column(name = "student_id", updatable = false)
    private Long studentId;

    @Column(nullable = false)
    private String mode;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "meeting_url")
    private String meetingUrl;

    @Column(name = "taught_by", nullable = false)
    private Long taughtBy;

    @Column(nullable = false)
    private String status = SCHEDULED;

    @Column(name = "reminded_at")
    private Instant remindedAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    protected ClassSession() {}

    public ClassSession(Long classId, String kind, Long studentId, String mode, Instant startsAt, Instant endsAt,
                        String meetingUrl, Long taughtBy, Long createdBy) {
        this.classId = classId;
        this.kind = kind;
        this.studentId = studentId;
        this.mode = mode;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.meetingUrl = meetingUrl;
        this.taughtBy = taughtBy;
        this.createdBy = createdBy;
    }

    public Long getId() { return id; }
    public Long getClassId() { return classId; }
    public String getKind() { return kind; }
    public Long getStudentId() { return studentId; }
    public String getMode() { return mode; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public String getMeetingUrl() { return meetingUrl; }
    public Long getTaughtBy() { return taughtBy; }
    public String getStatus() { return status; }
    public boolean isTutoring() { return TUTORING.equals(kind); }
    public boolean isCancelled() { return CANCELLED.equals(status); }

    /** Đổi giờ, hình thức, link; đổi giờ thì buổi được nhắc lại. */
    public void change(Instant startsAt, Instant endsAt, String mode, String meetingUrl) {
        if (!this.startsAt.equals(startsAt) || !this.endsAt.equals(endsAt)) {
            this.remindedAt = null;
        }
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.mode = mode;
        this.meetingUrl = meetingUrl;
    }

    public void cancel() { this.status = CANCELLED; }
}
