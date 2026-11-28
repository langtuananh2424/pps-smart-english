package com.pps.smartenglish.lms.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pps.smartenglish.lessoncontent.LessonContentDtos.GlossaryTerm;
import com.pps.smartenglish.lessoncontent.SubtitleParser.Cue;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * courseId..levelId: để player dựng breadcrumb và sidebar (D-60). subtitles (khóa en, vi) và glossary: D-175, chỉ có khi đã qua cổng
 * quyền xem như video.
 */
public record LessonResponse(Long id, String title, String type, Integer durationSec, VideoInfo video, Long courseId,
                             String courseSlug, String courseTitle, Long topicId, String topicTitle, Long levelId, Map<String, List<Cue>> subtitles,
                             List<GlossaryTerm> glossary) {

    /** STORAGE: url + expiresAt; YOUTUBE: ref. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record VideoInfo(String source, String url, String ref, Instant expiresAt) {}
}
