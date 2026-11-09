package com.pps.smartenglish.exercise.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * is_correct chỉ đổi bằng UPDATE trong repository, không sửa entity: null sang true/false khi AI chấm lần đầu
 * (applyAiVerdict), hoặc đổi tự do khi Admin yêu cầu chấm lại (setVerdict).
 */
@Entity
@Table(name = "student_answers")
public class StudentAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attempt_id", nullable = false, updatable = false)
    private Long attemptId;

    @Column(name = "question_id", nullable = false, updatable = false)
    private Long questionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(updatable = false)
    private JsonNode answer;

    /** null = chưa có kết quả (dạng chấm AI đang chờ hoặc chưa có lượt). */
    @Column(name = "is_correct")
    private Boolean correct;

    protected StudentAnswer() {}

    public StudentAnswer(Long attemptId, Long questionId, JsonNode answer, Boolean correct) {
        this.attemptId = attemptId;
        this.questionId = questionId;
        this.answer = answer;
        this.correct = correct;
    }

    public Long getId() { return id; }
    public Long getAttemptId() { return attemptId; }
    public Long getQuestionId() { return questionId; }
    public JsonNode getAnswer() { return answer; }
    public Boolean getCorrect() { return correct; }
}
