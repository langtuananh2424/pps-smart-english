package com.pps.smartenglish.classroom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** D-124: cấu hình phân hệ lớp do admin cài; một dòng id = 1. */
@Entity
@Table(name = "class_settings")
public class ClassSettings {

    @Id
    private Integer id = 1;

    @Column(name = "reminder_minutes", nullable = false)
    private int reminderMinutes = 60;

    @Column(name = "assignment_reminder_hours", nullable = false)
    private int assignmentReminderHours = 24;

    protected ClassSettings() {}

    public int getReminderMinutes() { return reminderMinutes; }
    public void setReminderMinutes(int reminderMinutes) { this.reminderMinutes = reminderMinutes; }
    public int getAssignmentReminderHours() { return assignmentReminderHours; }
    public void setAssignmentReminderHours(int hours) { this.assignmentReminderHours = hours; }
}
