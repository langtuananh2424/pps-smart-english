package com.pps.smartenglish.classroom.domain;

import com.pps.smartenglish.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/** FR-CLS-04: phân công giáo viên chính hoặc trợ giảng. Đang phụ trách = chưa thu hồi và hôm nay nằm trong khoảng ngày. */
@Entity
@Table(name = "class_staff")
public class ClassStaff extends Auditable {

    public static final String TEACHER = "TEACHER";
    public static final String ASSISTANT = "ASSISTANT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "class_id", nullable = false, updatable = false)
    private Long classId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, updatable = false)
    private String role;

    @Column(name = "assigned_from", nullable = false)
    private LocalDate assignedFrom;

    @Column(name = "assigned_to")
    private LocalDate assignedTo;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected ClassStaff() {}

    public ClassStaff(Long classId, Long userId, String role, LocalDate from, LocalDate to) {
        this.classId = classId;
        this.userId = userId;
        this.role = role;
        this.assignedFrom = from;
        this.assignedTo = to;
    }

    public Long getId() { return id; }
    public Long getClassId() { return classId; }
    public Long getUserId() { return userId; }
    public String getRole() { return role; }
    public LocalDate getAssignedFrom() { return assignedFrom; }
    public LocalDate getAssignedTo() { return assignedTo; }
    public Instant getRevokedAt() { return revokedAt; }

    public boolean isActiveOn(LocalDate day) {
        return revokedAt == null && !assignedFrom.isAfter(day) && (assignedTo == null || !assignedTo.isBefore(day));
    }

    public void revoke(Instant at) {
        if (revokedAt == null) {
            revokedAt = at;
        }
    }
}
