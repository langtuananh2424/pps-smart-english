package com.pps.smartenglish.lessoncontent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "lesson_subtitles")
class LessonSubtitle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lesson_id", updatable = false)
    private Long lessonId;

    @Column(updatable = false)
    private String lang;

    private String content;

    @Column(name = "cue_count")
    private int cueCount;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected LessonSubtitle() {}

    LessonSubtitle(Long lessonId, String lang) {
        this.lessonId = lessonId;
        this.lang = lang;
    }

    void set(String content, int cueCount, Instant now) {
        this.content = content;
        this.cueCount = cueCount;
        this.updatedAt = now;
    }

    String getLang() { return lang; }
    String getContent() { return content; }
    int getCueCount() { return cueCount; }
    Instant getUpdatedAt() { return updatedAt; }
}
