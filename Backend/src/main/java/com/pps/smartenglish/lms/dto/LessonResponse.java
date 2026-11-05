package com.pps.smartenglish.lms.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

public record LessonResponse(Long id, String title, String type, Integer durationSec, VideoInfo video) {

    /** STORAGE: url + expiresAt; YOUTUBE: ref. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record VideoInfo(String source, String url, String ref, Instant expiresAt) {}
}
