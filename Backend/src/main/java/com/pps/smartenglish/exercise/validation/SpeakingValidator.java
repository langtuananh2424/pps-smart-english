package com.pps.smartenglish.exercise.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Câu nói (FR-AI-03): payload {part 1|2|3, prompt (tối đa 2000 ký tự), cueCard? (chỉ part 2), maxSeconds? 10..180}; không có answerKey.
 * Thiếu maxSeconds thì mặc định theo part ở SpeakingQuestionProvider (D-43).
 */
@Component
class SpeakingValidator implements QuestionValidator {

    static final int MAX_PROMPT = 2000;
    static final int MIN_SECONDS = 10;
    static final int MAX_SECONDS = 180;

    @Override
    public QuestionType type() {
        return QuestionType.SPEAKING;
    }

    @Override
    public void validate(QuestionDraft d, Set<String> grammarCodes) {
        JsonNode payload = JsonRules.object(d.payload(), "payload", Set.of("part", "prompt", "cueCard", "maxSeconds"));
        int part = JsonRules.integer(payload, "part", "payload");
        if (part < 1 || part > 3) {
            throw JsonRules.bad("payload.part", "Chỉ nhận 1, 2 hoặc 3");
        }
        if (JsonRules.text(payload, "prompt", "payload").length() > MAX_PROMPT) {
            throw JsonRules.bad("payload.prompt", "Tối đa " + MAX_PROMPT + " ký tự");
        }
        if (payload.has("cueCard")) {
            if (part != 2) {
                throw JsonRules.bad("payload.cueCard", "Chỉ dùng cho part 2");
            }
            JsonRules.texts(JsonRules.array(payload, "cueCard", "payload", 1), "payload.cueCard", false);
        }
        if (payload.has("maxSeconds")) {
            int seconds = JsonRules.integer(payload, "maxSeconds", "payload");
            if (seconds < MIN_SECONDS || seconds > MAX_SECONDS) {
                throw JsonRules.bad("payload.maxSeconds", "Từ " + MIN_SECONDS + " đến " + MAX_SECONDS + " giây");
            }
        }
        JsonRules.requireNone(d.answerKey(), "answerKey", "Câu nói không có answerKey");
    }
}
