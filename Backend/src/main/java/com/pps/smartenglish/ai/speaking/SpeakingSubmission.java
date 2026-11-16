package com.pps.smartenglish.ai.speaking;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Một lượt nói của học viên (FR-AI-03). Chuyển trạng thái đi bằng UPDATE có điều kiện ở repository. */
@Entity
@Table(name = "speaking_submissions")
public class SpeakingSubmission extends Auditable {

    public static final String AWAITING_UPLOAD = "AWAITING_UPLOAD";
    public static final String GRADING = "GRADING";
    public static final String GRADED = "GRADED";
    public static final String UNCLEAR = "UNCLEAR";
    public static final String FAILED = "FAILED";
    public static final String EXPIRED = "EXPIRED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "question_id", nullable = false, updatable = false)
    private Long questionId;

    @Column(name = "course_id", updatable = false)
    private Long courseId;

    @Column(nullable = false)
    private String status = AWAITING_UPLOAD;

    @Column(name = "object_key", nullable = false, updatable = false)
    private String objectKey;

    @Column(name = "max_seconds", nullable = false, updatable = false)
    private int maxSeconds;

    @Column(name = "max_bytes", nullable = false, updatable = false)
    private long maxBytes;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "duration_sec")
    private BigDecimal durationSec;

    @Column(name = "upload_expires_at", nullable = false, updatable = false)
    private Instant uploadExpiresAt;

    @Column(name = "job_id")
    private Long jobId;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode result;

    @Column(name = "audio_deleted_at")
    private Instant audioDeletedAt;

    protected SpeakingSubmission() {}

    public SpeakingSubmission(Long userId, Long questionId, Long courseId, String objectKey, int maxSeconds, long maxBytes,
                              Instant uploadExpiresAt) {
        this.userId = userId;
        this.questionId = questionId;
        this.courseId = courseId;
        this.objectKey = objectKey;
        this.maxSeconds = maxSeconds;
        this.maxBytes = maxBytes;
        this.uploadExpiresAt = uploadExpiresAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getQuestionId() { return questionId; }
    public Long getCourseId() { return courseId; }
    public String getStatus() { return status; }
    public String getObjectKey() { return objectKey; }
    public int getMaxSeconds() { return maxSeconds; }
    public long getMaxBytes() { return maxBytes; }
    public Long getSizeBytes() { return sizeBytes; }
    public BigDecimal getDurationSec() { return durationSec; }
    public Instant getUploadExpiresAt() { return uploadExpiresAt; }
    public Long getJobId() { return jobId; }
    public JsonNode getResult() { return result; }
    public Instant getAudioDeletedAt() { return audioDeletedAt; }
}
