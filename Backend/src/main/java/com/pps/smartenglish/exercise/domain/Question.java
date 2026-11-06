package com.pps.smartenglish.exercise.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Chỉ đọc ở giai đoạn này (CMS ghi làm sau). answerKey không được đưa vào DTO nào trước khi nộp. */
@Entity
@Immutable
@Table(name = "questions")
public class Question {

    @Id
    private Long id;

    @Column(name = "bank_id")
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

    public Long getId() { return id; }
    public Long getBankId() { return bankId; }
    public QuestionType getType() { return QuestionType.valueOf(type); }
    public String getPrompt() { return prompt; }
    public JsonNode getPayload() { return payload; }
    public JsonNode getAnswerKey() { return answerKey; }
    public String getExplanation() { return explanation; }
}
