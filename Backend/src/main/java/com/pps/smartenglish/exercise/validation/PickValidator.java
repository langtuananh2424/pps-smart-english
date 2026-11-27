package com.pps.smartenglish.exercise.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import java.util.Set;

/**
 * Cơ sở cho các dạng chọn một giá trị: payload gồm các chuỗi bắt buộc; key {field: giá trị}. allowed != null là tập giá trị cố định
 * (TFNG, YNNG); null thì giá trị là id trong layout của nhóm, được kiểm chéo khi lưu (GroupLayoutCheck).
 */
abstract class PickValidator implements QuestionValidator {

    private final QuestionType type;
    private final Set<String> payloadFields;
    private final String keyField;
    private final Set<String> allowed;

    PickValidator(QuestionType type, Set<String> payloadFields, String keyField, Set<String> allowed) {
        this.type = type;
        this.payloadFields = payloadFields;
        this.keyField = keyField;
        this.allowed = allowed;
    }

    @Override
    public QuestionType type() {
        return type;
    }

    @Override
    public void validate(QuestionDraft d, Set<String> grammarCodes) {
        JsonNode payload = JsonRules.object(d.payload(), "payload", payloadFields);
        payloadFields.forEach(f -> JsonRules.text(payload, f, "payload", JsonRules.TEXT_MAX));
        JsonNode key = JsonRules.object(d.answerKey(), "answerKey", Set.of(keyField));
        String value = JsonRules.text(key, keyField, "answerKey", JsonRules.TEXT_MAX);
        if (allowed != null && !allowed.contains(value)) {
            throw JsonRules.bad("answerKey." + keyField, "Phải là một trong " + allowed);
        }
    }
}
