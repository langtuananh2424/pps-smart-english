package com.pps.smartenglish.retention.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Một đầu nhận Web Push. endpoint là khóa duy nhất: cùng trình duyệt đăng nhập tài khoản khác thì chuyển chủ. */
@Entity
@Table(name = "push_subscriptions")
public class PushSubscription {

    public static final String WEBPUSH = "WEBPUSH";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String channel = WEBPUSH;

    @Column(nullable = false, updatable = false)
    private String endpoint;

    /** {p256dh, auth}. */
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode keys;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PushSubscription() {}

    public PushSubscription(Long userId, String endpoint, JsonNode keys, Instant createdAt) {
        this.userId = userId;
        this.endpoint = endpoint;
        this.keys = keys;
        this.createdAt = createdAt;
    }

    public void reassign(Long userId, JsonNode keys) {
        this.userId = userId;
        this.keys = keys;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getEndpoint() { return endpoint; }
    public JsonNode getKeys() { return keys; }
}
