package com.pps.smartenglish.exercise.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Điền từ: payload {text, blanks[]} với chỗ trống viết {id} trong text; key {blanks:{id:[đáp án chấp nhận]}}. */
@Component
class ClozeValidator implements QuestionValidator {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]+)}");

    @Override
    public QuestionType type() {
        return QuestionType.CONNECTIVE_CLOZE;
    }

    @Override
    public void validate(QuestionDraft d, Set<String> grammarCodes) {
        JsonNode payload = JsonRules.object(d.payload(), "payload", Set.of("text", "blanks"));
        String text = JsonRules.text(payload, "text", "payload");
        Set<String> blanks = JsonRules.texts(JsonRules.array(payload, "blanks", "payload", 1), "payload.blanks", true);

        Set<String> inText = new HashSet<>();
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) {
            inText.add(m.group(1));
        }
        if (!inText.equals(blanks)) {
            throw JsonRules.bad("payload.text", "Mỗi chỗ trống {id} trong text phải có trong blanks và ngược lại");
        }

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
}
