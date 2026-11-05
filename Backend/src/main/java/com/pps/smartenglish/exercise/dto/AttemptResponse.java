package com.pps.smartenglish.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

/** Bài chưa nộp: chỉ có id, bankId, startedAt (các trường null bị bỏ), tuyệt đối không có đáp án. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AttemptResponse(Long id, Long bankId, Instant startedAt, Instant submittedAt, Integer score, Integer maxScore,
                              List<QuestionResult> results, List<String> pendingJobs) {

    /** isCorrect null = dạng chấm AI: khi đó không có correctAnswer. */
    public record QuestionResult(Long questionId, Boolean isCorrect,
                                 @JsonInclude(JsonInclude.Include.NON_NULL) JsonNode correctAnswer,
                                 @JsonInclude(JsonInclude.Include.NON_NULL) String explanation) {}
}
