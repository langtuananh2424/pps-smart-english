package com.pps.smartenglish.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.Immutable;

/** Token LLM thật của từng lời gọi (kể cả lần thử lại và output sai) để tính chi phí FR-AI-04. */
@Entity
@Immutable
@Table(name = "ai_grading_token_usage")
public class TokenUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String model;

    @Column(name = "input_tokens", nullable = false)
    private int inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private int outputTokens;

    @Column(name = "cache_read_tokens", nullable = false)
    private int cacheReadTokens;

    @Column(name = "cache_write_tokens", nullable = false)
    private int cacheWriteTokens;

    @Column(name = "cost_usd", nullable = false)
    private BigDecimal costUsd;

    protected TokenUsage() {}

    public TokenUsage(Long jobId, Long userId, String provider, String model, int inputTokens, int outputTokens,
                      int cacheReadTokens, int cacheWriteTokens, BigDecimal costUsd) {
        this.jobId = jobId;
        this.userId = userId;
        this.provider = provider;
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.cacheReadTokens = cacheReadTokens;
        this.cacheWriteTokens = cacheWriteTokens;
        this.costUsd = costUsd;
    }
}
