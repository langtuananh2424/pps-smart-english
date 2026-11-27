package com.pps.smartenglish.exercise.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** D-159: tiến độ xem video và trạng thái quiz của một nhóm trong một lượt (mỗi lượt làm bài là một lượt xem). */
@Entity
@Table(name = "attempt_video_groups")
public class VideoProgress {

    /** Trình phát báo vị trí tròn giây ở cuối video; cho phép lệch nhỏ khi so với thời lượng. */
    private static final double END_SLACK_SEC = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attempt_id", nullable = false, updatable = false)
    private Long attemptId;

    @Column(name = "group_id", nullable = false, updatable = false)
    private Long groupId;

    /** Thời lượng theo máy chủ tại lần báo gần nhất. */
    @Column(name = "duration_sec")
    private Double durationSec;

    /** Vị trí xa nhất được tính là đã xem liền mạch. */
    @Column(name = "furthest_sec", nullable = false)
    private double furthestSec;

    @Column(name = "last_position_sec", nullable = false)
    private double lastPositionSec;

    @Column(name = "first_report_at")
    private Instant firstReportAt;

    @Column(name = "last_report_at")
    private Instant lastReportAt;

    @Column(nullable = false)
    private int submits;

    @Column(nullable = false)
    private boolean finalized;

    @Column(nullable = false)
    private boolean passed;

    /** Câu trả lời của lần nộp quiz cuối: [{questionId, answer}]. */
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode answers;

    /** Id các câu sai của lần nộp cuối. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "wrong_ids")
    private JsonNode wrongIds;

    protected VideoProgress() {}

    public VideoProgress(Long attemptId, Long groupId) {
        this.attemptId = attemptId;
        this.groupId = groupId;
    }

    /**
     * Cộng phần đã xem chỉ khi bước tiến liền mạch và hợp lý theo thời gian thực: vị trí mới lớn hơn vị trí báo trước, tăng không quá
     * thời gian đã trôi từ lần báo trước + dung sai, bắt đầu từ vị trí không vượt phần đã xem + dung sai, và tổng không vượt thời
     * gian thực từ lần báo đầu + dung sai (chặn gửi dồn nhiều báo cáo). Nhảy cóc vẫn đổi vị trí nhưng không cộng.
     */
    public void watch(double position, double serverDurationSec, Instant now, double toleranceSec) {
        durationSec = serverDurationSec;
        double pos = Math.max(0, Math.min(position, durationSec));
        if (firstReportAt == null) {
            firstReportAt = now;
            lastReportAt = now;
        }
        double sinceLast = secondsBetween(lastReportAt, now);
        double sinceFirst = secondsBetween(firstReportAt, now);
        double delta = pos - lastPositionSec;
        if (delta > 0 && delta <= sinceLast + toleranceSec && lastPositionSec <= furthestSec + toleranceSec
                && pos <= sinceFirst + toleranceSec) {
            furthestSec = Math.max(furthestSec, pos);
        }
        lastPositionSec = pos;
        lastReportAt = now;
    }

    /** Đã xem đủ watchPct% thời lượng (chưa biết thời lượng thì chưa đủ). */
    public boolean watched(int watchPct) {
        if (durationSec == null || durationSec <= 0) {
            return false;
        }
        double need = durationSec * watchPct / 100.0;
        return furthestSec + Math.min(END_SLACK_SEC, durationSec * 0.05) >= need;
    }

    /** Ghi một lần nộp quiz; chốt khi đúng hết hoặc hết số lần nộp. */
    public void recordSubmit(JsonNode answers, JsonNode wrongIds, boolean allCorrect, int maxSubmits) {
        this.answers = answers;
        this.wrongIds = wrongIds;
        submits++;
        if (allCorrect || submits >= maxSubmits) {
            finalized = true;
            passed = allCorrect;
        }
    }

    private static double secondsBetween(Instant from, Instant to) {
        return Math.max(0, Duration.between(from, to).toMillis() / 1000.0);
    }

    public Long getAttemptId() { return attemptId; }
    public Long getGroupId() { return groupId; }
    public Double getDurationSec() { return durationSec; }
    public double getFurthestSec() { return furthestSec; }
    public int getSubmits() { return submits; }
    public boolean isFinalized() { return finalized; }
    public boolean isPassed() { return passed; }
    public JsonNode getAnswers() { return answers; }
    public JsonNode getWrongIds() { return wrongIds; }
}
