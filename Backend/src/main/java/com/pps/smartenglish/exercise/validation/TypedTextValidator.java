package com.pps.smartenglish.exercise.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import com.pps.smartenglish.exercise.grading.TypedText;
import java.util.HashSet;
import java.util.Set;

/**
 * Cơ sở cho các dạng gõ chữ: payload gồm các chuỗi bắt buộc (required), chuỗi tùy chọn (optional) và maxWords tùy chọn (>= 1);
 * key {accepted:[...]}, mỗi đáp án không rỗng sau khi chuẩn hóa và không vượt maxWords (cùng luật 4 với grader).
 */
abstract class TypedTextValidator implements QuestionValidator {

    private final QuestionType type;
    private final Set<String> required;
    private final Set<String> optional;
    private final boolean dropPunct;

    TypedTextValidator(QuestionType type, Set<String> required, Set<String> optional, boolean dropPunct) {
        this.type = type;
        this.required = required;
        this.optional = optional;
        this.dropPunct = dropPunct;
    }

    @Override
    public QuestionType type() {
        return type;
    }

    /** Ràng buộc riêng của dạng, chạy với payload đã qua whitelist (có thể null khi dạng không có trường bắt buộc). */
    void extra(JsonNode payload) {}

    @Override
    public void validate(QuestionDraft d, Set<String> grammarCodes) {
        int maxWords = Integer.MAX_VALUE;
        if (d.payload() == null && !required.isEmpty()) {
            throw JsonRules.bad("payload", "Bắt buộc, có " + required);
        }
        if (d.payload() != null) {
            Set<String> allowed = new HashSet<>(required);
            allowed.addAll(optional);
            allowed.add("maxWords");
            JsonNode payload = JsonRules.object(d.payload(), "payload", allowed);
            required.forEach(f -> JsonRules.text(payload, f, "payload", JsonRules.TEXT_MAX));
            optional.stream().filter(payload::has).forEach(f -> JsonRules.text(payload, f, "payload", JsonRules.TEXT_MAX));
            if (payload.has("maxWords")) {
                maxWords = JsonRules.integer(payload, "maxWords", "payload");
                if (maxWords < 1) {
                    throw JsonRules.bad("payload.maxWords", "Phải từ 1 trở lên");
                }
            }
        }
        extra(d.payload());
        JsonNode key = JsonRules.object(d.answerKey(), "answerKey", Set.of("accepted"));
        for (String a : JsonRules.texts(JsonRules.boundedArray(key, "accepted", "answerKey", 1), "answerKey.accepted", false)) {
            String formed = TypedText.form(a, dropPunct);
            if (formed.isEmpty()) {
                throw JsonRules.bad("answerKey.accepted", "Đáp án \"" + a + "\" rỗng sau khi bỏ khoảng trắng và dấu câu");
            }
            if (TypedText.words(formed) > maxWords) {
                throw JsonRules.bad("answerKey.accepted", "Đáp án \"" + a + "\" vượt giới hạn " + maxWords + " từ");
            }
        }
    }
}
