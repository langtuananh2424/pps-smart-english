package com.pps.smartenglish.lms.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/** courseId..levelId: để player dựng breadcrumb và sidebar (D-60). */
public record LessonResponse(Long id, String title, String type, Integer durationSec, VideoInfo video, Long courseId,
                             String courseSlug, String courseTitle, Long topicId, String topicTitle, Long levelId) {

    /** STORAGE: url + expiresAt; YOUTUBE: ref. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record VideoInfo(String source, String url, String ref, Instant expiresAt) {}
}
