package com.pps.smartenglish.commerce.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/** Mỗi (link, user) đúng một dòng; unique là cơ chế chặn dùng lại và bấm đồng thời. */
@Entity
@Immutable
@Table(name = "activation_redemptions")
public class ActivationRedemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "link_id", nullable = false)
    private Long linkId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "redeemed_at", nullable = false)
    private Instant redeemedAt;

    protected ActivationRedemption() {}

    public ActivationRedemption(Long linkId, Long userId, Instant redeemedAt) {
        this.linkId = linkId;
        this.userId = userId;
        this.redeemedAt = redeemedAt;
    }

    public Long getId() { return id; }
}
