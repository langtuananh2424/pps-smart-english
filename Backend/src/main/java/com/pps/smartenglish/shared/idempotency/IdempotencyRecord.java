package com.pps.smartenglish.shared.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "idempotency_keys")
class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "idem_key", nullable = false, updatable = false)
    private String idemKey;

    @Column(name = "request_hash", nullable = false, updatable = false)
    private String requestHash;

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    @Column(name = "response_body", nullable = false)
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {}

    /** Dòng "giữ chỗ" chèn trước khi chạy việc thật; complete() điền kết quả trong cùng transaction. */
    IdempotencyRecord(Long userId, String key, String requestHash, Instant createdAt) {
        this.userId = userId;
        this.idemKey = key;
        this.requestHash = requestHash;
        this.responseStatus = 0;
        this.responseBody = "";
        this.createdAt = createdAt;
    }

    void complete(int status, String body) {
        this.responseStatus = status;
        this.responseBody = body;
    }

    String getRequestHash() { return requestHash; }
    int getResponseStatus() { return responseStatus; }
    String getResponseBody() { return responseBody; }
}
