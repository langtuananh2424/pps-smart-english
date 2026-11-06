package com.pps.smartenglish.exercise.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Một bảng cho mọi dạng câu hỏi. payload được trả nguyên văn cho học viên (BankService) nên CMS chỉ cho phép đúng các trường
 * của từng dạng; answerKey không được đưa vào DTO nào của học viên trước khi nộp.
 */
@Entity
@Table(name = "questions")
public class Question extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Không đổi được sau khi tạo: câu hỏi không chuyển bộ. */
    @Column(name = "bank_id", updatable = false)
    private Long bankId;

    private String type;
    private String prompt;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode payload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answer_key")
    private JsonNode answerKey;

    private String explanation;

    @Column(name = "sort_order")
    private int sortOrder;

    protected Question() {}

    public Question(Long bankId, QuestionType type, String prompt, JsonNode payload, JsonNode answerKey, String explanation,
                    int sortOrder) {
        this.bankId = bankId;
        this.type = type.name();
        this.prompt = prompt;
        this.payload = payload;
        this.answerKey = answerKey;
        this.explanation = explanation;
        this.sortOrder = sortOrder;
    }

    public Long getId() { return id; }
    public Long getBankId() { return bankId; }
    public QuestionType getType() { return QuestionType.valueOf(type); }
    public String getPrompt() { return prompt; }
    public JsonNode getPayload() { return payload; }
    public JsonNode getAnswerKey() { return answerKey; }
    public String getExplanation() { return explanation; }
    public int getSortOrder() { return sortOrder; }

    public void setType(QuestionType type) { this.type = type.name(); }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public void setPayload(JsonNode payload) { this.payload = payload; }
    public void setAnswerKey(JsonNode answerKey) { this.answerKey = answerKey; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
