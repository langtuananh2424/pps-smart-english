package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.exercise.dto.AttemptResponse;
import com.pps.smartenglish.exercise.dto.AttemptResponse.QuestionResult;
import com.pps.smartenglish.exercise.domain.StudentAnswer;
import com.pps.smartenglish.exercise.domain.StudentAttempt;
import com.pps.smartenglish.exercise.grading.GraderRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Dựng kết quả sau nộp từ đáp án đã lưu: chỉ nơi này mới đưa answer_key (dạng đáp án đúng) ra ngoài. */
@Component
class ResultAssembler {

    private final GraderRegistry graders;

    ResultAssembler(GraderRegistry graders) {
        this.graders = graders;
    }

    AttemptResponse submitted(StudentAttempt attempt, Instant submittedAt, int score, int maxScore, QuestionSet set,
                              List<StudentAnswer> answers) {
        Map<Long, StudentAnswer> byQuestion = answers.stream()
                .collect(Collectors.toMap(StudentAnswer::getQuestionId, Function.identity()));
        var results = set.questions().stream().map(q -> {
            var stored = byQuestion.get(q.getId());
            boolean auto = graders.isAutoGraded(q.getType());
            return new QuestionResult(q.getId(), stored == null ? null : stored.getCorrect(),
                    auto ? graders.correctAnswer(q.getType(), set.inputOf(q)) : null, q.getExplanation());
        }).toList();
        // pendingJobs luôn rỗng cho tới khi có module ai
        return new AttemptResponse(attempt.getId(), attempt.getBankId(), attempt.getStartedAt(), submittedAt, score, maxScore,
                results, List.of());
    }
}
