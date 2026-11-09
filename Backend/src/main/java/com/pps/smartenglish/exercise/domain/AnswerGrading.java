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
 * Kết quả chấm một câu trả lời bởi luật (RULE) hoặc AI; mỗi (câu trả lời, grader) một dòng.
 * Không còn @Immutable: chấm lại bài cũ phải thay dòng này (xóa bằng câu lệnh riêng rồi ghi dòng mới).
 */
@Entity
@Table(name = "student_answer_grading")
public class AnswerGrading {

    public static final String RULE = "RULE";
    public static final String AI = "AI";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "answer_id", nullable = false)
    private Long answerId;

    @Column(nullable = false)
    private String grader;

    @Column(nullable = false)
    private int score;

    /** {feedback, correctedAnswer}. */
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode result;

    protected AnswerGrading() {}

    public AnswerGrading(Long answerId, String grader, int score, JsonNode result) {
        this.answerId = answerId;
        this.grader = grader;
        this.score = score;
        this.result = result;
    }

    public Long getAnswerId() { return answerId; }
    public String getGrader() { return grader; }
    public int getScore() { return score; }
    public JsonNode getResult() { return result; }
}
