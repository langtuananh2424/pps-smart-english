package com.pps.smartenglish.exercise.grading;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Chọn grader theo type. shortcut: CONNECTIVE_REWRITE, GRAMMAR_REWRITE, WRITING, SPEAKING chưa có grader vì chấm AI,
 * chờ module ai; hiện chỉ lưu câu trả lời, is_correct null và không tính vào điểm.
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

    /** Dạng tự chấm: true/false; dạng AI: null. Câu trả lời sai định dạng tính sai, không ném lỗi. */
    public Boolean grade(QuestionType type, GradingInput input, JsonNode answer) {
        var grader = graders.get(type);
        if (grader == null) {
            return null;
        }
        try {
            return grader.isCorrect(input, answer == null ? MissingNode.getInstance() : answer);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Đáp án đúng; null với dạng AI (không có đáp án cố định). */
    public JsonNode correctAnswer(QuestionType type, GradingInput input) {
        var grader = graders.get(type);
        return grader == null ? null : grader.correctAnswer(input);
    }
}
