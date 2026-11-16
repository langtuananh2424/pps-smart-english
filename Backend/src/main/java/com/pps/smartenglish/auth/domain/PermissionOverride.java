package com.pps.smartenglish.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** GRANT hoặc REVOKE một quyền cho một người, có hạn tùy chọn (null = không hết hạn). Một người một quyền một dòng. */
@Entity
@Table(name = "user_permission_overrides")
public class PermissionOverride {

    public static final String GRANT = "GRANT";
    public static final String REVOKE = "REVOKE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "permission_id", nullable = false, updatable = false)
    private Long permissionId;

    @Column(nullable = false)
    private String effect;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "granted_by")
    private Long grantedBy;

    protected PermissionOverride() {}

    public PermissionOverride(Long userId, Long permissionId, String effect, Instant expiresAt, Long grantedBy) {
        this.userId = userId;
        this.permissionId = permissionId;
        this.effect = effect;
        this.expiresAt = expiresAt;
        this.grantedBy = grantedBy;
    }

    public void change(String effect, Instant expiresAt, Long grantedBy) {
        this.effect = effect;
        this.expiresAt = expiresAt;
        this.grantedBy = grantedBy;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getPermissionId() { return permissionId; }
    public String getEffect() { return effect; }
    public Instant getExpiresAt() { return expiresAt; }
    public Long getGrantedBy() { return grantedBy; }
}
