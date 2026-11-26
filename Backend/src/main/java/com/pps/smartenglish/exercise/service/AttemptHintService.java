package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.exercise.domain.QuestionBank;
import com.pps.smartenglish.exercise.domain.QuestionHint;
import com.pps.smartenglish.exercise.dto.BankResponse.HintView;
import com.pps.smartenglish.exercise.repository.QuestionHintRepository;
import com.pps.smartenglish.exercise.repository.StudentAttemptRepository;
import com.pps.smartenglish.shared.error.ApiException;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-AI-06, D-151: gợi ý soạn sẵn của bộ IELTS chỉ đi theo lượt Luyện tập đang mở; lượt Mô phỏng thi không lấy được. */
@Service
public class AttemptHintService {

    public record QuestionHints(Long questionId, List<HintView> hints) {}

    private final StudentAttemptRepository attempts;
    private final AttemptModes modes;
    private final QuestionLoader loader;
    private final QuestionHintRepository hints;

    AttemptHintService(StudentAttemptRepository attempts, AttemptModes modes, QuestionLoader loader, QuestionHintRepository hints) {
        this.attempts = attempts;
        this.modes = modes;
        this.loader = loader;
        this.hints = hints;
    }

    @Transactional(readOnly = true)
    public List<QuestionHints> of(long userId, long attemptId) {
        var a = attempts.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "attempt_not_found", "Không tìm thấy lượt làm bài"));
        if (a.getSubmittedAt() != null || !QuestionBank.PRACTICE.equals(modes.of(a))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "hints_unavailable", "Gợi ý chỉ có khi đang luyện tập");
        }
        var ids = loader.load(a.getBankId()).questions().stream().map(q -> q.getId()).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return hints.findByQuestionIdInOrderBySortOrderAscIdAsc(ids).stream()
                .collect(Collectors.groupingBy(QuestionHint::getQuestionId, java.util.LinkedHashMap::new,
                        Collectors.mapping(h -> new HintView(h.getId(), h.getContent(), h.getDetail()), Collectors.toList())))
                .entrySet().stream().map(e -> new QuestionHints(e.getKey(), e.getValue())).toList();
    }
}
