package com.pps.smartenglish.exercise.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * D-159: câu quiz sau video. Đáp án ở choices (is_correct), không có answerKey; giải thích tiếng Việt ở cột explanation của câu
 * (payload gửi nguyên cho học viên nên không chứa giải thích). payload tùy chọn {multiple}: false (mặc định) đúng 1 đáp án đúng,
 * true cho phép nhiều đáp án đúng nhưng vẫn cần ít nhất một lựa chọn sai.
 */
@Component
class VideoConnectiveValidator implements QuestionValidator {

    @Override
    public QuestionType type() {
        return QuestionType.VIDEO_CONNECTIVE;
    }

    @Override
    public void validate(QuestionDraft d, Set<String> grammarCodes) {
        boolean multiple = false;
        if (d.payload() != null) {
            JsonNode payload = JsonRules.object(d.payload(), "payload", Set.of("multiple"));
            if (!payload.has("multiple") || !payload.get("multiple").isBoolean()) {
                throw JsonRules.bad("payload.multiple", "Phải là true hoặc false");
            }
            multiple = payload.get("multiple").asBoolean();
        }
        JsonRules.requireNone(d.answerKey(), "answerKey", "Đáp án nằm ở choices, không có answerKey");
        long correct = McqValidator.countCorrect(d.choices());
        if (correct < 1 || correct >= d.choices().size()) {
            throw JsonRules.bad("choices", "Cần ít nhất 1 lựa chọn đúng và 1 lựa chọn sai");
        }
        if (!multiple && correct != 1) {
            throw JsonRules.bad("choices", "Câu một đáp án phải có đúng 1 lựa chọn đúng");
        }
    }
}
