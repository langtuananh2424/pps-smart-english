package com.pps.smartenglish.catalog.domain;

import com.pps.smartenglish.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Chỉ đọc ở giai đoạn này; CMS ghi sẽ làm sau. */
@Entity
@Table(name = "courses")
public class Course extends Auditable {

    @Id
    private Long id;

    private String slug;
    private String title;
    private String description;

    @Column(name = "target_type")
    private String targetType;

    private String status;

    protected Course() {}

    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getTargetType() { return targetType; }
}
