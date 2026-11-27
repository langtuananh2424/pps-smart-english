package com.pps.smartenglish.exercise.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Nghe rồi nộp audio (D-154 mục 4): payload {prompt, maxSeconds? 10..180}; không có answerKey. Audio mẫu nằm ở nhóm (GroupLayoutCheck bắt buộc). */
@Component
class ListenRecordValidator implements QuestionValidator {

    @Override
    public QuestionType type() {
        return QuestionType.LISTEN_RECORD;
    }

    @Override
    public void validate(QuestionDraft d, Set<String> grammarCodes) {
        JsonNode payload = JsonRules.object(d.payload(), "payload", Set.of("prompt", "maxSeconds"));
        if (JsonRules.text(payload, "prompt", "payload").length() > SpeakingValidator.MAX_PROMPT) {
            throw JsonRules.bad("payload.prompt", "Tối đa " + SpeakingValidator.MAX_PROMPT + " ký tự");
        }
        if (payload.has("maxSeconds")) {
            int seconds = JsonRules.integer(payload, "maxSeconds", "payload");
            if (seconds < SpeakingValidator.MIN_SECONDS || seconds > SpeakingValidator.MAX_SECONDS) {
                throw JsonRules.bad("payload.maxSeconds", "Từ " + SpeakingValidator.MIN_SECONDS + " đến " + SpeakingValidator.MAX_SECONDS + " giây");
            }
        }
        JsonRules.requireNone(d.answerKey(), "answerKey", "Câu nộp bản ghi không có answerKey");
    }
}
