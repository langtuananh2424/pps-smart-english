package com.pps.smartenglish.placement.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pps.smartenglish.exercise.domain.Question;
import com.pps.smartenglish.exercise.dto.SubmitRequest;
import com.pps.smartenglish.exercise.dto.SubmitRequest.AnswerInput;
import com.pps.smartenglish.exercise.service.QuestionLoader;
import com.pps.smartenglish.exercise.service.SubmissionService;
import com.pps.smartenglish.placement.domain.PlacementAttempt;
import com.pps.smartenglish.placement.domain.PlacementConfig;
import com.pps.smartenglish.placement.dto.PlacementDtos.Progress;
import com.pps.smartenglish.placement.dto.PlacementDtos.Result;
import com.pps.smartenglish.placement.dto.PlacementDtos.Submitted;
import com.pps.smartenglish.placement.repository.PlacementAttemptRepository;
import com.pps.smartenglish.placement.repository.PlacementConfigRepository;
import com.pps.smartenglish.shared.error.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lưu nháp, nộp, tự nộp và claim (FR-TEST-01/02). Mọi thao tác khóa dòng lượt nên nộp hai lần hay nộp đồng thời với job
 * chỉ một bên có hiệu lực. Quá deadline + ân hạn thì bỏ đáp án gửi lên, chấm theo nháp (giờ theo máy chủ).
 */
@Service
public class PlacementSubmitService {

    /** Ân hạn cho độ trễ mạng của lần nộp cuối. */
    public static final Duration GRACE = Duration.ofSeconds(30);

    private final PlacementAttemptRepository attempts;
    private final PlacementConfigRepository config;
    private final PlacementAccess access;
    private final QuestionLoader loader;
    private final PlacementScorer scorer;
    private final PlacementResultService results;
    private final ObjectMapper mapper;
    private final Clock clock;

    PlacementSubmitService(PlacementAttemptRepository attempts, PlacementConfigRepository config, PlacementAccess access,
                           QuestionLoader loader, PlacementScorer scorer, PlacementResultService results, ObjectMapper mapper,
                           Clock clock) {
        this.attempts = attempts;
        this.config = config;
        this.access = access;
        this.loader = loader;
        this.scorer = scorer;
        this.results = results;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public Progress saveDraft(long id, Long userId, String token, SubmitRequest req) {
        var a = access.lock(id, userId, token);
        requireOpen(a);
        Instant now = Instant.now(clock);
        if (pastGrace(a, now)) {
            throw new ApiException(HttpStatus.CONFLICT, "attempt_time_up", "Đã hết giờ làm bài");
        }
        SubmissionService.indexAnswers(req.answers(), loader.load(a.getBankId()).questions());
        a.saveDraft(mapper.valueToTree(req.answers()));
        attempts.save(a);
        return new Progress(remainingSec(a, now));
    }

    /** req null (không gửi gì) = nộp theo nháp. Khách chỉ nhận {submitted, requiresLogin}: band chỉ có sau khi claim. */
    @Transactional
    public Submitted submit(long id, Long userId, String token, SubmitRequest req) {
        var a = access.lock(id, userId, token);
        requireOpen(a);
        Instant now = Instant.now(clock);
        finish(a, now, req == null || pastGrace(a, now) ? null : req);
        return a.getUserId() == null ? new Submitted(true, true, null) : new Submitted(true, false, results.of(a));
    }

    /** Job tự nộp: bỏ qua nếu lượt đã được nộp ở nơi khác. */
    @Transactional
    public void autoSubmit(long id) {
        var a = attempts.lockById(id).orElse(null);
        if (a != null && !a.submitted()) {
            finish(a, Instant.now(clock), null);
        }
    }

    /**
     * Gắn lượt đã nộp của khách vào học viên đang đăng nhập rồi trả kết quả. Vượt số lượt thì 409 và không gắn.
     * Gọi lại với chính học viên đó là idempotent.
     */
    @Transactional
    public Result claim(long id, long userId, String token) {
        attempts.lockUser(userId);
        var a = attempts.lockById(id).orElseThrow(PlacementAccess::notFound);
        if (a.getUserId() != null) {
            if (!a.getUserId().equals(userId)) {
                throw PlacementAccess.notFound();
            }
            return results.of(requireSubmitted(a));
        }
        PlacementAccess.requireToken(a, token);
        requireSubmitted(a);
        long used = attempts.countByUserIdAndSubmittedAtIsNotNull(userId);
        int allowed = config.findById(PlacementConfig.ID).orElseThrow().allowedAttempts();
        if (used >= allowed) {
            throw new ApiException(HttpStatus.CONFLICT, "placement_retakes_exhausted", "Đã hết số lần làm kiểm tra đầu vào",
                    Map.of("used", used, "allowed", allowed), null);
        }
        a.claim(userId, Instant.now(clock));
        attempts.save(a);
        return results.of(a);
    }

    /** req null = chấm theo nháp (bỏ câu không còn trong đề). */
    private void finish(PlacementAttempt a, Instant now, SubmitRequest req) {
        var set = loader.load(a.getBankId());
        List<AnswerInput> inputs = req != null ? req.answers() : draftOf(a, set.questions().stream().map(Question::getId).collect(Collectors.toSet()));
        var scored = scorer.score(set, SubmissionService.indexAnswers(inputs, set.questions()));
        a.submit(now, mapper.valueToTree(inputs), mapper.valueToTree(scored.skills()), scored.overall());
        attempts.save(a);
    }

    private List<AnswerInput> draftOf(PlacementAttempt a, Set<Long> validIds) {
        JsonNode draft = a.getDraftAnswers();
        if (draft == null) {
            return List.of();
        }
        return mapper.convertValue(draft, new TypeReference<List<AnswerInput>>() {}).stream()
                .filter(i -> validIds.contains(i.questionId())).toList();
    }

    private static PlacementAttempt requireSubmitted(PlacementAttempt a) {
        if (!a.submitted()) {
            throw new ApiException(HttpStatus.CONFLICT, "attempt_not_submitted", "Lượt làm bài chưa được nộp");
        }
        return a;
    }

    private static void requireOpen(PlacementAttempt a) {
        if (a.submitted()) {
            throw new ApiException(HttpStatus.CONFLICT, "attempt_already_submitted", "Lượt làm bài này đã được nộp");
        }
    }

    static boolean pastGrace(PlacementAttempt a, Instant now) {
        return now.isAfter(a.getDeadlineAt().plus(GRACE));
    }

    static long remainingSec(PlacementAttempt a, Instant now) {
        return Math.max(0, Duration.between(now, a.getDeadlineAt()).getSeconds());
    }
}
