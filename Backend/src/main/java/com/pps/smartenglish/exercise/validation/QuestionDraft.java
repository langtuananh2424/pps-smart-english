package com.pps.smartenglish.exercise.validation;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** Dữ liệu soạn của một câu hỏi đã gom đủ (kể cả khi PATCH: trạng thái sau khi áp thay đổi). */
public record QuestionDraft(JsonNode payload, JsonNode answerKey, List<ChoiceDraft> choices) {

    public record ChoiceDraft(String label, String content, boolean correct, String explanation) {}
}
