package com.pps.smartenglish.classroom.domain;

import com.pps.smartenglish.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** FR-CLS-05, D-124: ngưỡng đánh dấu học viên tụt lại của một khóa, do giáo vụ cài. Giá trị mặc định khi khóa chưa có dòng riêng. */
@Entity
@Table(name = "course_lag_thresholds")
public class LagThresholds extends Auditable {

    public static final int DEFAULT_OVERDUE = 1;
    public static final int DEFAULT_ABSENCES = 3;
    public static final int DEFAULT_UNITS_BEHIND = 1;
    public static final int DEFAULT_MIN_AVG_PERCENT = 50;

    @Id
    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "overdue_assignments", nullable = false)
    private int overdueAssignments = DEFAULT_OVERDUE;

    @Column(nullable = false)
    private int absences = DEFAULT_ABSENCES;

    @Column(name = "units_behind", nullable = false)
    private int unitsBehind = DEFAULT_UNITS_BEHIND;

    @Column(name = "min_avg_percent", nullable = false)
    private int minAvgPercent = DEFAULT_MIN_AVG_PERCENT;

    protected LagThresholds() {}

    public LagThresholds(Long courseId) {
        this.courseId = courseId;
    }

    public Long getCourseId() { return courseId; }
    public int getOverdueAssignments() { return overdueAssignments; }
    public int getAbsences() { return absences; }
    public int getUnitsBehind() { return unitsBehind; }
    public int getMinAvgPercent() { return minAvgPercent; }

    public void set(int overdueAssignments, int absences, int unitsBehind, int minAvgPercent) {
        this.overdueAssignments = overdueAssignments;
        this.absences = absences;
        this.unitsBehind = unitsBehind;
        this.minAvgPercent = minAvgPercent;
    }
}
