package com.pps.smartenglish.catalog.domain;

import com.pps.smartenglish.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "course_levels")
public class CourseLevel extends Auditable {

    @Id
    private Long id;

    @Column(name = "course_id")
    private Long courseId;

    private String code;
    private String title;

    @Column(name = "sort_order")
    private int sortOrder;

    protected CourseLevel() {}

    public Long getId() { return id; }
    public Long getCourseId() { return courseId; }
    public String getCode() { return code; }
    public String getTitle() { return title; }
}
