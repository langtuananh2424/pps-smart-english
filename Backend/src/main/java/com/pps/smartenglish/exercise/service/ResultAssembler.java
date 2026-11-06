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

    private final GraderRegistry graders;
    private final ObjectMapper mapper;

    ResultAssembler(GraderRegistry graders, ObjectMapper mapper) {
        this.graders = graders;
        this.mapper = mapper;
    }

    /** quotaProducts khác null khi có câu cần AI mà hết lượt. */
    AttemptResponse submitted(StudentAttempt attempt, Instant submittedAt, int score, int maxScore, QuestionSet set,
                              List<StudentAnswer> answers, List<AnswerGrading> gradings, List<Long> pendingJobs,
                              List<ProductView> quotaProducts) {
        Map<Long, StudentAnswer> byQuestion = answers.stream()
                .collect(Collectors.toMap(StudentAnswer::getQuestionId, Function.identity()));
        Map<Long, JsonNode> gradingByAnswer = gradings.stream()
                .collect(Collectors.toMap(AnswerGrading::getAnswerId, AnswerGrading::getResult, (a, b) -> a));
        var results = set.questions().stream().map(q -> result(q, set, byQuestion.get(q.getId()), gradingByAnswer)).toList();
        return new AttemptResponse(attempt.getId(), attempt.getBankId(), attempt.getStartedAt(), submittedAt, score, maxScore,
                results, pendingJobs, quotaProducts == null ? null : Boolean.TRUE, quotaProducts);
    }

    private QuestionResult result(Question q, QuestionSet set, StudentAnswer stored, Map<Long, JsonNode> gradingByAnswer) {
        Boolean correct = stored == null ? null : stored.getCorrect();
        if (REWRITE_TYPES.contains(q.getType())) {
            // câu viết lại: đáp án mẫu chỉ có khi đã chấm xong (luật hoặc AI), kèm nhận xét
            JsonNode g = stored == null ? null : gradingByAnswer.get(stored.getId());
            String corrected = g == null || g.path("correctedAnswer").isNull() ? null : g.path("correctedAnswer").asText(null);
            String feedback = g == null ? null : g.path("feedback").asText(null);
            return new QuestionResult(q.getId(), correct, corrected == null ? null : mapper.createObjectNode().put("text", corrected),
                    q.getExplanation(), feedback);
        }
        boolean auto = graders.isAutoGraded(q.getType());
        return new QuestionResult(q.getId(), correct, auto ? graders.correctAnswer(q.getType(), set.inputOf(q)) : null,
                q.getExplanation(), null);
    }
}
