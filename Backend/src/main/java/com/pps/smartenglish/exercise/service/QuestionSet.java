package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.exercise.domain.Question;
import com.pps.smartenglish.exercise.domain.QuestionChoice;
import com.pps.smartenglish.exercise.dto.ChoiceNote;
import com.pps.smartenglish.exercise.grading.GradingInput;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Câu hỏi của một bank (đã theo sort_order) cùng các lựa chọn, nạp bằng hai truy vấn cố định. */
public record QuestionSet(List<Question> questions, Map<Long, List<QuestionChoice>> choices) {

    public List<QuestionChoice> choicesOf(Question q) {
        return choices.getOrDefault(q.getId(), List.of());
    }

    /** Giải thích từng phương án (FR-EX-03); null khi câu không có phương án nào được giải thích. */
    public List<ChoiceNote> choiceNotes(Question q) {
        var notes = choicesOf(q).stream().filter(c -> c.getExplanation() != null && !c.getExplanation().isBlank())
                .map(c -> new ChoiceNote(c.getLabel(), c.isCorrect(), c.getExplanation())).toList();
        return notes.isEmpty() ? null : notes;
    }

    public GradingInput inputOf(Question q) {
        Set<Long> correct = choicesOf(q).stream().filter(QuestionChoice::isCorrect).map(QuestionChoice::getId)
                .collect(Collectors.toSet());
        return new GradingInput(q.getAnswerKey(), correct, q.getPayload());
    }
}
