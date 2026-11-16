package com.pps.smartenglish.placement.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Lượt kiểm tra đầu vào. Khách chưa có user: xác thực bằng hash token; claim gắn user và xóa hash. Mọi thay đổi chạy
 * dưới khóa dòng (PlacementAttemptRepository.lockById) nên nộp hai lần hay claim song song chỉ một bên thắng.
 */
@Entity
@Table(name = "placement_attempts")
@DynamicUpdate
public class PlacementAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bank_id", nullable = false, updatable = false)
    private Long bankId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "guest_token_hash")
    private String guestTokenHash;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "time_limit_sec", nullable = false, updatable = false)
    private int timeLimitSec;

    @Column(name = "deadline_at", nullable = false, updatable = false)
    private Instant deadlineAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "draft_answers")
    private JsonNode draftAnswers;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode answers;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    /** {SKILL: {correct, total, percent, band}}; không có đáp án đúng của từng câu. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skill_results")
    private JsonNode skillResults;

    @Column(name = "overall_band")
    private BigDecimal overallBand;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    protected PlacementAttempt() {}

    public PlacementAttempt(Long bankId, Long userId, String guestTokenHash, Instant startedAt, int timeLimitSec) {
        this.bankId = bankId;
        this.userId = userId;
        this.guestTokenHash = guestTokenHash;
        this.startedAt = startedAt;
        this.timeLimitSec = timeLimitSec;
        this.deadlineAt = startedAt.plusSeconds(timeLimitSec);
    }

    public Long getId() { return id; }
    public Long getBankId() { return bankId; }
    public Long getUserId() { return userId; }
    public String getGuestTokenHash() { return guestTokenHash; }
    public Instant getStartedAt() { return startedAt; }
    public int getTimeLimitSec() { return timeLimitSec; }
    public Instant getDeadlineAt() { return deadlineAt; }
    public JsonNode getDraftAnswers() { return draftAnswers; }
    public Instant getSubmittedAt() { return submittedAt; }
    public JsonNode getSkillResults() { return skillResults; }
    public BigDecimal getOverallBand() { return overallBand; }

    public boolean submitted() {
        return submittedAt != null;
    }

    public void saveDraft(JsonNode answers) {
        this.draftAnswers = answers;
    }

    public void submit(Instant now, JsonNode answers, JsonNode skillResults, BigDecimal overallBand) {
        this.submittedAt = now;
        this.answers = answers;
        this.skillResults = skillResults;
        this.overallBand = overallBand;
    }

    public void claim(long userId, Instant now) {
        this.userId = userId;
        this.guestTokenHash = null;
        this.claimedAt = now;
    }
}
