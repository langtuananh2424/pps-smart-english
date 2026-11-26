package com.pps.smartenglish.exercise.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.pps.smartenglish.exercise.config.RetryProperties;
import com.pps.smartenglish.exercise.domain.Question;
import com.pps.smartenglish.exercise.domain.QuestionBank;
import com.pps.smartenglish.exercise.domain.QuestionCheck;
import com.pps.smartenglish.exercise.domain.StudentAttempt;
import com.pps.smartenglish.exercise.dto.CheckDtos.CheckResult;
import com.pps.smartenglish.exercise.dto.CheckDtos.RetryState;
import com.pps.smartenglish.exercise.dto.SubmitRequest;
import com.pps.smartenglish.exercise.dto.SubmitRequest.AnswerInput;
import com.pps.smartenglish.exercise.grading.Credit;
import com.pps.smartenglish.exercise.grading.GraderRegistry;
import com.pps.smartenglish.exercise.repository.QuestionCheckRepository;
import com.pps.smartenglish.exercise.repository.StudentAttemptRepository;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.ratelimit.RateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-EX-04, D-150: kiểm tra từng câu trong lượt đang mở (thử lại kèm gợi ý). Chỉ bộ PRACTICE và lượt không phải Nghe ép; chỉ dạng tự
 * chấm. Trả đúng/sai và số lần còn lại, không trả đáp án. Điểm vẫn tính khi nộp theo câu trả lời cuối.
 */
@Service
public class AttemptCheckService {

    private final StudentAttemptRepository attempts;
    private final AttemptModes modes;
    private final QuestionCheckRepository checks;
    private final QuestionLoader loader;
    private final GraderRegistry graders;
    private final RateLimiter limiter;
    private final RetryProperties props;
    private final ObjectMapper mapper;
    private final Clock clock;

    AttemptCheckService(StudentAttemptRepository attempts, AttemptModes modes, QuestionCheckRepository checks,
                        QuestionLoader loader, GraderRegistry graders, RateLimiter limiter, RetryProperties props,
                        ObjectMapper mapper, Clock clock) {
        this.attempts = attempts;
        this.modes = modes;
        this.checks = checks;
        this.loader = loader;
        this.graders = graders;
        this.limiter = limiter;
        this.props = props;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public CheckResult check(long userId, long attemptId, long questionId, JsonNode answer) {
        limiter.requireHit("rl:attempt-check:" + userId, props.checksPerMinute(), Duration.ofMinutes(1), "attempt-check:" + userId);
        // Khóa dòng lượt trước khi đọc: hai lần kiểm tra song song không vượt lượt thử, và bản nháp đọc ra là bản mới nhất.
        var a = attempts.lockById(attemptId).filter(x -> x.getUserId() == userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "attempt_not_found", "Không tìm thấy lượt làm bài"));
        if (a.getSubmittedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "attempt_already_submitted", "Lượt làm bài này đã được nộp");
        }
        if (a.expired(Instant.now(clock))) {
            throw new ApiException(HttpStatus.CONFLICT, "attempt_time_up", "Đã hết giờ làm bài");
        }
        if (!enabled(a)) {
            throw new ApiException(HttpStatus.CONFLICT, "retry_not_available", "Bộ này không có thử lại kèm gợi ý");
        }
        var set = loader.load(a.getBankId());
        var q = set.questions().stream().filter(x -> x.getId() == questionId).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "question_not_found", "Câu hỏi không thuộc bộ này"));
        if (!graders.isAutoGraded(q.getType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "question_not_checkable", "Câu này chấm bằng AI, không kiểm tra từng câu");
        }
        var row = checks.findByAttemptIdAndQuestionId(attemptId, questionId).orElseGet(() -> new QuestionCheck(attemptId, questionId));
        if (row.locked(props.maxRetries())) {
            throw new ApiException(HttpStatus.CONFLICT, "retry_exhausted", "Câu này đã hết lượt thử");
        }
        boolean correct = Credit.isFull(graders.credit(q.getType(), set.inputOf(q), answer));
        row.record(correct, answer);
        checks.save(row);
        a.saveDraft(withAnswer(a.getDraftAnswers(), questionId, answer)); // như lưu nháp: tải lại trang không mất câu trả lời
        attempts.save(a);
        return result(row);
    }

    /** Null khi lượt không áp dụng thử lại. */
    @Transactional(readOnly = true)
    public RetryState stateOf(StudentAttempt a) {
        if (!enabled(a)) {
            return null;
        }
        return new RetryState(props.maxRetries(), checks.findByAttemptId(a.getId()).stream().map(this::result).toList());
    }

    /**
     * Câu đã khóa (đúng hoặc hết lượt) giữ câu trả lời của lần kiểm tra cuối: nháp và nộp bài bỏ giá trị khác gửi lên, không báo lỗi để
     * không làm hỏng việc lưu cả bộ. Chỉ câu còn hiệu lực trong bộ (questions) mới được ghim.
     */
    public SubmitRequest pinLocked(long attemptId, SubmitRequest req, List<Question> questions) {
        Set<Long> valid = questions.stream().map(Question::getId).collect(Collectors.toSet());
        Map<Long, QuestionCheck> locked = checks.findByAttemptId(attemptId).stream()
                .filter(c -> c.getAnswer() != null && valid.contains(c.getQuestionId()) && c.locked(props.maxRetries()))
                .collect(Collectors.toMap(QuestionCheck::getQuestionId, Function.identity()));
        if (locked.isEmpty()) {
            return req;
        }
        Map<Long, Long> times = new java.util.HashMap<>();
        List<AnswerInput> out = new ArrayList<>();
        for (AnswerInput in : req.answers()) {
            if (locked.containsKey(in.questionId())) {
                times.put(in.questionId(), in.timeSpentMs());
            } else {
                out.add(in);
            }
        }
        locked.forEach((qid, c) -> out.add(new AnswerInput(qid, c.getAnswer(), times.get(qid))));
        return new SubmitRequest(out);
    }

    /**
     * Điều kiện duy nhất "lượt áp dụng thử lại" (D-150, D-151): theo chế độ của lượt, không theo bộ. Lượt EXAM (bộ EXAM, bài thi khóa,
     * kiểm tra đầu vào, hoặc Mô phỏng thi của bộ IELTS) và lượt Nghe ép (COUNT_ONLY) không có.
     */
    private boolean enabled(StudentAttempt a) {
        return !a.isCountOnly() && QuestionBank.PRACTICE.equals(modes.of(a));
    }

    private CheckResult result(QuestionCheck row) {
        return new CheckResult(row.getQuestionId(), row.isSolved(), row.checksLeft(props.maxRetries()));
    }

    /** Thay (hoặc thêm) phần tử của câu trong nháp, giữ nguyên các câu khác. */
    private ArrayNode withAnswer(JsonNode draft, long questionId, JsonNode answer) {
        ArrayNode out = mapper.createArrayNode();
        if (draft != null && draft.isArray()) {
            draft.forEach(item -> {
                if (item.path("questionId").asLong() != questionId) {
                    out.add(item);
                }
            });
        }
        out.add(mapper.createObjectNode().put("questionId", questionId).set("answer", answer));
        return out;
    }
}
