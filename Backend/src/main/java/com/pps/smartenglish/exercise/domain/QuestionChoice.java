package com.pps.smartenglish.exercise.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Lựa chọn của câu trắc nghiệm; is_correct chỉ ở đây, không bao giờ trả cho học viên trước khi nộp. */
@Entity
@Table(name = "question_choices")
public class QuestionChoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question_id", updatable = false)
    private Long questionId;

    private String label;
    private String content;

    @Column(name = "is_correct")
    private boolean correct;

    private String explanation;

    protected QuestionChoice() {}

    public QuestionChoice(Long questionId, String label, String content, boolean correct, String explanation) {
        this.questionId = questionId;
        this.label = label;
        this.content = content;
        this.correct = correct;
        this.explanation = explanation;
    }

    public Long getId() { return id; }
    public Long getQuestionId() { return questionId; }
    public String getLabel() { return label; }
    public String getContent() { return content; }
    public boolean isCorrect() { return correct; }
    public String getExplanation() { return explanation; }

    /** Sửa tại chỗ để id lựa chọn không đổi: student_answers lưu choiceId. */
    public void update(String content, boolean correct, String explanation) {
        this.content = content;
        this.correct = correct;
        this.explanation = explanation;
    }
}
