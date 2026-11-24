package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.exercise.domain.StudentAnswer;
import com.pps.smartenglish.exercise.domain.StudentAttempt;
import com.pps.smartenglish.exercise.dto.AttemptResponse;
import com.pps.smartenglish.exercise.listening.ForcedResultService;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Chọn ResultShaper theo result_mode của lượt. */
@Component
class ResultShapers {

    private final Map<String, ResultShaper> byMode;

    ResultShapers(List<ResultShaper> shapers) {
        this.byMode = shapers.stream().collect(Collectors.toMap(ResultShaper::mode, Function.identity()));
    }

    AttemptResponse shape(AttemptResponse full, StudentAttempt attempt, QuestionSet set, List<StudentAnswer> answers) {
        var shaper = attempt.isCountOnly() ? byMode.get(StudentAttempt.COUNT_ONLY) : byMode.get(StudentAttempt.FULL);
        return shaper.shape(full, attempt, set, answers);
    }

    @Component
    static class Full implements ResultShaper {
        public String mode() { return StudentAttempt.FULL; }

        public AttemptResponse shape(AttemptResponse full, StudentAttempt attempt, QuestionSet set, List<StudentAnswer> answers) {
            return full;
        }
    }

    /**
     * D-136, D-142: sau khi nộp chỉ biết số câu đúng, không biết đáp án và không có điểm. Bỏ score, maxScore, results (đúng/sai, đáp án, giải thích từng câu) và breakdown
     * (đếm theo dạng/kỹ năng suy ra được từng câu), thay bằng bản tóm tắt.
     */
    @Component
    static class CountOnly implements ResultShaper {

        private final ForcedResultService forced;

        CountOnly(ForcedResultService forced) {
            this.forced = forced;
        }

        public String mode() { return StudentAttempt.COUNT_ONLY; }

        public AttemptResponse shape(AttemptResponse full, StudentAttempt attempt, QuestionSet set, List<StudentAnswer> answers) {
            return new AttemptResponse(full.id(), full.bankId(), full.startedAt(), full.submittedAt(), null, null,
                    null, full.pendingJobs(), full.aiQuotaRequired(), full.products(), null,
                    forced.summary(attempt, set.questions(), answers));
        }
    }
}
