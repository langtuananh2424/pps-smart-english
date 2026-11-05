package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.exercise.domain.Question;
import com.pps.smartenglish.exercise.domain.QuestionChoice;
import com.pps.smartenglish.exercise.grading.GradingInput;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Câu hỏi của một bank (đã theo sort_order) cùng các lựa chọn, nạp bằng hai truy vấn cố định. */
record QuestionSet(List<Question> questions, Map<Long, List<QuestionChoice>> choices) {

    List<QuestionChoice> choicesOf(Question q) {
        return choices.getOrDefault(q.getId(), List.of());
    }

    GradingInput inputOf(Question q) {
        Set<Long> correct = choicesOf(q).stream().filter(QuestionChoice::isCorrect).map(QuestionChoice::getId)
                .collect(Collectors.toSet());
        return new GradingInput(q.getAnswerKey(), correct);
    }
}
