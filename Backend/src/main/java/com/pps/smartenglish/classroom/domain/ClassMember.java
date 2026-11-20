package com.pps.smartenglish.classroom.domain;

import com.pps.smartenglish.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** FR-CLS-03: một lần học viên ở trong một lớp. Rời lớp thì đổi trạng thái, không xóa (giữ lịch sử). */
@Entity
@Table(name = "class_members")
public class ClassMember extends Auditable {

    public static final String ACTIVE = "ACTIVE";
    public static final String TRANSFERRED = "TRANSFERRED";
    public static final String WITHDRAWN = "WITHDRAWN";
    public static final String COMPLETED = "COMPLETED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "class_id", nullable = false, updatable = false)
    private Long classId;

    @Column(name = "course_id", nullable = false, updatable = false)
    private Long courseId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false)
    private String status = ACTIVE;

    /** 'khóa:học viên' khi ACTIVE, null khi không: unique của cột này là chốt một lớp đang học mỗi khóa. */
    @Column(name = "live_key")
    private String liveKey;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    protected ClassMember() {}

    public ClassMember(Long classId, Long courseId, Long userId, Instant joinedAt) {
        this.classId = classId;
        this.courseId = courseId;
        this.userId = userId;
        this.joinedAt = joinedAt;
        this.liveKey = courseId + ":" + userId;
    }

    public Long getId() { return id; }
    public Long getClassId() { return classId; }
    public Long getCourseId() { return courseId; }
    public Long getUserId() { return userId; }
    public String getStatus() { return status; }
    public Instant getJoinedAt() { return joinedAt; }
    public Instant getLeftAt() { return leftAt; }

    /** Rời lớp với trạng thái mới (TRANSFERRED, WITHDRAWN, COMPLETED). */
    public void leave(String newStatus, Instant at) {
        this.status = newStatus;
        this.leftAt = at;
        this.liveKey = null;
    }
}
