package com.pps.smartenglish.exercise.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Việc nộp bài không sửa entity này mà dùng UPDATE nguyên tử trong repository. */
@Entity
@Table(name = "student_exercise_attempts")
public class StudentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "bank_id", nullable = false, updatable = false)
    private Long bankId;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    private Integer score;

    @Column(name = "max_score")
    private Integer maxScore;

    protected StudentAttempt() {}

    public StudentAttempt(Long userId, Long bankId, Instant startedAt) {
        this.userId = userId;
        this.bankId = bankId;
        this.startedAt = startedAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getBankId() { return bankId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Integer getScore() { return score; }
    public Integer getMaxScore() { return maxScore; }
}
