package com.pps.smartenglish.exercise.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pps.smartenglish.ai.domain.AiJobKind;
import com.pps.smartenglish.ai.domain.AiTask;
import com.pps.smartenglish.ai.service.AddonCatalog;
import com.pps.smartenglish.ai.service.AiJobService;
import com.pps.smartenglish.ai.service.AttemptAiScores;
import com.pps.smartenglish.commerce.dto.ProductView;
import com.pps.smartenglish.exercise.config.LearningStatsProperties;
import com.pps.smartenglish.exercise.domain.AnswerGrading;
import com.pps.smartenglish.exercise.domain.Question;
import com.pps.smartenglish.exercise.domain.StudentAnswer;
import com.pps.smartenglish.exercise.domain.StudentAttempt;
import com.pps.smartenglish.exercise.dto.AttemptResponse;
import com.pps.smartenglish.exercise.dto.SubmitRequest;
import com.pps.smartenglish.exercise.dto.SubmitRequest.AnswerInput;
import com.pps.smartenglish.exercise.grading.Credit;
import com.pps.smartenglish.exercise.grading.GraderRegistry;
import com.pps.smartenglish.exercise.repository.AnswerGradingRepository;
import com.pps.smartenglish.exercise.repository.QuestionBankRepository;
import com.pps.smartenglish.exercise.repository.StudentAnswerRepository;
import com.pps.smartenglish.exercise.repository.StudentAttemptRepository;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.event.ExerciseScored;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * job AI trừ lượt theo trọng số REWRITE_SET; hết lượt thì lần nộp vẫn thành công, câu cần AI giữ isCorrect null.
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
    private final AttemptAiScores aiScores;
    private final AddonCatalog addons;
    private final QuestionBankRepository banks;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final LearningStatsProperties props;
    private final AttemptCheckService checkService;
    private final AssignedBanks assigned;
    private final VideoQuizService videoQuiz;

    SubmissionService(StudentAttemptRepository attempts, StudentAnswerRepository answers, AnswerGradingRepository gradings,
                      QuestionLoader loader, GraderRegistry graders, RewriteRules rewriteRules, RewriteJobInput rewriteInput,
                      ResultAssembler assembler, AiJobService aiJobs, AttemptAiScores aiScores, AddonCatalog addons, QuestionBankRepository banks, ObjectMapper mapper, Clock clock,
                      ApplicationEventPublisher events, LearningStatsProperties props, AttemptCheckService checkService,
                      AssignedBanks assigned, VideoQuizService videoQuiz) {
        this.videoQuiz = videoQuiz;
        this.assigned = assigned;
        this.props = props;
        this.checkService = checkService;
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
        this.aiScores = aiScores;
        this.addons = addons;
        this.banks = banks;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** Hết giờ, hoặc req null (job tự nộp): chấm theo đáp án đã lưu, bỏ qua đáp án gửi lên. */
    @Transactional
    public AttemptResponse submit(long userId, Long attemptId, SubmitRequest req) {
        var attempt = attempts.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "attempt_not_found", "Không tìm thấy lượt làm bài"));
        if (attempt.getSubmittedAt() != null) {
            throw alreadySubmitted();
        }
        if (req != null) {
            assigned.requireActive(attempt.getAssignmentId()); // D-153; req null = hệ thống nộp khi hết giờ, vẫn phải chốt lượt
        }
        Instant now = Instant.now(clock);
        if (req == null || attempt.expired(now)) {
            req = draftRequest(attempt);
        }
        var set = loader.load(attempt);
        req = checkService.pinLocked(attemptId, req, set.questions()); // D-150: câu đã khóa dùng câu trả lời của lần kiểm tra cuối
        req = videoQuiz.pinAnswers(attemptId, req, set.questions()); // D-159: câu video chỉ lấy đáp án quiz đã chốt
        Map<Long, JsonNode> given = indexAnswers(req.answers(), set.questions());
        Map<Long, Integer> times = validTimes(req.answers());

        BigDecimal score = BigDecimal.ZERO; // tổng credit; làm tròn 2 chữ số một lần ở cuối
        int maxScore = 0;
        List<StudentAnswer> rows = new ArrayList<>();
        Map<Long, RewriteRules.Result> ruleResults = new HashMap<>(); // theo questionId, chỉ câu viết lại đã quyết bằng luật
        Map<Long, Question> needsAi = new LinkedHashMap<>();
        List<Question> aiQuestions = new ArrayList<>(); // WRITING/SPEAKING
        for (Question q : set.questions()) {
            JsonNode answer = given.get(q.getId());
            Boolean correct;
            BigDecimal credit = null; // null = chưa chấm (dạng AI)
            if (ResultAssembler.REWRITE_TYPES.contains(q.getType())) {
                var r = rewriteRules.check(q, answer);
                correct = switch (r.verdict()) {
                    case WRONG -> false;
                    case CORRECT -> true;
                    case NEEDS_AI -> null;
                };
                credit = correct == null ? null : Credit.of(correct);
                if (correct == null) {
                    needsAi.put(q.getId(), q);
                } else {
                    ruleResults.put(q.getId(), r);
                }
                maxScore++; // câu viết lại tính vào maxScore ngay, điểm cộng sau khi AI chấm: score không bao giờ lệch maxScore
            } else if (ResultAssembler.AI_SESSION_TYPES.contains(q.getType())) {
                maxScore++; // D-145: câu Viết/Nói luôn tính vào maxScore; điểm lấy từ phiên gắn lượt, sau khi khóa lượt
                aiQuestions.add(q);
                continue;
            } else {
                credit = graders.credit(q.getType(), set.inputOf(q), answer);
                correct = credit == null ? null : Credit.isFull(credit);
                maxScore += credit == null ? 0 : 1;
            }
            score = score.add(credit == null ? BigDecimal.ZERO : credit);
            rows.add(new StudentAnswer(attemptId, q.getId(), answer, correct, credit, times.get(q.getId()), q.getAnswerVersion()));
        }

        score = score.setScale(2, RoundingMode.HALF_UP);
        // Cổng chặn nộp đồng thời: UPDATE theo submitted_at IS NULL; thua cuộc thì rollback, không ghi student_answers
        if (attempts.claimSubmission(attemptId, now, score, maxScore) == 0) {
            throw alreadySubmitted();
        }
        BigDecimal sessionScore = addSessionAnswers(attemptId, aiQuestions, given, times, rows);
        answers.saveAll(rows);
        if (sessionScore.signum() > 0) {
            score = score.add(sessionScore).setScale(2, RoundingMode.HALF_UP);
            attempts.recomputeScore(attemptId); // ghi điểm gồm câu Viết/Nói đã có điểm
        }
        List<AnswerGrading> ruleRows = saveRuleGradings(rows, ruleResults);

        // 429/503 ném từ đây làm rollback cả lần nộp; chỉ hết lượt là không làm hỏng lần nộp
        List<Long> pending = new ArrayList<>();
        var quotaProducts = enqueueRewrite(userId, attemptId, banks.findCourseId(attempt.getBankId()).orElse(null), needsAi, given, pending,
                attempt.getAssignmentId() != null);
        if (!aiQuestions.isEmpty()) { // job chấm Viết/Nói của các phiên gắn lượt cũng đang chờ: web cần biết để hỏi lại
            aiJobs.pendingIds(attemptId).stream().filter(id -> !pending.contains(id)).forEach(pending::add);
        }
        // retention nghe sau commit: mọi lần nộp tính là một ngày có học, điểm dùng để xét thưởng
        events.publishEvent(new ExerciseScored(userId, attempt.getBankId(), attemptId, score, maxScore, true, attempt.isExtra()));
        return assembler.submitted(attempt, now, score, maxScore, set, rows, ruleRows, pending, quotaProducts);
    }

    /**
     * D-145: câu Viết/Nói lấy credit từ phiên mới nhất gắn lượt. Gọi sau khi claimSubmission đã khóa dòng lượt: kết quả AI
     * về trước lúc này thì đọc thấy, về sau thì AiCreditApplier ghi vào câu trả lời (credit còn null). Không có phiên nào
     * thì bỏ trống: 0 điểm và không chờ. D-147: phiên chưa nộp chấm, hết lượt AI, lỗi hay không rõ cũng tính 0 ngay (kèm lý do
     * nếu đã nộp chấm mà không chấm được); chỉ phiên có job đang chạy mới để credit null và chờ, kéo theo passed null.
     * Trả tổng credit đã có.
     */
    private BigDecimal addSessionAnswers(Long attemptId, List<Question> aiQuestions, Map<Long, JsonNode> given,
                                         Map<Long, Integer> times, List<StudentAnswer> rows) {
        if (aiQuestions.isEmpty()) {
            return BigDecimal.ZERO;
        }
        var linked = aiScores.latestByQuestion(attemptId);
        BigDecimal sum = BigDecimal.ZERO;
        for (Question q : aiQuestions) {
            var l = linked.get(q.getId());
            BigDecimal credit = l == null ? Credit.ZERO : l.credit();
            rows.add(new StudentAnswer(attemptId, q.getId(), given.get(q.getId()), null, credit, times.get(q.getId()), q.getAnswerVersion(),
                    l == null ? null : l.failReason(), l != null && l.refunded()));
            sum = sum.add(credit == null ? BigDecimal.ZERO : credit);
        }
        return sum;
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
    private List<ProductView> enqueueRewrite(long userId, Long attemptId, Long courseId, Map<Long, Question> needsAi, Map<Long, JsonNode> given,
                                             List<Long> pending,
                                             boolean assigned) {
        if (needsAi.isEmpty()) {
            return null;
        }
        var input = rewriteInput.build(attemptId, needsAi, given);
        if (assigned) { // D-130: bài giáo viên giao không trừ lượt AI, vẫn qua giới hạn tần suất và hàng đợi
            pending.add(aiJobs.enqueueUncharged(userId, AiJobKind.REWRITE_GRADE, input, attemptId));
            return null;
        }
        var job = aiJobs.enqueueCharged(userId, AiJobKind.REWRITE_GRADE, input, null, attemptId, AiTask.REWRITE_SET, courseId);
        job.ifPresent(pending::add);
        return job.isPresent() ? null : addons.addons();
    }

    /** Đáp án đã lưu thành yêu cầu nộp; chưa lưu gì thì không có câu nào (mọi câu bỏ trống). */
    private SubmitRequest draftRequest(StudentAttempt attempt) {
        var draft = attempt.getDraftAnswers();
        if (draft == null) {
            return new SubmitRequest(List.of());
        }
        return new SubmitRequest(mapper.convertValue(draft, new TypeReference<List<AnswerInput>>() {}));
    }

    /** FR-EX-08: client báo, không tin được; ngoài 0..maxTimeSpentMs thì bỏ (không có trong map = lưu null). */
    private Map<Long, Integer> validTimes(List<AnswerInput> inputs) {
        Map<Long, Integer> out = new HashMap<>();
        for (AnswerInput in : inputs) {
            Long ms = in.timeSpentMs();
            if (ms != null && ms >= 0 && ms <= props.maxTimeSpentMs()) {
                out.put(in.questionId(), ms.intValue());
            }
        }
        return out;
    }

    /** questionId phải thuộc bank và không lặp; answer null/JSON null = bỏ trống (lưu SQL NULL). */
    public static Map<Long, JsonNode> indexAnswers(List<AnswerInput> inputs, List<Question> questions) {
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
