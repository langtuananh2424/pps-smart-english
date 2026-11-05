package com.pps.smartenglish.commerce.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Nhật ký webhook: mỗi (provider, provider_ref) đúng một dòng. */
@Entity
@Immutable
@Table(name = "payment_transactions")
public class PaymentTransaction {

    public enum Status { APPLIED, MISMATCH, ORDER_NOT_FOUND, ORDER_CANCELLED, ORDER_ALREADY_PAID }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id")
    private Long orderId;

    @Column(nullable = false)
    private String provider;

    @Column(name = "provider_ref", nullable = false)
    private String providerRef;

    @Column(name = "amount_vnd", nullable = false)
    private long amountVnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload")
    private JsonNode rawPayload;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected PaymentTransaction() {}

    public PaymentTransaction(Long orderId, String provider, String providerRef, long amountVnd, Status status,
                              JsonNode rawPayload, Instant receivedAt) {
        this.orderId = orderId;
        this.provider = provider;
        this.providerRef = providerRef;
        this.amountVnd = amountVnd;
        this.status = status;
        this.rawPayload = rawPayload;
        this.receivedAt = receivedAt;
    }
}
