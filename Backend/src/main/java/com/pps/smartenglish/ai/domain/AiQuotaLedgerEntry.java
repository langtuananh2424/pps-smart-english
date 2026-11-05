package com.pps.smartenglish.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Sổ cái chỉ thêm, không sửa: balance = tổng delta. */
@Entity
@Immutable
@Table(name = "ai_quota_ledger")
public class AiQuotaLedgerEntry {

    public enum Reason { PURCHASE, GRADING, REFUND, ACTIVATION_LINK }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private int delta;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false)
    private Reason reason;

    @Column(name = "ref_id")
    private Long refId;

    protected AiQuotaLedgerEntry() {}

    public AiQuotaLedgerEntry(Long userId, int delta, Reason reason, Long refId) {
        this.userId = userId;
        this.delta = delta;
        this.reason = reason;
        this.refId = refId;
    }
}
