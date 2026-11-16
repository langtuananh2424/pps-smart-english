package com.pps.smartenglish.exercise.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** FR-AI-07: một cặp hỏi - đáp soạn sẵn của bộ câu hỏi. */
@Entity
@Table(name = "bank_faqs")
public class BankFaq {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bank_id", updatable = false)
    private Long bankId;

    @Column(name = "sort_order")
    private int sortOrder;

    private String question;
    private String answer;

    protected BankFaq() {}

    public BankFaq(Long bankId, int sortOrder, String question, String answer) {
        this.bankId = bankId;
        this.sortOrder = sortOrder;
        this.question = question;
        this.answer = answer;
    }

    public Long getId() { return id; }
    public Long getBankId() { return bankId; }
    public int getSortOrder() { return sortOrder; }
    public String getQuestion() { return question; }
    public String getAnswer() { return answer; }

    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public void setQuestion(String question) { this.question = question; }
    public void setAnswer(String answer) { this.answer = answer; }
}
