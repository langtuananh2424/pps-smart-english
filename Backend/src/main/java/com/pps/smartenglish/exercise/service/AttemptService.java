package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.ai.service.AiJobService;
import com.pps.smartenglish.exercise.domain.AnswerGrading;
import com.pps.smartenglish.exercise.domain.StudentAnswer;
import com.pps.smartenglish.exercise.domain.StudentAttempt;
import com.pps.smartenglish.exercise.dto.AttemptResponse;
import com.pps.smartenglish.exercise.dto.AttemptStarted;
import com.pps.smartenglish.exercise.dto.AttemptSummary;
import com.pps.smartenglish.exercise.repository.AnswerGradingRepository;
import com.pps.smartenglish.exercise.repository.QuestionBankRepository;
import com.pps.smartenglish.exercise.repository.StudentAnswerRepository;
import com.pps.smartenglish.exercise.repository.StudentAttemptRepository;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.web.CursorPage;
import com.pps.smartenglish.shared.web.Cursors;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttemptService {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;

    private final BankGate gate;
    private final StudentAttemptRepository attempts;
    private final StudentAnswerRepository answers;
    private final AnswerGradingRepository gradings;
    private final AiJobService aiJobs;
    private final QuestionLoader loader;
    private final ResultAssembler assembler;
    private final ExamService exam;
    private final LearningStatsService stats;
    private final Clock clock;
    private final ForcedAttempts forced;
    private final ExamBands bands;
    private final AttemptModes modes;
    private final QuestionBankRepository banks;

    AttemptService(BankGate gate, StudentAttemptRepository attempts, StudentAnswerRepository answers,
                   AnswerGradingRepository gradings, AiJobService aiJobs, QuestionLoader loader, ResultAssembler assembler,
                   ExamService exam, LearningStatsService stats, Clock clock, ForcedAttempts forced, ExamBands bands,
                   AttemptModes modes, QuestionBankRepository banks) {
        this.modes = modes;
        this.banks = banks;
        this.bands = bands;
        this.forced = forced;
        this.stats = stats;
        this.gate = gate;
        this.attempts = attempts;
        this.answers = answers;
        this.gradings = gradings;
        this.aiJobs = aiJobs;
        this.loader = loader;
        this.assembler = assembler;
        this.exam = exam;
        this.clock = clock;
    }

    @Transactional
    public AttemptStarted start(Long bankId, long userId) {
        exam.guardStart(userId, bankId); // trước gate để người chưa mua nhận exam_requires_enrollment thay vì bank_locked
        var bank = gate.require(bankId, userId);
        if (modes.examAssigned(userId, bankId)) { // D-162
            throw new ApiException(HttpStatus.CONFLICT, "bank_assigned_as_exam", "Bộ này đang được giao làm bài thi, hãy làm bài được giao trước");
        }
        requireNoOpenAttempt(userId, bankId);
        var mode = modes.resolve(banks.findById(bankId).orElseThrow(), null); // gate vừa tìm thấy bộ; tự học theo chế độ của bộ
        var a = attempts.save(forced.applySelfStudy(new StudentAttempt(userId, bankId, Instant.now(clock), bank.timeLimitSec()).withMode(mode)));
        return new AttemptStarted(a.getId(), a.getBankId(), a.getStartedAt(), a.getTimeLimitSec(), a.getMode());
    }

    /**
     * D-147: mỗi bộ chỉ một lượt mở cho mỗi học viên (mở thêm lượt để xem trước bản sửa AI của lượt kia). Khóa dòng user để hai
     * start song song không tạo hai lượt; bộ EXAM đã có exam_in_progress ở ExamService.
     */
    void requireNoOpenAttempt(long userId, long bankId) {
        attempts.lockUser(userId);
        attempts.findFirstByUserIdAndBankIdAndSubmittedAtIsNullOrderByIdDesc(userId, bankId).ifPresent(open -> {
            throw new ApiException(HttpStatus.CONFLICT, "attempt_in_progress", "Bạn đang có một lượt làm bài chưa nộp ở bộ này",
                    java.util.Map.of("attemptId", open.getId()), null);
        });
    }

    /**
     * FR-ASG-02: bắt đầu (hoặc làm tiếp) lượt của bài được giao. Người gọi đã kiểm họ là người nhận; ở đây bỏ qua khóa unit tuần tự
     * (BankGate mở riêng bộ này, FR-ASG-01) và các luật thi lại của bài thi thường. Mỗi bài giao chỉ nộp một lần.
     */
    @Transactional
    public AttemptStarted startAssigned(Long bankId, long userId, long assignmentId, boolean forcedAssignment, String assignmentMode) {
        // D-143: bài giao Nghe ép (chốt lúc giao) làm lại không giới hạn; bài giao thường vẫn nộp một lần (FR-ASG-02)
        if (attempts.existsByAssignmentIdAndUserIdAndSubmittedAtIsNotNull(assignmentId, userId) && !forcedAssignment) {
            throw new ApiException(HttpStatus.CONFLICT, "assignment_submitted", "Bạn đã nộp bài được giao này");
        }
        attempts.lockUser(userId); // cùng khóa với start: hai request đồng thời không tạo hai lượt mở
        var open = attempts.findFirstByAssignmentIdAndUserIdAndSubmittedAtIsNullOrderByIdDesc(assignmentId, userId);
        if (open.isPresent()) {
            var a = open.get();
            return new AttemptStarted(a.getId(), a.getBankId(), a.getStartedAt(), a.getTimeLimitSec(), a.getMode());
        }
        var bank = gate.require(bankId, userId);
        requireNoOpenAttempt(userId, bankId); // D-147: còn lượt tự học (hoặc bài giao khác) mở trên bộ này thì 409
        var mode = modes.resolve(banks.findById(bankId).orElseThrow(), assignmentMode); // D-155: bài giao có chế độ thì theo bài giao
        var a = attempts.save(forced.applyAssigned(new StudentAttempt(userId, bankId, Instant.now(clock), bank.timeLimitSec(), assignmentId).withMode(mode),
                assignmentId, forcedAssignment));
        return new AttemptStarted(a.getId(), a.getBankId(), a.getStartedAt(), a.getTimeLimitSec(), a.getMode());
    }

    /** Chưa nộp: không có đáp án. Đã nộp: kết quả từ đáp án đã lưu (không chấm lại). */
    @Transactional(readOnly = true)
    public AttemptResponse get(long userId, Long attemptId) {
        var a = attempts.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "attempt_not_found", "Không tìm thấy lượt làm bài"));
        if (a.getSubmittedAt() == null) {
            return new AttemptResponse(a.getId(), a.getBankId(), a.getStartedAt(), null, null, null, null, null, null, null, null, null, null, null, null);
        }
        var stored = answers.findByAttemptId(a.getId());
        var gradingRows = stored.isEmpty() ? List.<AnswerGrading>of()
                : gradings.findByAnswerIdIn(stored.stream().map(StudentAnswer::getId).toList());
        var base = assembler.submitted(a, a.getSubmittedAt(), a.getScore(), a.getMaxScore(),
                loader.loadForHistory(a, stored.stream().map(StudentAnswer::getQuestionId).toList()), stored,
                gradingRows, aiJobs.pendingIds(a.getId()), null);
        return new AttemptResponse(base.id(), base.bankId(), base.startedAt(), base.submittedAt(), base.score(), base.maxScore(),
                base.results(), base.pendingJobs(), base.aiQuotaRequired(), base.products(),
                a.isCountOnly() || base.score() == null ? null : stats.breakdown(userId, attemptId), base.forced(), base.passed(), base.passPct(),
                a.isCountOnly() || base.score() == null ? null : bands.of(a));
    }

    /** D-147: lượt đã nộp mà còn job AI chưa xong thì điểm hiện có chỉ là điểm tạm, không trả. */
    private boolean hideScore(StudentAttempt a) {
        return a.isCountOnly() || a.getSubmittedAt() != null && !aiJobs.pendingIds(a.getId()).isEmpty();
    }

    /** Mới nhất trước; con trỏ là id của phần tử cuối trang, mã hóa mờ. */
    @Transactional(readOnly = true)
    public CursorPage<AttemptSummary> mine(long userId, Integer limit, String cursor, Long bankId, boolean openOnly) {
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1) {
            throw ApiException.validation("limit", "Phải từ 1 trở lên");
        }
        size = Math.min(size, MAX_LIMIT);
        long before = Cursors.decode(cursor);
        List<StudentAttempt> rows = attempts.findPage(userId, before, bankId, openOnly, PageRequest.of(0, size + 1));
        boolean more = rows.size() > size;
        List<StudentAttempt> page = more ? rows.subList(0, size) : rows;
        var data = page.stream().map(a -> new AttemptSummary(a.getId(), a.getBankId(), a.getStartedAt(),
                a.getSubmittedAt(), hideScore(a) ? null : a.getScore(), a.isCountOnly() ? null : a.getMaxScore())).toList();
        return new CursorPage<>(data, more ? Cursors.encode(page.get(size - 1).getId()) : null);
    }
}
