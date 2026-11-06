package com.pps.smartenglish.exercise.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Hai dạng viết lại. answerKey không bắt buộc (RewriteRules chấp nhận key null: câu không khớp luật sẽ do AI chấm), nếu có
 * thì chỉ {acceptedAnswers: [chuỗi]}.
 */
final class RewriteValidators {

    private RewriteValidators() {}

    private static void sentenceAndKey(JsonNode payload, QuestionDraft d) {
        JsonRules.text(payload, "sentence", "payload");
        if (d.answerKey() != null) {
            JsonNode key = JsonRules.object(d.answerKey(), "answerKey", Set.of("acceptedAnswers"));
            JsonRules.texts(JsonRules.array(key, "acceptedAnswers", "answerKey", 0), "answerKey.acceptedAnswers", false);
        }
    }

    /** payload {sentence, requiredWord}: từ nối bắt buộc có trong câu trả lời (RewriteRules kiểm nguyên từ). */
    @Component
    static class Connective implements QuestionValidator {

        @Override
        public QuestionType type() {
            return QuestionType.CONNECTIVE_REWRITE;
        }

        @Override
        public void validate(QuestionDraft d, Set<String> grammarCodes) {
            JsonNode payload = JsonRules.object(d.payload(), "payload", Set.of("sentence", "requiredWord"));
            JsonRules.text(payload, "requiredWord", "payload");
            sentenceAndKey(payload, d);
        }
    }

    /** payload {sentence, requiredElement}: requiredElement là mã yếu tố ngữ pháp có thật. */
    @Component
    static class Grammar implements QuestionValidator {

        @Override
        public QuestionType type() {
            return QuestionType.GRAMMAR_REWRITE;
        }

        @Override
        public void validate(QuestionDraft d, Set<String> grammarCodes) {
            JsonNode payload = JsonRules.object(d.payload(), "payload", Set.of("sentence", "requiredElement"));
            if (!grammarCodes.contains(JsonRules.text(payload, "requiredElement", "payload"))) {
                throw JsonRules.bad("payload.requiredElement", "Mã yếu tố ngữ pháp không tồn tại");
            }
            sentenceAndKey(payload, d);
        }
    }
}
