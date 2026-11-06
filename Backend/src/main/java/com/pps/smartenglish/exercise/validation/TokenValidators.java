package com.pps.smartenglish.exercise.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Hai dạng làm việc trên danh sách token của câu: nhận diện cấu trúc và tìm-sửa lỗi. Chỉ số token tính từ 0. */
final class TokenValidators {

    private TokenValidators() {}

    private static int tokenCount(JsonNode payload) {
        return JsonRules.texts(JsonRules.array(payload, "tokens", "payload", 1), "payload.tokens", false).size();
    }

    /** payload {tokens[]}; key {spans:[{start,end,element}]}, end tính cả, element là mã yếu tố có thật. */
    @Component
    static class Identify implements QuestionValidator {

        @Override
        public QuestionType type() {
            return QuestionType.GRAMMAR_IDENTIFY;
        }

        @Override
        public void validate(QuestionDraft d, Set<String> grammarCodes) {
            int n = tokenCount(JsonRules.object(d.payload(), "payload", Set.of("tokens")));
            JsonNode key = JsonRules.object(d.answerKey(), "answerKey", Set.of("spans"));
            JsonNode spans = JsonRules.array(key, "spans", "answerKey", 1);
            for (int i = 0; i < spans.size(); i++) {
                String at = "answerKey.spans[" + i + "]";
                JsonNode s = JsonRules.object(spans.get(i), at, Set.of("start", "end", "element"));
                int start = JsonRules.integer(s, "start", at);
                int end = JsonRules.integer(s, "end", at);
                if (start < 0 || end < start || end >= n) {
                    throw JsonRules.bad(at, "Cần 0 <= start <= end < số token (" + n + ")");
                }
                if (!grammarCodes.contains(JsonRules.text(s, "element", at))) {
                    throw JsonRules.bad(at + ".element", "Mã yếu tố ngữ pháp không tồn tại");
                }
            }
        }
    }

    /** payload {tokens[]}; key {position, corrections[]}. */
    @Component
    static class SpotFix implements QuestionValidator {

        @Override
        public QuestionType type() {
            return QuestionType.GRAMMAR_SPOT_FIX;
        }

        @Override
        public void validate(QuestionDraft d, Set<String> grammarCodes) {
            int n = tokenCount(JsonRules.object(d.payload(), "payload", Set.of("tokens")));
            JsonNode key = JsonRules.object(d.answerKey(), "answerKey", Set.of("position", "corrections"));
            int position = JsonRules.integer(key, "position", "answerKey");
            if (position < 0 || position >= n) {
                throw JsonRules.bad("answerKey.position", "Phải nằm trong 0.." + (n - 1));
            }
            JsonRules.texts(JsonRules.array(key, "corrections", "answerKey", 1), "answerKey.corrections", false);
        }
    }
}
