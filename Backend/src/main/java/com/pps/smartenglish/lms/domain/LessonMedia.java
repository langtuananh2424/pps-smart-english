package com.pps.smartenglish.lms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Góc nhìn chỉ đọc của lessons có cột video; tách khỏi catalog.Lesson để API catalog không bao giờ thấy video. */
@Entity
@Immutable
@Table(name = "lessons")
public class LessonMedia {

    @Id
    private Long id;

    @Column(name = "topic_id")
    private Long topicId;

    private String type;
    private String title;

    @Column(name = "video_source")
    private String videoSource;

    @Column(name = "video_ref")
    private String videoRef;

    @Column(name = "duration_sec")
    private Integer durationSec;

    private String status;

    protected LessonMedia() {}
}
