package com.pps.smartenglish.exercise.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "student_answers")
public class StudentAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attempt_id", nullable = false)
    private Long attemptId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode answer;

    /** null = dạng chấm AI, chưa có kết quả. */
    @Column(name = "is_correct")
    private Boolean correct;

    protected StudentAnswer() {}

    public StudentAnswer(Long attemptId, Long questionId, JsonNode answer, Boolean correct) {
        this.attemptId = attemptId;
        this.questionId = questionId;
        this.answer = answer;
        this.correct = correct;
    }

    public Long getQuestionId() { return questionId; }
    public Boolean getCorrect() { return correct; }
}
