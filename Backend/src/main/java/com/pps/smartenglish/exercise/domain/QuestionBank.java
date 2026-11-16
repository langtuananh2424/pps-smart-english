package com.pps.smartenglish.exercise.domain;

import com.pps.smartenglish.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "question_banks")
public class QuestionBank extends Auditable {

    public static final String DRAFT = "DRAFT";
    public static final String PUBLISHED = "PUBLISHED";
    /** D-11: luyện tập được tạm dừng; kiểm tra/thi thì không. */
    public static final String PRACTICE = "PRACTICE";
    public static final String EXAM = "EXAM";
    /** Bộ của khóa học (có topic) hoặc đề kiểm tra đầu vào (FR-TEST-01, không topic, luôn EXAM, bắt buộc time_limit_sec). */
    public static final String COURSE = "COURSE";
    public static final String PLACEMENT = "PLACEMENT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Không đổi được sau khi tạo: bộ câu hỏi không chuyển topic. */
    @Column(name = "topic_id", updatable = false)
    private Long topicId;

    @Column(name = "lesson_id")
    private Long lessonId;

    private String title;

    @Column(name = "is_trial")
    private boolean trial;

    /** DRAFT/PUBLISHED; đường đọc của học viên chỉ thấy PUBLISHED (QuestionBankRepository.findInfo). */
    private String status = DRAFT;

    /** Null: không giới hạn thời gian. */
    @Column(name = "time_limit_sec")
    private Integer timeLimitSec;

    private String mode = PRACTICE;

    @Column(updatable = false)
    private String purpose = COURSE;

    protected QuestionBank() {}

    /** Đề kiểm tra đầu vào: không topic, chế độ EXAM, có giới hạn thời gian. */
    public static QuestionBank placement(String title, int timeLimitSec) {
        var bank = new QuestionBank(null, null, title, false);
        bank.purpose = PLACEMENT;
        bank.mode = EXAM;
        bank.timeLimitSec = timeLimitSec;
        return bank;
    }

    public QuestionBank(Long topicId, Long lessonId, String title, boolean trial) {
        this.topicId = topicId;
        this.lessonId = lessonId;
        this.title = title;
        this.trial = trial;
    }

    public Long getId() { return id; }
    public Long getTopicId() { return topicId; }
    public Long getLessonId() { return lessonId; }
    public String getTitle() { return title; }
    public boolean isTrial() { return trial; }
    public String getStatus() { return status; }
    public Integer getTimeLimitSec() { return timeLimitSec; }
    public String getMode() { return mode; }
    public String getPurpose() { return purpose; }
    public boolean isPlacement() { return PLACEMENT.equals(purpose); }

    public void setLessonId(Long lessonId) { this.lessonId = lessonId; }
    public void setTitle(String title) { this.title = title; }
    public void setTrial(boolean trial) { this.trial = trial; }
    public void setStatus(String status) { this.status = status; }
    public void setTimeLimitSec(Integer timeLimitSec) { this.timeLimitSec = timeLimitSec; }
    public void setMode(String mode) { this.mode = mode; }
}
