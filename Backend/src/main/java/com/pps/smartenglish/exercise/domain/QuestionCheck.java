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

/** D-150: số lần kiểm tra sai của một câu trong một lượt, và câu đã kiểm tra đúng chưa. */
@Entity
@Table(name = "attempt_question_checks")
public class QuestionCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attempt_id", nullable = false, updatable = false)
    private Long attemptId;

    @Column(name = "question_id", nullable = false, updatable = false)
    private Long questionId;

    @Column(name = "wrong_count", nullable = false)
    private int wrongCount;

    @Column(nullable = false)
    private boolean solved;

    /** Câu trả lời của lần kiểm tra gần nhất. */
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode answer;

    protected QuestionCheck() {}

    public QuestionCheck(Long attemptId, Long questionId) {
        this.attemptId = attemptId;
        this.questionId = questionId;
    }

    /** Khóa khi đã đúng, hoặc đã dùng hết maxRetries + 1 lần kiểm tra. */
    public boolean locked(int maxRetries) {
        return solved || wrongCount > maxRetries;
    }

    public int checksLeft(int maxRetries) {
        return Math.max(0, maxRetries + 1 - wrongCount);
    }

    public void record(boolean correct, JsonNode answer) {
        this.answer = answer;
        if (correct) {
            solved = true;
        } else {
            wrongCount++;
        }
    }

    public Long getQuestionId() { return questionId; }
    public int getWrongCount() { return wrongCount; }
    public boolean isSolved() { return solved; }
    public JsonNode getAnswer() { return answer; }
}
