package com.pps.smartenglish.catalog.domain;

import com.pps.smartenglish.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "topics")
public class Topic extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Không đổi được sau khi tạo: topic không chuyển level. */
    @Column(name = "level_id", updatable = false)
    private Long levelId;

    private String title;

    @Column(name = "sort_order")
    private int sortOrder;

    @Column(name = "is_free_trial")
    private boolean freeTrial;

    private String status = ContentStatus.DRAFT;

    protected Topic() {}

    public Topic(Long levelId, String title, int sortOrder, boolean freeTrial) {
        this.levelId = levelId;
        this.title = title;
        this.sortOrder = sortOrder;
        this.freeTrial = freeTrial;
    }

    public Long getId() { return id; }
    public Long getLevelId() { return levelId; }
    public String getTitle() { return title; }
    public int getSortOrder() { return sortOrder; }
    public boolean isFreeTrial() { return freeTrial; }
    public String getStatus() { return status; }

    public void setTitle(String title) { this.title = title; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public void setFreeTrial(boolean freeTrial) { this.freeTrial = freeTrial; }
    public void setStatus(String status) { this.status = status; }
}
