package com.pps.smartenglish.exercise.listening;

import com.pps.smartenglish.exercise.content.RichText;
import com.pps.smartenglish.exercise.domain.Question;
import com.pps.smartenglish.exercise.domain.QuestionGroup;
import com.pps.smartenglish.exercise.listening.ListeningDtos.ScriptView;
import com.pps.smartenglish.exercise.listening.ListeningStatsRepository.WrongCount;
import com.pps.smartenglish.exercise.repository.QuestionGroupRepository;
import com.pps.smartenglish.exercise.repository.QuestionRepository;
import com.pps.smartenglish.exercise.repository.StudentAttemptRepository;
import com.pps.smartenglish.shared.error.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-EX-10, D-136: học viên chọn một câu sai nhiều để xem script. Script chỉ ra từ đây, không nằm trong đề hay kết quả thường.
 * Điều kiện kiểm ở máy chủ: lượt Nghe ép đã nộp, đã đủ N lượt, và câu nằm trong danh sách câu sai nhiều của lượt đó.
 */
@Service
public class ScriptService {

    private final StudentAttemptRepository attempts;
    private final QuestionRepository questions;
    private final QuestionGroupRepository groups;
    private final ForcedRules rules;
    private final ForcedResultService results;
    private final ListeningStatsRepository stats;
    private final RichText rich;
    private final Clock clock;

    ScriptService(StudentAttemptRepository attempts, QuestionRepository questions, QuestionGroupRepository groups, ForcedRules rules,
                  ForcedResultService results, ListeningStatsRepository stats, RichText rich, Clock clock) {
        this.attempts = attempts;
        this.questions = questions;
        this.groups = groups;
        this.rules = rules;
        this.results = results;
        this.stats = stats;
        this.rich = rich;
        this.clock = clock;
    }

    /** Không @Transactional bao ngoài: lần ghi script_views thua cuộc đua (unique) không được làm hỏng transaction của lần đọc. */
    public ScriptView view(long userId, long attemptId, long questionId) {
        var a = attempts.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "attempt_not_found", "Không tìm thấy lượt làm bài"));
        if (a.getSubmittedAt() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "attempt_not_submitted", "Nộp bài xong mới xem được script");
        }
        if (!a.isCountOnly()) {
            throw new ApiException(HttpStatus.CONFLICT, "script_not_available", "Bài này không ở chế độ Nghe ép");
        }
        Question q = questions.findById(questionId).filter(x -> a.getBankId().equals(x.getBankId()) && x.getArchivedAt() == null)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "question_not_found", "Không tìm thấy câu hỏi"));
        var rule = rules.forAttempt(a);
        List<WrongCount> hard = results.hardQuestions(a, rule).orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "script_locked",
                "Chỉ xem được script sau mỗi " + rule.hintEveryN() + " lượt làm"));
        if (hard.stream().noneMatch(w -> w.questionId() == questionId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "script_not_hard",
                    "Chỉ xem được script của câu sai từ " + rule.wrongThreshold() + " lần trở lên");
        }
        ScriptView out = scriptOf(q);
        try {
            stats.recordScriptView(userId, questionId, attemptId, Instant.now(clock));
        } catch (DataIntegrityViolationException e) {
            // hai lần xem đồng thời: đã có dòng của lượt này, không cần ghi lại
        }
        return out;
    }

    /** Script riêng của câu trước, không có thì script của nhóm (đã dựng HTML). */
    private ScriptView scriptOf(Question q) {
        if (q.getTranscriptExcerpt() != null && !q.getTranscriptExcerpt().isBlank()) {
            return new ScriptView(q.getId(), q.getTranscriptExcerpt(), null);
        }
        QuestionGroup g = q.getGroupId() == null ? null : groups.findById(q.getGroupId()).orElse(null);
        if (g == null || g.getTranscriptHtml() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "script_not_found", "Câu này chưa có script");
        }
        return new ScriptView(q.getId(), null, rich.render(List.of(g.getTranscriptHtml())).get(g.getTranscriptHtml()));
    }
}
