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

    @Column(name = "attempt_id", nullable = false, updatable = false)
    private Long attemptId;

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

    public Long getId() { return id; }
    public Long getAttemptId() { return attemptId; }
    public BigDecimal getScore() { return score; }
    public String getComment() { return comment; }
    public Long getGradedBy() { return gradedBy; }
    public Instant getGradedAt() { return gradedAt; }
}
