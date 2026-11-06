package com.pps.smartenglish.retention.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDate;

/** Một ngày có học của một người (cơ sở tính streak, FR-RET-01). Chỉ chèn qua ActivityDayRepository.insertIfAbsent. */
@Entity
@Table(name = "activity_days")
@IdClass(ActivityDay.Key.class)
public class ActivityDay {

    public record Key(Long userId, LocalDate activityDate) implements Serializable {}

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "activity_date")
    private LocalDate activityDate;

    protected ActivityDay() {}
}
