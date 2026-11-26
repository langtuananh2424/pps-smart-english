package com.pps.smartenglish.exercise.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pps.smartenglish.commerce.dto.ProductView;
import com.pps.smartenglish.exercise.domain.AnswerGrading;
import com.pps.smartenglish.exercise.domain.Question;
import com.pps.smartenglish.exercise.domain.QuestionType;
import com.pps.smartenglish.exercise.domain.StudentAnswer;
import com.pps.smartenglish.exercise.domain.StudentAttempt;
import com.pps.smartenglish.exercise.dto.AttemptResponse;
import com.pps.smartenglish.exercise.dto.AttemptResponse.QuestionResult;
import com.pps.smartenglish.exercise.grading.GraderRegistry;
import com.pps.smartenglish.exercise.repository.QuestionBankRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Dựng kết quả sau nộp từ đáp án đã lưu: chỉ nơi này mới đưa answer_key (dạng đáp án đúng) ra ngoài. */
@Component
class ResultAssembler {

    static final Set<QuestionType> REWRITE_TYPES = Set.of(QuestionType.CONNECTIVE_REWRITE, QuestionType.GRAMMAR_REWRITE);

    /** D-145: điểm từ phiên Viết/Nói gắn lượt, về sau lúc nộp. */
    static final Set<QuestionType> AI_SESSION_TYPES = Set.of(QuestionType.WRITING, QuestionType.SPEAKING);

    private final GraderRegistry graders;
    private final ObjectMapper mapper;
    private final ResultShapers shapers;

    private final QuestionBankRepository banks;

    ResultAssembler(GraderRegistry graders, ObjectMapper mapper, ResultShapers shapers, QuestionBankRepository banks) {
        this.banks = banks;
        this.shapers = shapers;
        this.graders = graders;
        this.mapper = mapper;
    }

    /** quotaProducts khác null khi có câu cần AI mà hết lượt. */
    AttemptResponse submitted(StudentAttempt attempt, Instant submittedAt, BigDecimal score, int maxScore, QuestionSet set,
                              List<StudentAnswer> answers, List<AnswerGrading> gradings, List<Long> pendingJobs,
                              List<ProductView> quotaProducts) {
        Map<Long, StudentAnswer> byQuestion = answers.stream()
                .collect(Collectors.toMap(StudentAnswer::getQuestionId, Function.identity()));
        Map<Long, JsonNode> gradingByAnswer = gradings.stream()
                .collect(Collectors.toMap(AnswerGrading::getAnswerId, AnswerGrading::getResult, (a, b) -> a));
        var results = set.questions().stream().map(q -> result(q, set, byQuestion.get(q.getId()), gradingByAnswer)).toList();
        // D-145: một chỗ chung cho nộp bài và GET; đề đầu vào không có đạt/chưa, Nghe ép bị shaper bỏ trường này
        var bank = banks.findById(attempt.getBankId()).filter(b -> !b.isPlacement()).orElse(null);
        Integer passPct = bank == null ? null : bank.getPassPct();
        boolean awaiting = awaitsAi(answers, set);
        var full = new AttemptResponse(attempt.getId(), attempt.getBankId(), attempt.getStartedAt(), submittedAt, score, maxScore,
                results, pendingJobs, quotaProducts == null ? null : Boolean.TRUE, quotaProducts, null, null,
                passed(passPct, score, maxScore, awaiting), passPct, null);
        var shaped = shapers.shape(full, attempt, set, answers); // Nghe ép: ẩn đáp án theo result_mode
        if (awaiting || !pendingJobs.isEmpty()) {
            // D-147: còn câu AI chưa chốt thì không có điểm tạm, đáp án hay đạt/chưa; chỉ trạng thái đang chấm
            return new AttemptResponse(shaped.id(), shaped.bankId(), shaped.startedAt(), shaped.submittedAt(), null, shaped.maxScore(),
                    null, pendingJobs, shaped.aiQuotaRequired(), shaped.products(), null, shaped.forced(), null, shaped.passPct(), null);
        }
        return shaped;
    }

    /**
     * Còn câu cần AI mà chưa có điểm: đang chạy, hết lượt hay job lỗi. Viết lại chờ is_correct; Viết/Nói chờ credit
     * (câu bỏ trống đã có credit 0 nên không chờ).
     */
    private static boolean awaitsAi(List<StudentAnswer> answers, QuestionSet set) {
        Map<Long, Question> byId = set.questions().stream().collect(Collectors.toMap(Question::getId, Function.identity()));
        return answers.stream().anyMatch(a -> {
            var type = byId.get(a.getQuestionId()).getType();
            return REWRITE_TYPES.contains(type) ? a.getCorrect() == null : AI_SESSION_TYPES.contains(type) && a.getCredit() == null;
        });
    }

    /** Đạt khi score/maxScore >= ngưỡng, so sánh không làm tròn; null khi chưa chốt điểm (còn câu AI) hoặc maxScore = 0. */
    static Boolean passed(Integer passPct, BigDecimal score, int maxScore, boolean awaitsAi) {
        if (passPct == null || maxScore <= 0 || score == null || awaitsAi) {
            return null;
        }
        return score.multiply(BigDecimal.valueOf(100)).compareTo(BigDecimal.valueOf((long) passPct * maxScore)) >= 0;
    }

    private QuestionResult result(Question q, QuestionSet set, StudentAnswer stored, Map<Long, JsonNode> gradingByAnswer) {
        Boolean correct = stored == null ? null : stored.getCorrect();
        if (REWRITE_TYPES.contains(q.getType())) {
            // câu viết lại: đáp án mẫu chỉ có khi đã chấm xong (luật hoặc AI), kèm nhận xét
            JsonNode g = stored == null ? null : gradingByAnswer.get(stored.getId());
            String corrected = g == null || g.path("correctedAnswer").isNull() ? null : g.path("correctedAnswer").asText(null);
            String feedback = g == null ? null : g.path("feedback").asText(null);
            return new QuestionResult(q.getId(), correct, corrected == null ? null : mapper.createObjectNode().put("text", corrected),
                    q.getExplanation(), feedback, null, null);
        }
        boolean auto = graders.isAutoGraded(q.getType());
        return new QuestionResult(q.getId(), correct, auto ? graders.correctAnswer(q.getType(), set.inputOf(q)) : null,
                q.getExplanation(), null, stored == null ? null : stored.getAiFailReason(),
                stored == null || stored.getAiFailReason() == null ? null : stored.isAiFailRefunded());
    }
}
