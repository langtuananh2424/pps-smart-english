package com.pps.smartenglish.exercise.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pps.smartenglish.ai.domain.AiJobKind;
import com.pps.smartenglish.ai.service.AddonCatalog;
import com.pps.smartenglish.ai.service.AiJobService;
import com.pps.smartenglish.commerce.dto.ProductView;
import com.pps.smartenglish.exercise.domain.AnswerGrading;
import com.pps.smartenglish.exercise.domain.Question;
import com.pps.smartenglish.exercise.domain.StudentAnswer;
import com.pps.smartenglish.exercise.dto.AttemptResponse;
import com.pps.smartenglish.exercise.dto.SubmitRequest;
import com.pps.smartenglish.exercise.dto.SubmitRequest.AnswerInput;
import com.pps.smartenglish.exercise.grading.GraderRegistry;
import com.pps.smartenglish.exercise.repository.AnswerGradingRepository;
import com.pps.smartenglish.exercise.repository.StudentAnswerRepository;
import com.pps.smartenglish.exercise.repository.StudentAttemptRepository;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.event.ExerciseScored;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-EX-03: chấm tại chỗ và nộp đúng một lần. Câu viết lại chấm luật trước (miễn phí), phần còn lại gộp vào đúng một
 * job AI trừ đúng một lượt; hết lượt thì lần nộp vẫn thành công, câu cần AI giữ isCorrect null.
 */
@Service
public class SubmissionService {

    private final StudentAttemptRepository attempts;
    private final StudentAnswerRepository answers;
    private final AnswerGradingRepository gradings;
    private final QuestionLoader loader;
    private final GraderRegistry graders;
    private final RewriteRules rewriteRules;
    private final RewriteJobInput rewriteInput;
    private final ResultAssembler assembler;
    private final AiJobService aiJobs;
    private final AddonCatalog addons;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    SubmissionService(StudentAttemptRepository attempts, StudentAnswerRepository answers, AnswerGradingRepository gradings,
                      QuestionLoader loader, GraderRegistry graders, RewriteRules rewriteRules, RewriteJobInput rewriteInput,
                      ResultAssembler assembler, AiJobService aiJobs, AddonCatalog addons, ObjectMapper mapper, Clock clock,
                      ApplicationEventPublisher events) {
        this.events = events;
        this.rewriteInput = rewriteInput;
        this.attempts = attempts;
        this.answers = answers;
        this.gradings = gradings;
        this.loader = loader;
        this.graders = graders;
        this.rewriteRules = rewriteRules;
        this.assembler = assembler;
        this.aiJobs = aiJobs;
        this.addons = addons;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public AttemptResponse submit(long userId, Long attemptId, SubmitRequest req) {
        var attempt = attempts.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "attempt_not_found", "Không tìm thấy lượt làm bài"));
        if (attempt.getSubmittedAt() != null) {
            throw alreadySubmitted();
        }
        var set = loader.load(attempt.getBankId());
        Map<Long, JsonNode> given = indexAnswers(req.answers(), set.questions());

        int score = 0;
        int maxScore = 0;
        List<StudentAnswer> rows = new ArrayList<>();
        Map<Long, RewriteRules.Result> ruleResults = new HashMap<>(); // theo questionId, chỉ câu viết lại đã quyết bằng luật
        Map<Long, Question> needsAi = new LinkedHashMap<>();
        for (Question q : set.questions()) {
            JsonNode answer = given.get(q.getId());
            Boolean correct;
            if (ResultAssembler.REWRITE_TYPES.contains(q.getType())) {
                var r = rewriteRules.check(q, answer);
                correct = switch (r.verdict()) {
                    case WRONG -> false;
                    case CORRECT -> true;
                    case NEEDS_AI -> null;
                };
                if (correct == null) {
                    needsAi.put(q.getId(), q);
                } else {
                    ruleResults.put(q.getId(), r);
                }
                maxScore++; // câu viết lại tính vào maxScore ngay, điểm cộng sau khi AI chấm: score không bao giờ lệch maxScore
            } else {
                correct = graders.grade(q.getType(), set.inputOf(q), answer);
                maxScore += correct == null ? 0 : 1;
            }
            score += Boolean.TRUE.equals(correct) ? 1 : 0;
            rows.add(new StudentAnswer(attemptId, q.getId(), answer, correct));
        }

