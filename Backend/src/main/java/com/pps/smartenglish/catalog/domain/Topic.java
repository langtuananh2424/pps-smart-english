package com.pps.smartenglish.catalog.domain;

import com.pps.smartenglish.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "topics")
public class Topic extends Auditable {

    @Id
    private Long id;

    @Column(name = "level_id")
    private Long levelId;

    private String title;

    @Column(name = "sort_order")
    private int sortOrder;

    @Column(name = "is_free_trial")
    private boolean freeTrial;

    protected Topic() {}

    public Long getId() { return id; }
    public Long getLevelId() { return levelId; }
    public String getTitle() { return title; }
    public boolean isFreeTrial() { return freeTrial; }
}
