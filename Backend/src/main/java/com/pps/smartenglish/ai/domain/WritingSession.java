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

/** Trạng thái Scaffolding (FR-AI-01). turns luôn được thay bằng bản mới khi đổi, không sửa tại chỗ. */
@Entity
@Table(name = "writing_sessions")
public class WritingSession extends Auditable {

    public static final String ACTIVE = "ACTIVE";
    public static final String READY = "READY";
    public static final String SUBMITTED = "SUBMITTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "question_id", nullable = false, updatable = false)
    private Long questionId;

    /** Số câu hỏi đào sâu AI đã hỏi (không tính câu gốc của Admin). */
    @Column(nullable = false)
    private int step;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private JsonNode turns;

    private String draft;

    @Column(nullable = false)
    private String status = ACTIVE;

    @Column(name = "grade_job_id")
    private Long gradeJobId;

    /** Bài giáo viên giao (FR-ASG-02); null với phiên luyện tập thường. */
    @Column(name = "assignment_id", updatable = false)
    private Long assignmentId;

    /** Lúc nộp chấm; chỉ ghi qua claimSubmit. */
    @Column(name = "submitted_at")
    private Instant submittedAt;

    protected WritingSession() {}

    public WritingSession(Long userId, Long questionId, JsonNode turns) {
        this.userId = userId;
        this.questionId = questionId;
        this.turns = turns;
    }

    public WritingSession(Long userId, Long questionId, JsonNode turns, Long assignmentId) {
        this(userId, questionId, turns);
        this.assignmentId = assignmentId;
    }

    public void setTurns(JsonNode turns) { this.turns = turns; }
    public void setStep(int step) { this.step = step; }
    public void setStatus(String status) { this.status = status; }
    public void setDraft(String draft) { this.draft = draft; }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getQuestionId() { return questionId; }
    public int getStep() { return step; }
    public JsonNode getTurns() { return turns; }
    public String getDraft() { return draft; }
    public String getStatus() { return status; }
    public Long getGradeJobId() { return gradeJobId; }
    public Long getAssignmentId() { return assignmentId; }
    public Instant getSubmittedAt() { return submittedAt; }
}
