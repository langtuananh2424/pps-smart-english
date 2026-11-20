package com.pps.smartenglish.classroom.domain;

import com.pps.smartenglish.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** FR-ASG-01, D-112: bài giao cho lớp, trỏ tới một bộ câu hỏi đã xuất bản của khóa (không có bài giáo viên tự soạn). */
@Entity
@Table(name = "assignments")
public class Assignment extends Auditable {

    public static final String ACTIVE = "ACTIVE";
    public static final String CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "class_id", nullable = false, updatable = false)
    private Long classId;

    @Column(name = "bank_id", nullable = false, updatable = false)
    private Long bankId;

    @Column(name = "due_at", nullable = false, updatable = false)
    private Instant dueAt;

    @Column(nullable = false)
    private String status = ACTIVE;

    /** 'lớp:bộ câu hỏi:hạn' khi ACTIVE, null khi hủy: unique của cột này là chốt chống giao trùng. */
    @Column(name = "live_key")
    private String liveKey;

    @Column(name = "reminded_at")
    private Instant remindedAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    protected Assignment() {}

    public Assignment(Long classId, Long bankId, Instant dueAt, Long createdBy) {
        this.classId = classId;
        this.bankId = bankId;
        this.dueAt = dueAt;
        this.createdBy = createdBy;
        this.liveKey = classId + ":" + bankId + ":" + dueAt.toEpochMilli();
    }

    public Long getId() { return id; }
    public Long getClassId() { return classId; }
    public Long getBankId() { return bankId; }
    public Instant getDueAt() { return dueAt; }
    public String getStatus() { return status; }
    public boolean isActive() { return ACTIVE.equals(status); }

    public void cancel() {
        this.status = CANCELLED;
        this.liveKey = null;
    }
}
