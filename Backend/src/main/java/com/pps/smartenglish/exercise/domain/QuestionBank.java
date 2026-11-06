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

    protected QuestionBank() {}

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

    public void setLessonId(Long lessonId) { this.lessonId = lessonId; }
    public void setTitle(String title) { this.title = title; }
    public void setTrial(boolean trial) { this.trial = trial; }
    public void setStatus(String status) { this.status = status; }
}
