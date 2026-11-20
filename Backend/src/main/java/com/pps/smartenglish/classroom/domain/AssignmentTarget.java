package com.pps.smartenglish.classroom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** FR-ASG-01: người nhận riêng của một bài giao. Bài không có dòng nào là giao cho cả lớp. */
@Entity
@Table(name = "assignment_targets")
public class AssignmentTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "assignment_id", nullable = false, updatable = false)
    private Long assignmentId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    protected AssignmentTarget() {}

    public AssignmentTarget(Long assignmentId, Long userId) {
        this.assignmentId = assignmentId;
        this.userId = userId;
    }

    public Long getAssignmentId() { return assignmentId; }
    public Long getUserId() { return userId; }
}
