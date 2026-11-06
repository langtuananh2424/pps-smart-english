package com.pps.smartenglish.catalog.domain;

import com.pps.smartenglish.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "course_levels")
public class CourseLevel extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Không đổi được sau khi tạo: level không chuyển khóa. */
    @Column(name = "course_id", updatable = false)
    private Long courseId;

    private String code;
    private String title;

    @Column(name = "sort_order")
    private int sortOrder;

    private String status = ContentStatus.DRAFT;

    protected CourseLevel() {}

    public CourseLevel(Long courseId, String code, String title, int sortOrder) {
        this.courseId = courseId;
        this.code = code;
        this.title = title;
        this.sortOrder = sortOrder;
    }

    public Long getId() { return id; }
    public Long getCourseId() { return courseId; }
    public String getCode() { return code; }
    public String getTitle() { return title; }
    public int getSortOrder() { return sortOrder; }
    public String getStatus() { return status; }

    public void setCode(String code) { this.code = code; }
    public void setTitle(String title) { this.title = title; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public void setStatus(String status) { this.status = status; }
}
