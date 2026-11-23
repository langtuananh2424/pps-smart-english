package com.pps.smartenglish.classroom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** FR-ASG-03, D-113: một lần giáo viên sửa điểm hoặc nhận xét. Chỉ thêm, không sửa; điểm cuối là dòng mới nhất có điểm. */
@Entity
@Table(name = "grade_overrides")
public class GradeOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Đúng một trong ba: lượt làm bài, phiên viết hoặc lượt nói của bài giao. */
    @Column(name = "attempt_id", updatable = false)
    private Long attemptId;

    @Column(name = "writing_session_id", updatable = false)
    private Long writingSessionId;

    @Column(name = "speaking_submission_id", updatable = false)
    private Long speakingSubmissionId;

    @Column(updatable = false)
    private BigDecimal score;

    @Column(updatable = false)
    private String comment;

    @Column(name = "graded_by", nullable = false, updatable = false)
    private Long gradedBy;

    @Column(name = "graded_at", nullable = false, updatable = false)
    private Instant gradedAt;

    protected GradeOverride() {}

    public GradeOverride(Long attemptId, BigDecimal score, String comment, Long gradedBy, Instant gradedAt) {
        this.attemptId = attemptId;
        this.score = score;
        this.comment = comment;
        this.gradedBy = gradedBy;
        this.gradedAt = gradedAt;
    }

    /** Sửa điểm cho bài Viết/Nói; questionType là Assignment.getQuestionType(), null thì submissionId là lượt làm bài. */
    public static GradeOverride of(String questionType, Long submissionId, BigDecimal score, String comment, Long gradedBy, Instant gradedAt) {
        var o = new GradeOverride(null, score, comment, gradedBy, gradedAt);
        if (Assignment.WRITING.equals(questionType)) {
            o.writingSessionId = submissionId;
        } else if (Assignment.SPEAKING.equals(questionType)) {
            o.speakingSubmissionId = submissionId;
        } else {
            o.attemptId = submissionId;
        }
        return o;
    }

    public Long getId() { return id; }
    /** Id của lượt làm bài, phiên viết hoặc lượt nói mà dòng này chấm. */
    public Long getSubmissionId() {
        return attemptId != null ? attemptId : writingSessionId != null ? writingSessionId : speakingSubmissionId;
    }
    public BigDecimal getScore() { return score; }
    public String getComment() { return comment; }
    public Long getGradedBy() { return gradedBy; }
    public Instant getGradedAt() { return gradedAt; }
}
