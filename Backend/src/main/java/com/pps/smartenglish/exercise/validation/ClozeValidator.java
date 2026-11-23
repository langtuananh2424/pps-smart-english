package com.pps.smartenglish.exercise.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Điền từ: payload {text, blanks[], labels?:{id:tên chỗ trống}} với chỗ trống viết {id} trong text; key {blanks:{id:[đáp án chấp nhận]}}. */
@Component
class ClozeValidator implements QuestionValidator {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]+)}");

    @Override
    public QuestionType type() {
        return QuestionType.CONNECTIVE_CLOZE;
    }

    @Override
    public void validate(QuestionDraft d, Set<String> grammarCodes) {
        JsonNode payload = JsonRules.object(d.payload(), "payload", Set.of("text", "blanks", "labels"));
        String text = JsonRules.text(payload, "text", "payload");
        Set<String> blanks = JsonRules.texts(JsonRules.array(payload, "blanks", "payload", 1), "payload.blanks", true);

        if (!placeholders(text).equals(blanks)) {
            throw JsonRules.bad("payload.text", "Mỗi chỗ trống {id} trong text phải có trong blanks và ngược lại");
        }

        validateLabels(payload.get("labels"), blanks);

        JsonNode key = JsonRules.object(d.answerKey(), "answerKey", Set.of("blanks"));
        JsonNode answers = key.get("blanks");
        if (answers == null || !answers.isObject() || !JsonRules.fieldNames(answers).equals(blanks)) {
            throw JsonRules.bad("answerKey.blanks", "Phải có đáp án cho đúng từng chỗ trống trong payload.blanks");
        }
        answers.fields().forEachRemaining(e -> {
            if (!e.getValue().isArray() || e.getValue().isEmpty()) {
                throw JsonRules.bad("answerKey.blanks." + e.getKey(), "Cần ít nhất một đáp án chấp nhận");
            }
            JsonRules.texts(e.getValue(), "answerKey.blanks." + e.getKey(), false);
        });
    }

    /** Các id chỗ trống viết dạng {id} trong text (dùng chung với WORD_BANK). */
    static Set<String> placeholders(String text) {
        Set<String> out = new HashSet<>();
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    /** D-70: tên chỗ trống tùy chọn; khóa phải là id chỗ trống có thật, mỗi tên không rỗng và tối đa 100 ký tự. */
    private static void validateLabels(JsonNode labels, Set<String> blanks) {
        if (labels == null) {
            return;
        }
        if (!labels.isObject()) {
            throw JsonRules.bad("payload.labels", "Phải là object {idChỗTrống: tên}");
        }
        labels.fields().forEachRemaining(e -> {
            String at = "payload.labels." + e.getKey();
            if (!blanks.contains(e.getKey())) {
                throw JsonRules.bad(at, "Không phải chỗ trống trong payload.blanks");
            }
            if (!e.getValue().isTextual() || e.getValue().asText().isBlank() || e.getValue().asText().length() > 100) {
                throw JsonRules.bad(at, "Tên chỗ trống phải là chữ không rỗng, tối đa 100 ký tự");
            }
        });
    }
}
