package com.pps.smartenglish.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/** FR-EX-07, D-152. */
public final class ExtraDtos {

    private ExtraDtos() {}

    public record Overview(Long courseId, String courseTitle, int reviewSize, int challengeSize, double maxRatio,
                           List<LevelView> levels, List<TopicCard> review, List<TopicCard> challenge) {}

    public record LevelView(Long id, String title) {}

    /**
     * state: COMPLETED (Ôn lại), OPEN (Thử sức), PAID (chưa mua), LOCKED (chờ học tuần tự). available: số câu hợp lệ theo dạng
     * (AUTO, WRITING, SPEAKING); học thử chỉ có AUTO. trialOnly: chỉ có quyền học thử.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TopicCard(Long topicId, String title, Long levelId, String levelTitle, int levelNo, int topicNo, String state,
                            Integer accuracy, Integer questions, Map<String, Integer> available, boolean trialOnly,
                            Long blockedByTopicId, String blockedByTitle) {}

    /** scope: REVIEW | CHALLENGE; topicId bắt buộc với CHALLENGE; kind: AUTO | WRITING | SPEAKING, vắng = mọi dạng. */
    public record StartRequest(String scope, Long topicId, String kind) {}
}
