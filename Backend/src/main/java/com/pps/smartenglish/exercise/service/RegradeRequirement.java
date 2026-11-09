package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.exercise.domain.Question;
import com.pps.smartenglish.exercise.repository.RegradeRunRepository;
import com.pps.smartenglish.exercise.repository.StudentAnswerRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Câu nào cần Admin xác nhận chấm lại: có bài nộp, dạng chấm lại được, và chưa có run DONE hoặc RUNNING ở phiên bản đáp án
 * hiện tại. Hai truy vấn cho cả danh sách, dùng chung cho chi tiết câu hỏi và màn xem trước.
 */
@Component
class RegradeRequirement {

    private final StudentAnswerRepository answers;
    private final RegradeRunRepository runs;

    RegradeRequirement(StudentAnswerRepository answers, RegradeRunRepository runs) {
        this.answers = answers;
        this.runs = runs;
    }

    /** Id các câu trong danh sách đang cần chấm lại. */
    Set<Long> among(List<Question> questions) {
        List<Long> candidates = questions.stream().filter(q -> q.getType().regradable()).map(Question::getId).toList();
        if (candidates.isEmpty()) {
            return Set.of();
        }
        Set<Long> required = new HashSet<>(answers.findQuestionIdsWithAnswers(candidates));
        required.removeAll(runs.findQuestionsWithActiveRun(candidates));
        return required;
    }
}
