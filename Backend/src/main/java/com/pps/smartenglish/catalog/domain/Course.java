package com.pps.smartenglish.catalog.domain;

import com.pps.smartenglish.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "courses")
public class Course extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String slug;
    private String title;
    private String description;

    @Column(name = "target_type")
    private String targetType;

    private String status = ContentStatus.DRAFT;

    protected Course() {}

    public Course(String slug, String title, String description, String targetType) {
        this.slug = slug;
        this.title = title;
        this.description = description;
        this.targetType = targetType;
    }

    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getTargetType() { return targetType; }
    public String getStatus() { return status; }

    public void setSlug(String slug) { this.slug = slug; }
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public void setStatus(String status) { this.status = status; }
}
