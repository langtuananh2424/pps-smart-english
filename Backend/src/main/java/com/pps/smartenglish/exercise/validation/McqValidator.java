package com.pps.smartenglish.exercise.validation;

import com.pps.smartenglish.exercise.domain.QuestionType;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Trắc nghiệm: đáp án nằm ở choices (is_correct); không có payload và answerKey. */
@Component
class McqValidator implements QuestionValidator {

    @Override
    public QuestionType type() {
        return QuestionType.CONNECTIVE_MCQ;
    }

    @Override
    public void validate(QuestionDraft d, Set<String> grammarCodes) {
        JsonRules.requireNone(d.payload(), "payload", "Trắc nghiệm không có payload");
        JsonRules.requireNone(d.answerKey(), "answerKey", "Đáp án trắc nghiệm nằm ở choices, không có answerKey");
        var choices = d.choices();
        if (choices.size() < 2) {
            throw JsonRules.bad("choices", "Cần ít nhất 2 lựa chọn");
        }
        Set<String> labels = new HashSet<>();
        long correct = 0;
        for (int i = 0; i < choices.size(); i++) {
            var c = choices.get(i);
            String at = "choices[" + i + "]";
            if (c.label() == null || c.label().isBlank() || c.label().length() > 10) {
                throw JsonRules.bad(at + ".label", "Bắt buộc, tối đa 10 ký tự");
            }
            if (!labels.add(c.label().strip())) {
                throw JsonRules.bad(at + ".label", "Nhãn bị lặp");
            }
            if (c.content() == null || c.content().isBlank()) {
                throw JsonRules.bad(at + ".content", "Bắt buộc");
            }
            correct += c.correct() ? 1 : 0;
        }
        if (correct != 1) {
            throw JsonRules.bad("choices", "Phải có đúng 1 lựa chọn đúng");
        }
    }
}
