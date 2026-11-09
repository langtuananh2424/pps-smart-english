package com.pps.smartenglish.exercise.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Một câu trả lời cần chấm lại: vừa là hàng đợi của worker vừa là lịch sử điểm cũ. Hàng được tạo bằng INSERT ... SELECT;
 * status và job_id đổi bằng UPDATE có điều kiện (RegradeItemRepository), entity chỉ ghi phần điểm cũ/mới.
 */
@Entity
@Table(name = "regrade_items")
public class RegradeItem {

    public static final String PENDING = "PENDING";
    public static final String AI_WAITING = "AI_WAITING";
    public static final String DONE = "DONE";
    public static final String FAILED = "FAILED";
    public static final String CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false, updatable = false)
    private Long runId;

    @Column(name = "answer_id", nullable = false, updatable = false)
    private Long answerId;

    @Column(name = "attempt_id", nullable = false, updatable = false)
    private Long attemptId;

    @Column(nullable = false, insertable = false, updatable = false)
    private String status;

    @Column(name = "job_id", insertable = false, updatable = false)
    private Long jobId;

    @Column(name = "old_is_correct")
    private Boolean oldCorrect;

    @Column(name = "new_is_correct")
    private Boolean newCorrect;

    @Column(name = "old_attempt_score")
    private Integer oldAttemptScore;

    @Column(name = "new_attempt_score")
    private Integer newAttemptScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_result")
    private JsonNode oldResult;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_result")
    private JsonNode newResult;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected RegradeItem() {}

    public Long getId() { return id; }
    public Long getRunId() { return runId; }
    public Long getAnswerId() { return answerId; }
    public Long getAttemptId() { return attemptId; }
    public String getStatus() { return status; }
    public Long getJobId() { return jobId; }

    /** Ghi điểm cũ/mới sau khi đã giành được quyền xử lý (status chuyển DONE bằng câu UPDATE riêng). */
    public void record(Boolean oldCorrect, boolean newCorrect, int oldScore, int newScore, JsonNode oldResult, JsonNode newResult,
                       Instant now) {
        this.oldCorrect = oldCorrect;
        this.newCorrect = newCorrect;
        this.oldAttemptScore = oldScore;
        this.newAttemptScore = newScore;
        this.oldResult = oldResult;
        this.newResult = newResult;
        this.processedAt = now;
    }
}
