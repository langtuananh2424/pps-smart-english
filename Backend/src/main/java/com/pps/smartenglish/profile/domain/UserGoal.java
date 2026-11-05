package com.pps.smartenglish.profile.domain;

import com.pps.smartenglish.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "user_goals")
public class UserGoal extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "target_type", nullable = false)
    private String targetType;

    @Column(name = "target_value", nullable = false)
    private String targetValue;

    @Column(name = "target_date")
    private LocalDate targetDate;

    protected UserGoal() {}

    public UserGoal(Long userId, Long courseId, String targetType, String targetValue, LocalDate targetDate) {
        this.userId = userId;
        this.courseId = courseId;
        this.targetType = targetType;
        this.targetValue = targetValue;
        this.targetDate = targetDate;
    }

    public Long getId() {
        return id;
    }

    public Long getCourseId() {
        return courseId;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetValue() {
        return targetValue;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }
}
