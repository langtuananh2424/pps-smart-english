package com.pps.smartenglish.catalog.domain;

import com.pps.smartenglish.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "courses")
public class Course extends Auditable {

    public static final String SELF_PACED = "SELF_PACED";
    public static final String CLASS_BASED = "CLASS_BASED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String slug;
    private String title;
    private String description;

    @Column(name = "target_type")
    private String targetType;

    private String status = ContentStatus.DRAFT;

    /** Lượt AI định kỳ (D-31): WEEK hoặc MONTH, mỗi kỳ đủ aiPeriodQuota lượt cho học viên còn hạn. */
    @Column(name = "ai_period", nullable = false)
    private String aiPeriod = "WEEK";

    @Column(name = "ai_period_quota", nullable = false)
    private int aiPeriodQuota = 20;

    /** FR-EXAM-02: bộ câu hỏi mode EXAM làm bài thi khóa; mở khi học xong >= examUnlockPercent, thi lại tối đa examRetakes lần. */
    @Column(name = "exam_bank_id")
    private Long examBankId;

    @Column(name = "exam_unlock_percent", nullable = false)
    private int examUnlockPercent = 80;

    @Column(name = "exam_retakes", nullable = false)
    private int examRetakes = 0;

    /** D-117: SELF_PACED (khóa tự học) hoặc CLASS_BASED (khóa có lớp, FR-CLS-01). */
    @Column(name = "delivery_mode", nullable = false)
    private String deliveryMode = SELF_PACED;

    /** FR-LMS-03, D-124: % thời lượng xem để bài giảng tính là xong (1..100). */
    @Column(name = "lesson_complete_percent", nullable = false)
    private int lessonCompletePercent = 80;

    /** FR-LMS-04, D-122: bật thì học viên phải xong unit trước mới mở unit sau. */
    @Column(nullable = false)
    private boolean sequential;

    /** D-161: khóa IELTS; bộ của khóa này mới được soạn kiểu câu IELTS_*. */
    @Column(name = "is_ielts", nullable = false)
    private boolean ielts;

    /** FR-GOAL-03: dải band đầu vào <= band < đầu ra; null cả hai = không tham gia gợi ý. */
    @Column(name = "band_from")
    private BigDecimal bandFrom;

    @Column(name = "band_to")
    private BigDecimal bandTo;

    protected Course() {}

    public Course(String slug, String title, String description, String targetType) {
        this.slug = slug;
        this.title = title;
        this.description = description;
        this.targetType = targetType;
    }

    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getTargetType() { return targetType; }
    public String getStatus() { return status; }
    public String getAiPeriod() { return aiPeriod; }
    public int getAiPeriodQuota() { return aiPeriodQuota; }

    public BigDecimal getBandFrom() { return bandFrom; }
    public BigDecimal getBandTo() { return bandTo; }
    public void setBand(BigDecimal from, BigDecimal to) {
        this.bandFrom = from;
        this.bandTo = to;
    }

    public String getDeliveryMode() { return deliveryMode; }
    public void setDeliveryMode(String deliveryMode) { this.deliveryMode = deliveryMode; }
    public int getLessonCompletePercent() { return lessonCompletePercent; }
    public void setLessonCompletePercent(int lessonCompletePercent) { this.lessonCompletePercent = lessonCompletePercent; }
    public boolean isIelts() { return ielts; }
    public void setIelts(boolean ielts) { this.ielts = ielts; }
    public boolean isSequential() { return sequential; }
    public void setSequential(boolean sequential) { this.sequential = sequential; }

    public Long getExamBankId() { return examBankId; }
    public int getExamUnlockPercent() { return examUnlockPercent; }
    public int getExamRetakes() { return examRetakes; }

    public void setExamBankId(Long examBankId) { this.examBankId = examBankId; }
    public void setExamUnlockPercent(int examUnlockPercent) { this.examUnlockPercent = examUnlockPercent; }
    public void setExamRetakes(int examRetakes) { this.examRetakes = examRetakes; }
    public void setSlug(String slug) { this.slug = slug; }
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public void setStatus(String status) { this.status = status; }
    public void setAiPeriod(String aiPeriod) { this.aiPeriod = aiPeriod; }
    public void setAiPeriodQuota(int aiPeriodQuota) { this.aiPeriodQuota = aiPeriodQuota; }
}
