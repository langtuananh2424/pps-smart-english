package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.exercise.domain.Question;
import com.pps.smartenglish.exercise.domain.QuestionChoice;
import com.pps.smartenglish.exercise.repository.QuestionChoiceRepository;
import com.pps.smartenglish.exercise.repository.QuestionRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
class QuestionLoader {

    private final QuestionRepository questions;
    private final QuestionChoiceRepository choices;

    QuestionLoader(QuestionRepository questions, QuestionChoiceRepository choices) {
        this.questions = questions;
        this.choices = choices;
    }

    QuestionSet load(Long bankId) {
        List<Question> qs = questions.findByBankIdOrderBySortOrderAscIdAsc(bankId);
        var byQuestion = qs.isEmpty() ? java.util.Map.<Long, List<QuestionChoice>>of()
                : choices.findByQuestionIdInOrderByLabelAscIdAsc(qs.stream().map(Question::getId).toList()).stream()
                        .collect(Collectors.groupingBy(QuestionChoice::getQuestionId));
        return new QuestionSet(qs, byQuestion);
    }
}
