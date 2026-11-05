package com.pps.smartenglish.commerce.domain;

import com.pps.smartenglish.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Link kích hoạt: chỉ giữ hash của mã. used_count và revoked_at chỉ đổi bằng UPDATE nguyên tử trong repository. */
@Entity
@Table(name = "activation_links")
public class ActivationLink extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private String tokenHash;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "max_uses", nullable = false, updatable = false)
    private int maxUses;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    private String note;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    protected ActivationLink() {}

    public ActivationLink(String tokenHash, Long productId, int maxUses, Instant expiresAt, String note, Long createdBy) {
        this.tokenHash = tokenHash;
        this.productId = productId;
        this.maxUses = maxUses;
        this.expiresAt = expiresAt;
        this.note = note;
        this.createdBy = createdBy;
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }

    public boolean isUsable(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
