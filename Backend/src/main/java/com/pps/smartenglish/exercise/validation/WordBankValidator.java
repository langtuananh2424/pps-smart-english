package com.pps.smartenglish.exercise.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Hộp từ: payload {text, blanks[], words[], allowReuse?: boolean (mặc định false), scoring?: PARTIAL|ALL}, chỗ trống viết {id} trong text như cloze;
 * key {blanks:{id:từ}} với từ thuộc words; allowReuse = false thì mỗi từ dùng cho tối đa một chỗ trống.
 */
@Component
class WordBankValidator implements QuestionValidator {

    @Override
    public QuestionType type() {
        return QuestionType.WORD_BANK;
    }

    @Override
    public void validate(QuestionDraft d, Set<String> grammarCodes) {
        JsonNode payload = JsonRules.object(d.payload(), "payload", Set.of("text", "blanks", "words", "allowReuse", "scoring"));
        JsonRules.scoring(payload);
        Set<String> blanks = JsonRules.texts(JsonRules.boundedArray(payload, "blanks", "payload", 1), "payload.blanks", true);
        if (!ClozeValidator.placeholders(JsonRules.text(payload, "text", "payload", JsonRules.LONG_TEXT_MAX)).equals(blanks)) {
            throw JsonRules.bad("payload.text", "Mỗi chỗ trống {id} trong text phải có trong blanks và ngược lại");
        }
        Set<String> words = new HashSet<>();
        for (String w : JsonRules.texts(JsonRules.boundedArray(payload, "words", "payload", 1), "payload.words", false)) {
            if (!words.add(w.trim().toLowerCase(Locale.ROOT))) {
                throw JsonRules.bad("payload.words", "Từ \"" + w + "\" bị lặp trong hộp từ");
            }
        }
        boolean reuse = false;
        if (payload.has("allowReuse")) {
            if (!payload.get("allowReuse").isBoolean()) {
                throw JsonRules.bad("payload.allowReuse", "Phải là true hoặc false");
            }
            reuse = payload.get("allowReuse").asBoolean();
        }

        JsonNode key = JsonRules.object(d.answerKey(), "answerKey", Set.of("blanks"));
        JsonNode answers = key.get("blanks");
        if (answers == null || !answers.isObject() || !JsonRules.fieldNames(answers).equals(blanks)) {
            throw JsonRules.bad("answerKey.blanks", "Phải có đáp án cho đúng từng chỗ trống trong payload.blanks");
        }
        Set<String> used = new HashSet<>();
        for (var e : answers.properties()) {
            String at = "answerKey.blanks." + e.getKey();
            String word = e.getValue().isTextual() ? e.getValue().asText().trim().toLowerCase(Locale.ROOT) : null;
            if (word == null || !words.contains(word)) {
                throw JsonRules.bad(at, "Phải là một từ trong payload.words");
            }
            if (!used.add(word) && !reuse) {
                throw JsonRules.bad(at, "Từ này đã dùng ở chỗ trống khác; bật allowReuse nếu cho dùng lại");
            }
        }
    }
}
