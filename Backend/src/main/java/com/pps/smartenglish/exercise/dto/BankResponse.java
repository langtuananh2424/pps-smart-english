package com.pps.smartenglish.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** Không có trường nào mang đáp án: không answerKey, is_correct hay explanation. */
public record BankResponse(Long id, String title, Long lessonId, boolean isTrial, List<QuestionView> questions) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QuestionView(Long id, String type, String prompt, JsonNode payload, List<ChoiceView> choices) {}

    public record ChoiceView(Long id, String label, String content) {}
}
