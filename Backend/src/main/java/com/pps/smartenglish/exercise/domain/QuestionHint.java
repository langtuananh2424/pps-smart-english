package com.pps.smartenglish.exercise.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** FR-AI-06: gợi ý soạn sẵn của một câu hỏi; content là bong bóng ngắn, detail là popup dài (có thể không có). */
@Entity
@Table(name = "question_hints")
public class QuestionHint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question_id", updatable = false)
    private Long questionId;

    @Column(name = "sort_order")
    private int sortOrder;

    private String content;
    private String detail;

    protected QuestionHint() {}

    public QuestionHint(Long questionId, int sortOrder, String content, String detail) {
        this.questionId = questionId;
        this.sortOrder = sortOrder;
        this.content = content;
        this.detail = detail;
    }

    public Long getId() { return id; }
    public Long getQuestionId() { return questionId; }
    public int getSortOrder() { return sortOrder; }
    public String getContent() { return content; }
    public String getDetail() { return detail; }

    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public void setContent(String content) { this.content = content; }
    public void setDetail(String detail) { this.detail = detail; }
}
