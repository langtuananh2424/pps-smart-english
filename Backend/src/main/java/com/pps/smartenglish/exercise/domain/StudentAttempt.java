package com.pps.smartenglish.exercise.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Nộp bài không sửa entity này mà dùng UPDATE nguyên tử trong repository. Pause, resume và lưu nháp chỉ ghi cột của
 * chúng (DynamicUpdate), nên không ghi đè submitted_at hay điểm nếu nộp đồng thời.
 */
@Entity
@Table(name = "student_exercise_attempts")
@DynamicUpdate
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

    /** Tổng credit các câu (2 chữ số thập phân), D-44. */
    private BigDecimal score;

    @Column(name = "max_score")
    private Integer maxScore;

    /** Null: không giới hạn thời gian (copy từ bank lúc bắt đầu). */
    @Column(name = "time_limit_sec", updatable = false)
    private Integer timeLimitSec;

    /** Thời gian đã chạy trước các lần tạm dừng; đoạn đang chạy được cộng thêm khi tính còn lại. */
    @Column(name = "time_used_sec", nullable = false)
    private int timeUsedSec;

    /** Lúc bắt đầu đoạn đang chạy; null khi đang tạm dừng. */
    @Column(name = "resumed_at")
    private Instant resumedAt;

    /** FR-ASG-02: lượt làm của bài được giao; null với bài thường. Lượt này không trừ lượt AI (D-130). */
    @Column(name = "assignment_id", updatable = false)
    private Long assignmentId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "draft_answers")
    private JsonNode draftAnswers;

    protected StudentAttempt() {}

    public StudentAttempt(Long userId, Long bankId, Instant startedAt, Integer timeLimitSec) {
        this.userId = userId;
        this.bankId = bankId;
        this.startedAt = startedAt;
        this.timeLimitSec = timeLimitSec;
        this.resumedAt = startedAt;
    }

    public StudentAttempt(Long userId, Long bankId, Instant startedAt, Integer timeLimitSec, Long assignmentId) {
        this(userId, bankId, startedAt, timeLimitSec);
        this.assignmentId = assignmentId;
    }

    public Long getAssignmentId() { return assignmentId; }
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getBankId() { return bankId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public BigDecimal getScore() { return score; }
    public Integer getMaxScore() { return maxScore; }
    public Integer getTimeLimitSec() { return timeLimitSec; }
    public JsonNode getDraftAnswers() { return draftAnswers; }

    /** Null nếu không giới hạn thời gian. Không âm. */
    public Long remainingSec(Instant now) {
        if (timeLimitSec == null) {
            return null;
        }
        long running = resumedAt == null ? 0 : Math.max(0, Duration.between(resumedAt, now).getSeconds());
        return Math.max(0, timeLimitSec - timeUsedSec - running);
    }

    public boolean paused() {
        return timeLimitSec != null && resumedAt == null;
    }

    /** Hết giờ khi đang chạy đồng hồ. Lượt đang tạm dừng không hết giờ. */
    public boolean expired(Instant now) {
        if (timeLimitSec == null || resumedAt == null) {
            return false;
        }
        return remainingSec(now) == 0;
    }

    /** Dừng đồng hồ: cộng đoạn vừa chạy vào thời gian đã dùng. */
    public void pause(Instant now) {
        timeUsedSec += (int) Math.max(0, Duration.between(resumedAt, now).getSeconds());
        resumedAt = null;
    }

    public void resume(Instant now) {
        resumedAt = now;
    }

    public void saveDraft(JsonNode answers) {
        draftAnswers = answers;
    }

    public int getTimeUsedSec() { return timeUsedSec; }
    public Instant getResumedAt() { return resumedAt; }
}
