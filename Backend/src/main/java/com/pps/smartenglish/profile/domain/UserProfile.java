package com.pps.smartenglish.profile.domain;

import com.pps.smartenglish.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "user_profiles")
public class UserProfile extends Auditable {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "self_level")
    private SelfLevel selfLevel;

    private long xp;

    @Column(name = "streak_current")
    private int streakCurrent;

    @Column(name = "streak_best")
    private int streakBest;

    @Column(name = "last_active_date")
    private LocalDate lastActiveDate;

    private String timezone;

    protected UserProfile() {}

    public UserProfile(Long userId, String displayName) {
        this.userId = userId;
        this.displayName = displayName;
    }

    public Long getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public SelfLevel getSelfLevel() {
        return selfLevel;
    }

    public void setSelfLevel(SelfLevel selfLevel) {
        this.selfLevel = selfLevel;
    }

    public long getXp() {
        return xp;
    }

    public int getStreakCurrent() {
        return streakCurrent;
    }

    public int getStreakBest() {
        return streakBest;
    }

    public LocalDate getLastActiveDate() {
        return lastActiveDate;
    }

    public String getTimezone() {
        return timezone;
    }
}
