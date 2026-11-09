package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.exercise.domain.Question;
import com.pps.smartenglish.exercise.domain.RegradeRun;
import com.pps.smartenglish.exercise.dto.RegradeDtos.Preview;
import com.pps.smartenglish.exercise.dto.RegradeDtos.RunView;
import com.pps.smartenglish.exercise.dto.RegradeDtos.Started;
import com.pps.smartenglish.exercise.dto.RegradeItemView;
import com.pps.smartenglish.exercise.repository.QuestionRepository;
import com.pps.smartenglish.exercise.repository.RegradeItemRepository;
import com.pps.smartenglish.exercise.repository.RegradeRunRepository;
import com.pps.smartenglish.exercise.repository.StudentAnswerRepository;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.web.CursorPage;
import com.pps.smartenglish.shared.web.Cursors;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API chấm lại bài cũ cho ADMIN (FR-ADM-02): xem trước, xác nhận, xem run và lịch sử điểm. Xác nhận chỉ tạo run và item; việc
 * chấm do RegradeRunner làm nền.
 */
@Service
public class RegradeService {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    private final QuestionRepository questions;
    private final StudentAnswerRepository answers;
    private final RegradeRunRepository runs;
    private final RegradeItemRepository items;
    private final RegradeRequirement requirement;
    private final RegradeRunCreator creator;

    RegradeService(QuestionRepository questions, StudentAnswerRepository answers, RegradeRunRepository runs,
                   RegradeItemRepository items, RegradeRequirement requirement, RegradeRunCreator creator) {
        this.questions = questions;
        this.answers = answers;
        this.runs = runs;
        this.items = items;
        this.requirement = requirement;
        this.creator = creator;
    }

    /** Vài câu SQL đếm, không tải bài về bộ nhớ. maxAiCalls là cận trên (luật còn quyết được nhiều câu). */
    @Transactional(readOnly = true)
    public Preview preview(long questionId) {
        Question q = regradable(questionId);
        long ai = ResultAssembler.REWRITE_TYPES.contains(q.getType()) ? answers.countWithText(questionId) : 0;
        return new Preview(questionId, q.getAnswerVersion(), answers.countSubmitted(questionId),
                answers.countSubmittedAttempts(questionId), ai, requirement.among(List.of(q)).contains(questionId));
    }

    /**
     * Xác nhận chấm lại đúng phiên bản Admin đã xem. Bấm lại: run đang chạy hoặc đã xong không lỗi thì trả lại run cũ (200); run
     * xong có item lỗi thì chạy lại phần lỗi (202). Không dùng @Transactional ở đây để UNIQUE trùng không làm hỏng transaction.
     */
    public Started start(long adminId, long questionId, int answerVersion) {
        Question q = regradable(questionId);
        if (answerVersion != q.getAnswerVersion()) {
            throw new ApiException(HttpStatus.CONFLICT, "stale_version", "Đáp án đã đổi, hãy xem lại bản xem trước",
                    Map.of("currentVersion", q.getAnswerVersion()), null);
        }
        var existing = runs.findByQuestionIdAndQuestionVersion(questionId, answerVersion);
        if (existing.isPresent()) {
            return existingRun(existing.get());
        }
        if (answers.countSubmitted(questionId) == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "no_submissions", "Câu hỏi chưa có bài nộp nào để chấm lại");
        }
        try {
            return new Started(RunView.of(creator.create(q, adminId), null), true);
        } catch (DataIntegrityViolationException e) {
            // hai Admin bấm cùng lúc: bên thua lấy run của bên thắng
            return existingRun(runs.findByQuestionIdAndQuestionVersion(questionId, answerVersion).orElseThrow());
        }
    }

    @Transactional(readOnly = true)
    public CursorPage<RunView> list(Long questionId, int limit, String cursor) {
        var page = PageRequest.of(0, limit + 1);
        long before = Cursors.decode(cursor);
        List<RegradeRun> rows = questionId == null ? runs.findPage(before, page) : runs.findPageByQuestion(questionId, before, page);
        boolean more = rows.size() > limit;
        List<RegradeRun> data = more ? rows.subList(0, limit) : rows;
        return new CursorPage<>(data.stream().map(r -> RunView.of(r, null)).toList(),
                more ? Cursors.encode(data.get(limit - 1).getId()) : null);
    }

    @Transactional(readOnly = true)
    public RunView get(long runId) {
        RegradeRun run = findRun(runId);
        Map<String, Long> counts = new HashMap<>();
        items.countByStatus(runId).forEach(r -> counts.put((String) r[0], ((Number) r[1]).longValue()));
        return RunView.of(run, counts);
    }

    @Transactional(readOnly = true)
    public CursorPage<RegradeItemView> items(long runId, int limit, String cursor) {
        findRun(runId);
        List<RegradeItemView> rows = items.findPage(runId, Cursors.decode(cursor), PageRequest.of(0, limit + 1));
        boolean more = rows.size() > limit;
        List<RegradeItemView> data = more ? rows.subList(0, limit) : rows;
        return new CursorPage<>(data, more ? Cursors.encode(data.get(limit - 1).id()) : null);
    }

    private Started existingRun(RegradeRun run) {
        if (RegradeRun.DONE.equals(run.getStatus()) && run.getFailed() > 0) {
            items.retryFailed(run.getId());
            runs.reopen(run.getId());
            return new Started(RunView.of(runs.findById(run.getId()).orElseThrow(), null), true);
        }
        return new Started(RunView.of(run, null), false);
    }

    private Question regradable(long questionId) {
        Question q = questions.findById(questionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "question_not_found", "Không tìm thấy câu hỏi"));
        if (!q.getType().regradable()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "not_regradable", "Dạng câu hỏi này không chấm lại được");
        }
        return q;
    }

    private RegradeRun findRun(long runId) {
        return runs.findById(runId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "regrade_not_found", "Không tìm thấy lần chấm lại"));
    }
}
