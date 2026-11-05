package com.pps.smartenglish.commerce.domain;

import com.pps.smartenglish.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Quyền học một Level. Mỗi (user, level) một dòng; mua lại thì sửa dòng cũ (gia hạn). */
@Entity
@Table(name = "user_course_enrollments")
public class Enrollment extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "level_id", nullable = false, updatable = false)
    private Long levelId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "starts_at", nullable = false, updatable = false)
    private Instant startsAt;

    /** null = không giới hạn. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    protected Enrollment() {}

    public Enrollment(Long userId, Long levelId, Long orderId, Instant startsAt, Instant expiresAt) {
        this.userId = userId;
        this.levelId = levelId;
        this.orderId = orderId;
        this.startsAt = startsAt;
        this.expiresAt = expiresAt;
    }

    public void renew(Long orderId, Instant expiresAt) {
        this.orderId = orderId;
        this.status = "ACTIVE";
        this.expiresAt = expiresAt;
    }

    public Instant getExpiresAt() { return expiresAt; }
}
