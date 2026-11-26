package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.exercise.domain.Question;
import com.pps.smartenglish.exercise.domain.QuestionChoice;
import com.pps.smartenglish.exercise.domain.StudentAttempt;
import com.pps.smartenglish.exercise.repository.AttemptQuestionRepository;
import com.pps.smartenglish.exercise.repository.QuestionChoiceRepository;
import com.pps.smartenglish.exercise.repository.QuestionRepository;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class QuestionLoader {

    private final QuestionRepository questions;
    private final QuestionChoiceRepository choices;
    private final AttemptQuestionRepository extra;

    QuestionLoader(QuestionRepository questions, QuestionChoiceRepository choices, AttemptQuestionRepository extra) {
        this.questions = questions;
        this.choices = choices;
        this.extra = extra;
    }

    /** Câu còn hiệu lực của lượt: cả bộ, hoặc danh sách riêng của lượt Luyện thêm (D-152). */
    public QuestionSet load(StudentAttempt a) {
        if (!a.isExtra()) {
            return load(a.getBankId());
        }
        return withChoices(ofIds(a.getId()).stream().filter(q -> q.getArchivedAt() == null).toList());
    }

    /** Xem lại lượt đã nộp: như loadForHistory(bankId, ...) nhưng theo lượt. */
    public QuestionSet loadForHistory(StudentAttempt a, Collection<Long> answeredQuestionIds) {
        if (!a.isExtra()) {
            return loadForHistory(a.getBankId(), answeredQuestionIds);
        }
        return withChoices(ofIds(a.getId()).stream().filter(q -> q.getArchivedAt() == null || answeredQuestionIds.contains(q.getId())).toList());
    }

    private List<Question> ofIds(Long attemptId) {
        var ids = extra.questionIds(attemptId);
        var byId = questions.findAllById(ids).stream().collect(Collectors.toMap(Question::getId, q -> q));
        return ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    /** Câu còn hiệu lực: cho lượt làm mới, không có câu đã lưu trữ (D-138). */
    public QuestionSet load(Long bankId) {
        return withChoices(questions.findActiveByBankId(bankId));
    }

    /** Xem lại bài đã nộp: câu còn hiệu lực cộng câu đã lưu trữ nhưng học viên đã trả lời, để lịch sử không mất. */
    public QuestionSet loadForHistory(Long bankId, Collection<Long> answeredQuestionIds) {
        return withChoices(questions.findByBankIdOrderBySortOrderAscIdAsc(bankId).stream()
                .filter(q -> q.getArchivedAt() == null || answeredQuestionIds.contains(q.getId())).toList());
    }

    private QuestionSet withChoices(List<Question> qs) {
        var byQuestion = qs.isEmpty() ? java.util.Map.<Long, List<QuestionChoice>>of()
                : choices.findByQuestionIdInOrderByLabelAscIdAsc(qs.stream().map(Question::getId).toList()).stream()
                        .collect(Collectors.groupingBy(QuestionChoice::getQuestionId));
        return new QuestionSet(qs, byQuestion);
    }
}
