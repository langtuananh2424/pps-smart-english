package com.pps.smartenglish.ai.domain;

import com.pps.smartenglish.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_ai_quota")
public class UserAiQuota extends Auditable {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private int balance;

    protected UserAiQuota() {}

    public UserAiQuota(Long userId, int balance) {
        this.userId = userId;
        this.balance = balance;
    }

    public Long getUserId() {
        return userId;
    }

    public int getBalance() {
        return balance;
    }
}
