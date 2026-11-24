package com.pps.smartenglish.exercise.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Một lần nhập hàng loạt vào một bộ câu hỏi (D-138); hoàn tác theo lô. */
@Entity
@Table(name = "import_batches")
public class ImportBatch {

    public static final String APPLIED = "APPLIED";
    public static final String UNDONE = "UNDONE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bank_id", updatable = false)
    private Long bankId;

    @Column(name = "user_id", updatable = false)
    private Long userId;

    /** PASTE hoặc EXCEL. */
    @Column(updatable = false)
    private String source;

    private String status = APPLIED;

    @Column(name = "question_count", updatable = false)
    private int questionCount;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "undone_at")
    private Instant undoneAt;

    protected ImportBatch() {}

    public ImportBatch(Long bankId, Long userId, String source, int questionCount, Instant createdAt) {
        this.bankId = bankId;
        this.userId = userId;
        this.source = source;
        this.questionCount = questionCount;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getBankId() { return bankId; }
    public Long getUserId() { return userId; }
    public String getSource() { return source; }
    public String getStatus() { return status; }
    public int getQuestionCount() { return questionCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUndoneAt() { return undoneAt; }

    public void undo(Instant at) {
        this.status = UNDONE;
        this.undoneAt = at;
    }
}
