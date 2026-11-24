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
    public static final String WRITING = "WRITING";
    public static final String SPEAKING = "SPEAKING";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "class_id", nullable = false, updatable = false)
    private Long classId;

    @Column(name = "bank_id", nullable = false, updatable = false)
    private Long bankId;

    /** Giao riêng câu Viết/Nói chấm AI (D-130); null khi giao cả bộ câu hỏi. bank_id vẫn là bộ chứa câu. */
    @Column(name = "question_id", updatable = false)
    private Long questionId;

    /** WRITING hoặc SPEAKING khi có questionId. */
    @Column(name = "question_type", updatable = false)
    private String questionType;

    @Column(name = "due_at", nullable = false, updatable = false)
    private Instant dueAt;

    @Column(nullable = false)
    private String status = ACTIVE;

    /** 'lớp:bộ câu hỏi:hạn' khi ACTIVE, null khi hủy: unique của cột này là chốt chống giao trùng. */
    @Column(name = "live_key")
    private String liveKey;

    /** D-143: bài giao chốt chế độ Nghe ép lúc giao (bộ LOCKED); bộ đổi chế độ sau đó không đổi bài đã giao. */
    @Column(name = "forced_listening", nullable = false, updatable = false)
    private boolean forcedListening;

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

    /** Giao một câu Viết/Nói: live_key có thêm câu để hai câu cùng bộ, cùng hạn không bị coi là trùng. */
    public Assignment(Long classId, Long bankId, Long questionId, String questionType, Instant dueAt, Long createdBy) {
        this(classId, bankId, dueAt, createdBy);
        this.questionId = questionId;
        this.questionType = questionType;
        this.liveKey = classId + ":" + bankId + ":q" + questionId + ":" + dueAt.toEpochMilli();
    }

    public Assignment forcedListening() {
        this.forcedListening = true;
        return this;
    }

    public boolean isForcedListening() { return forcedListening; }
    public Long getId() { return id; }
    public Long getQuestionId() { return questionId; }
    public String getQuestionType() { return questionType; }
    public boolean isAiQuestion() { return questionId != null; }
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
