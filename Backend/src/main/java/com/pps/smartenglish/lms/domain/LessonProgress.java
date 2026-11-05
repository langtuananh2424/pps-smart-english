package com.pps.smartenglish.lms.domain;

import com.pps.smartenglish.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Chỉ đọc qua JPA; mọi ghi đi bằng câu lệnh nguyên tử trong repository. */
@Entity
@Table(name = "lesson_progress")
public class LessonProgress extends Auditable {

    @Id
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "lesson_id")
    private Long lessonId;

    @Column(name = "watched_sec")
    private int watchedSec;

    @Column(name = "max_position_sec")
    private int maxPositionSec;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected LessonProgress() {}

    public Long getLessonId() { return lessonId; }
    public int getWatchedSec() { return watchedSec; }
    public int getMaxPositionSec() { return maxPositionSec; }
    public Instant getCompletedAt() { return completedAt; }
}
