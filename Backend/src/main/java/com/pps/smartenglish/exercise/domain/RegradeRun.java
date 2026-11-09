package com.pps.smartenglish.exercise.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Một lần chấm lại cho một phiên bản đáp án của một câu. Trạng thái đổi bằng UPDATE có điều kiện trong repository. */
@Entity
@Table(name = "regrade_runs")
public class RegradeRun {

    public static final String RUNNING = "RUNNING";
    public static final String DONE = "DONE";
    public static final String SUPERSEDED = "SUPERSEDED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question_id", nullable = false, updatable = false)
    private Long questionId;

    @Column(name = "question_version", nullable = false, updatable = false)
    private int questionVersion;

    @Column(nullable = false, updatable = false)
    private String status = RUNNING;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private Long requestedBy;

    @Column(updatable = false)
    private int total;

    @Column(updatable = false)
    private int changed;

    @Column(updatable = false)
    private int failed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "finished_at", updatable = false)
    private Instant finishedAt;

    protected RegradeRun() {}

    public RegradeRun(Long questionId, int questionVersion, Long requestedBy, Instant createdAt) {
        this.questionId = questionId;
        this.questionVersion = questionVersion;
        this.requestedBy = requestedBy;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getQuestionId() { return questionId; }
    public int getQuestionVersion() { return questionVersion; }
    public String getStatus() { return status; }
    public Long getRequestedBy() { return requestedBy; }
    public int getTotal() { return total; }
    public int getChanged() { return changed; }
    public int getFailed() { return failed; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getFinishedAt() { return finishedAt; }
}
