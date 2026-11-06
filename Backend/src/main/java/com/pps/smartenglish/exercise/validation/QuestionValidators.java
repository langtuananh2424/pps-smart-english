package com.pps.smartenglish.exercise.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Chọn validator theo dạng (cùng kiểu GraderRegistry). Dạng chưa có validator (SPEAKING) bị từ chối. */
@Component
public class QuestionValidators {

    private final Map<QuestionType, QuestionValidator> validators = new EnumMap<>(QuestionType.class);

    QuestionValidators(List<QuestionValidator> all) {
        all.forEach(v -> validators.put(v.type(), v));
    }

    /** payload/answerKey rỗng (null, JSON null, {}) coi như vắng mặt; trả null cho chúng. */
    public static JsonNode normalize(JsonNode n) {
        return n == null || n.isNull() || (n.isObject() && n.isEmpty()) ? null : n;
    }

    public void validate(QuestionType type, QuestionDraft draft, Set<String> grammarCodes) {
        var validator = validators.get(type);
        if (validator == null) {
            throw JsonRules.bad("type", "Dạng " + type + " chưa được hỗ trợ trong CMS");
        }
        if (type != QuestionType.CONNECTIVE_MCQ && !draft.choices().isEmpty()) {
            throw JsonRules.bad("choices", "Chỉ câu trắc nghiệm dùng choices");
        }
        validator.validate(draft, grammarCodes);
    }
}
