package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.ai.service.AiJobService;
import com.pps.smartenglish.exercise.domain.AnswerGrading;
import com.pps.smartenglish.exercise.domain.StudentAnswer;
import com.pps.smartenglish.exercise.domain.StudentAttempt;
import com.pps.smartenglish.exercise.dto.AttemptResponse;
import com.pps.smartenglish.exercise.dto.AttemptStarted;
import com.pps.smartenglish.exercise.dto.AttemptSummary;
import com.pps.smartenglish.exercise.repository.AnswerGradingRepository;
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
    private final Clock clock;

    AttemptService(BankGate gate, StudentAttemptRepository attempts, StudentAnswerRepository answers,
                   AnswerGradingRepository gradings, AiJobService aiJobs, QuestionLoader loader, ResultAssembler assembler,
                   Clock clock) {
        this.gate = gate;
        this.attempts = attempts;
        this.answers = answers;
        this.gradings = gradings;
        this.aiJobs = aiJobs;
        this.loader = loader;
        this.assembler = assembler;
        this.clock = clock;
    }

    @Transactional
    public AttemptStarted start(Long bankId, long userId) {
        gate.require(bankId, userId);
        var a = attempts.save(new StudentAttempt(userId, bankId, Instant.now(clock)));
        return new AttemptStarted(a.getId(), a.getBankId(), a.getStartedAt());
    }

    /** Chưa nộp: không có đáp án. Đã nộp: kết quả từ đáp án đã lưu (không chấm lại). */
    @Transactional(readOnly = true)
    public AttemptResponse get(long userId, Long attemptId) {
        var a = attempts.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "attempt_not_found", "Không tìm thấy lượt làm bài"));
        if (a.getSubmittedAt() == null) {
            return new AttemptResponse(a.getId(), a.getBankId(), a.getStartedAt(), null, null, null, null, null, null, null);
        }
        var stored = answers.findByAttemptId(a.getId());
        var gradingRows = stored.isEmpty() ? List.<AnswerGrading>of()
                : gradings.findByAnswerIdIn(stored.stream().map(StudentAnswer::getId).toList());
        return assembler.submitted(a, a.getSubmittedAt(), a.getScore(), a.getMaxScore(), loader.load(a.getBankId()), stored,
                gradingRows, aiJobs.pendingIds(a.getId()), null);
    }

    /** Mới nhất trước; con trỏ là id của phần tử cuối trang, mã hóa mờ. */
    @Transactional(readOnly = true)
    public CursorPage<AttemptSummary> mine(long userId, Integer limit, String cursor) {
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1) {
            throw ApiException.validation("limit", "Phải từ 1 trở lên");
        }
        size = Math.min(size, MAX_LIMIT);
        long before = Cursors.decode(cursor);
        List<StudentAttempt> rows = attempts.findPage(userId, before, PageRequest.of(0, size + 1));
        boolean more = rows.size() > size;
        List<StudentAttempt> page = more ? rows.subList(0, size) : rows;
        var data = page.stream().map(a -> new AttemptSummary(a.getId(), a.getBankId(), a.getStartedAt(),
                a.getSubmittedAt(), a.getScore(), a.getMaxScore())).toList();
        return new CursorPage<>(data, more ? Cursors.encode(page.get(size - 1).getId()) : null);
    }
}