        // Cổng chặn nộp đồng thời: UPDATE theo submitted_at IS NULL; thua cuộc thì rollback, không ghi student_answers
        Instant now = Instant.now(clock);
        if (attempts.claimSubmission(attemptId, now, score, maxScore) == 0) {
            throw alreadySubmitted();
        }
        answers.saveAll(rows);
        List<AnswerGrading> ruleRows = saveRuleGradings(rows, ruleResults);

        // 429/503 ném từ đây làm rollback cả lần nộp; chỉ hết lượt là không làm hỏng lần nộp
        List<Long> pending = new ArrayList<>();
        var quotaProducts = enqueueRewrite(userId, attemptId, needsAi, given, pending);
        // retention nghe sau commit: mọi lần nộp tính là một ngày có học, điểm dùng để xét thưởng
        events.publishEvent(new ExerciseScored(userId, attempt.getBankId(), attemptId, score, maxScore, true));
        return assembler.submitted(attempt, now, score, maxScore, set, rows, ruleRows, pending, quotaProducts);
    }

    private List<AnswerGrading> saveRuleGradings(List<StudentAnswer> rows, Map<Long, RewriteRules.Result> ruleResults) {
        List<AnswerGrading> out = new ArrayList<>();
        for (StudentAnswer row : rows) {
            var r = ruleResults.get(row.getQuestionId());
            if (r != null) {
                var result = mapper.createObjectNode().put("feedback", r.feedback()).put("correctedAnswer", r.corrected());
                out.add(new AnswerGrading(row.getId(), AnswerGrading.RULE, Boolean.TRUE.equals(row.getCorrect()) ? 1 : 0, result));
            }
        }
        return gradings.saveAll(out);
    }

    /** Gộp mọi câu cần AI vào một job, một lượt. Trả danh sách gói nạp lượt nếu hết lượt, ngược lại null. */
    private List<ProductView> enqueueRewrite(long userId, Long attemptId, Map<Long, Question> needsAi, Map<Long, JsonNode> given,
                                             List<Long> pending) {
        if (needsAi.isEmpty()) {
            return null;
        }
        var input = rewriteInput.build(attemptId, needsAi, given);
        var job = aiJobs.enqueueCharged(userId, AiJobKind.REWRITE_GRADE, input, null, attemptId);
        job.ifPresent(pending::add);
        return job.isPresent() ? null : addons.addons();
    }

    /** questionId phải thuộc bank và không lặp; answer null/JSON null = bỏ trống (lưu SQL NULL). */
    private static Map<Long, JsonNode> indexAnswers(List<AnswerInput> inputs, List<Question> questions) {
        Set<Long> valid = questions.stream().map(Question::getId).collect(Collectors.toSet());
        Map<Long, JsonNode> out = new HashMap<>();
        for (int i = 0; i < inputs.size(); i++) {
            Long id = inputs.get(i).questionId();
            String field = "answers[" + i + "].questionId";
            if (!valid.contains(id)) {
                throw ApiException.validation(field, "Câu hỏi không thuộc bộ câu hỏi này");
            }
            JsonNode answer = inputs.get(i).answer();
            if (out.containsKey(id)) {
                throw ApiException.validation(field, "Câu hỏi bị lặp");
            }
            out.put(id, answer == null || answer.isNull() ? null : answer);
        }
        return out;
    }

    private static ApiException alreadySubmitted() {
        return new ApiException(HttpStatus.CONFLICT, "attempt_already_submitted", "Lượt làm bài này đã được nộp");
    }
}
