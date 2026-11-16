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

    /** Nguồn lượt bị trừ/hoàn: lượt định kỳ của khóa hoặc pool mua thêm (D-47). */
    public enum Source { PERIODIC, POOL }

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

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false)
    private Source source;

    @Column(name = "course_id")
    private Long courseId;

    protected AiQuotaLedgerEntry() {}

    public AiQuotaLedgerEntry(Long userId, int delta, Reason reason, Long refId, Source source, Long courseId) {
        this.userId = userId;
        this.delta = delta;
        this.reason = reason;
        this.refId = refId;
        this.source = source;
        this.courseId = courseId;
    }
}
