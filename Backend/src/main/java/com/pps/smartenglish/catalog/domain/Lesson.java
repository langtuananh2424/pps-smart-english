package com.pps.smartenglish.catalog.domain;

import com.pps.smartenglish.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Cột video chỉ phục vụ CMS ghi/đọc; API công khai của catalog luôn dựng DTO riêng nên không bao giờ trả video_source/video_ref
 * (CatalogTest kiểm). Phát video đi qua lms.LessonMedia.
 */
@Entity
@Table(name = "lessons")
public class Lesson extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Không đổi được sau khi tạo: bài không chuyển topic. */
    @Column(name = "topic_id", updatable = false)
    private Long topicId;

    private String type;
    private String title;

    @Column(name = "video_source")
    private String videoSource;

    @Column(name = "video_ref")
    private String videoRef;

    @Column(name = "duration_sec")
    private Integer durationSec;

    @Column(name = "sort_order")
    private int sortOrder;

    private String status = ContentStatus.DRAFT;

    protected Lesson() {}

    public Lesson(Long topicId, String type, String title, Integer durationSec, int sortOrder) {
        this.topicId = topicId;
        this.type = type;
        this.title = title;
        this.durationSec = durationSec;
        this.sortOrder = sortOrder;
    }

    public Long getId() { return id; }
    public Long getTopicId() { return topicId; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getVideoSource() { return videoSource; }
    public String getVideoRef() { return videoRef; }
    public Integer getDurationSec() { return durationSec; }
    public int getSortOrder() { return sortOrder; }
    public String getStatus() { return status; }

    public void setType(String type) { this.type = type; }
    public void setTitle(String title) { this.title = title; }
    public void setDurationSec(Integer durationSec) { this.durationSec = durationSec; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public void setStatus(String status) { this.status = status; }

    public void setVideo(String source, String ref) {
        this.videoSource = source;
        this.videoRef = ref;
    }
}
