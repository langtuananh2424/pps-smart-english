package com.pps.smartenglish.exercise.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pps.smartenglish.ai.dto.RewriteGrades;
import com.pps.smartenglish.ai.service.RewriteResultSink;
import com.pps.smartenglish.exercise.domain.AnswerGrading;
import com.pps.smartenglish.exercise.repository.AnswerGradingRepository;
import com.pps.smartenglish.exercise.repository.StudentAnswerRepository;
import com.pps.smartenglish.exercise.repository.StudentAttemptRepository;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Nhận kết quả AI cho câu viết lại: ghi is_correct (một lần), student_answer_grading grader=AI và cộng điểm lượt làm bài. */
@Component
class RewriteResultApplier implements RewriteResultSink {

    private final StudentAnswerRepository answers;
    private final AnswerGradingRepository gradings;
    private final StudentAttemptRepository attempts;
    private final ObjectMapper mapper;

    RewriteResultApplier(StudentAnswerRepository answers, AnswerGradingRepository gradings, StudentAttemptRepository attempts,
                         ObjectMapper mapper) {
        this.answers = answers;
        this.gradings = gradings;
        this.attempts = attempts;
        this.mapper = mapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void apply(long attemptId, List<RewriteGrades.Item> items) {
        int points = 0;
        for (var item : items) {
            if (answers.applyAiVerdict(attemptId, item.id(), item.correct()) == 0) {
                continue; // đã có kết quả (chạy lại job): không ghi đè, không cộng điểm lần hai
            }
            var answer = answers.findByAttemptIdAndQuestionId(attemptId, item.id()).orElseThrow();
            var result = mapper.createObjectNode().put("feedback", item.feedback()).put("correctedAnswer", item.correctedAnswer());
            gradings.save(new AnswerGrading(answer.getId(), AnswerGrading.AI, item.correct() ? 1 : 0, result));
            points += item.correct() ? 1 : 0;
        }
        if (points > 0) {
            attempts.addScore(attemptId, points);
        }
    }
}
