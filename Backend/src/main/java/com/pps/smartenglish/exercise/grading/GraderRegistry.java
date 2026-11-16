package com.pps.smartenglish.exercise.grading;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Chọn grader theo type, chấm thuần trong bộ nhớ. Các dạng không có ở đây không chấm theo answer_key:
 * CONNECTIVE_REWRITE và GRAMMAR_REWRITE đi qua RewriteRules rồi job AI trong SubmissionService;
 * WRITING chấm ở luồng phiên viết của module ai; SPEAKING chấm ở phiên nói của module ai (FR-AI-03).
 */
@Component
public class GraderRegistry {

    private final Map<QuestionType, QuestionGrader> graders = new EnumMap<>(QuestionType.class);

    GraderRegistry(List<QuestionGrader> all) {
        all.forEach(g -> graders.put(g.type(), g));
    }

    public boolean isAutoGraded(QuestionType type) {
        return graders.containsKey(type);
    }

    /** Dạng tự chấm: credit 0..1; dạng AI: null. Câu trả lời sai định dạng tính 0, không ném lỗi. */
    public BigDecimal credit(QuestionType type, GradingInput input, JsonNode answer) {
        var grader = graders.get(type);
        if (grader == null) {
            return null;
        }
        try {
            return grader.credit(input, answer == null ? MissingNode.getInstance() : answer);
        } catch (RuntimeException e) {
            return Credit.ZERO;
        }
    }

    /** Đáp án đúng; null với dạng AI (không có đáp án cố định). */
    public JsonNode correctAnswer(QuestionType type, GradingInput input) {
        var grader = graders.get(type);
        return grader == null ? null : grader.correctAnswer(input);
    }
}
