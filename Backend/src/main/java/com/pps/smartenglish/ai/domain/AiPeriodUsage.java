package com.pps.smartenglish.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** Số lượt đã dùng của (học viên, khóa) trong một kỳ. Chỉ tăng giảm bằng UPDATE có điều kiện ở repository. */
@Entity
@Table(name = "ai_period_usage")
public class AiPeriodUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(nullable = false)
    private int used;

    protected AiPeriodUsage() {}

    public AiPeriodUsage(Long userId, Long courseId, LocalDate periodStart) {
        this.userId = userId;
        this.courseId = courseId;
        this.periodStart = periodStart;
    }
}
