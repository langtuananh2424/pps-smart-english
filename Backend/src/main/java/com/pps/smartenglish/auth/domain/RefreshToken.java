package com.pps.smartenglish.auth.domain;

import com.pps.smartenglish.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** Chỉ đặt khi token bị thay bằng token mới (xoay vòng), không đặt khi hủy chủ động. */
    @Column(name = "replaced_at")
    private Instant replacedAt;

    @Column(name = "device_label")
    private String deviceLabel;

    protected RefreshToken() {}

    public RefreshToken(Long userId, String tokenHash, Instant expiresAt, String deviceLabel) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.deviceLabel = deviceLabel;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getReplacedAt() {
        return replacedAt;
    }

    public String getDeviceLabel() {
        return deviceLabel;
    }
}
