package com.pps.smartenglish.catalog.domain;

import com.pps.smartenglish.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Cố ý không ánh xạ video_source/video_ref: API công khai không bao giờ được đọc tới chúng. */
@Entity
@Table(name = "lessons")
public class Lesson extends Auditable {

    @Id
    private Long id;

    @Column(name = "topic_id")
    private Long topicId;

    private String type;
    private String title;

    @Column(name = "duration_sec")
    private Integer durationSec;

    @Column(name = "sort_order")
    private int sortOrder;

    private String status;

    protected Lesson() {}

    public Long getId() { return id; }
    public Long getTopicId() { return topicId; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public Integer getDurationSec() { return durationSec; }
}
