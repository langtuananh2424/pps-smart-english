package com.pps.smartenglish.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** Không có trường nào mang đáp án: không answerKey, is_correct hay explanation. */
public record BankResponse(Long id, String title, Long lessonId, boolean isTrial, List<QuestionView> questions) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QuestionView(Long id, String type, String prompt, JsonNode payload, List<ChoiceView> choices,
                        List<HintView> hints) {}

    public record ChoiceView(Long id, String label, String content) {}

    /** FR-AI-06: gợi ý soạn sẵn; chỉ có ở bộ PRACTICE, bộ EXAM luôn là mảng rỗng. */
    public record HintView(Long id, String content, String detail) {}
}
