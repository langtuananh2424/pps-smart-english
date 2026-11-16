package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.exercise.domain.QuestionHint;
import com.pps.smartenglish.exercise.dto.CamDtos.HintInput;
import com.pps.smartenglish.exercise.dto.CamDtos.HintView;
import com.pps.smartenglish.exercise.repository.QuestionHintRepository;
import com.pps.smartenglish.exercise.repository.QuestionRepository;
import com.pps.smartenglish.exercise.validation.PlainText;
import com.pps.smartenglish.shared.audit.AuditRecorder;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.persistence.CmsWrites;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-AI-06 (D-28): CMS gợi ý soạn sẵn theo câu. */
@Service
public class CmsHintService {

    static final int MAX_PER_QUESTION = 10;
    private static final int CONTENT_MAX = 500;
    private static final int DETAIL_MAX = 5000;

    private final QuestionHintRepository hints;
    private final QuestionRepository questions;
    private final CmsWrites writes;
    private final AuditRecorder audit;

    CmsHintService(QuestionHintRepository hints, QuestionRepository questions, CmsWrites writes, AuditRecorder audit) {
        this.hints = hints;
        this.questions = questions;
        this.writes = writes;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<HintView> list(long questionId) {
        requireQuestion(questionId);
        return hints.findByQuestionIdOrderBySortOrderAscIdAsc(questionId).stream().map(CmsHintService::view).toList();
    }

    @Transactional
    public HintView create(long questionId, HintInput req) {
        if (questions.lockById(questionId).isEmpty()) { // khóa dòng cha: đếm + chèn không vượt giới hạn khi đồng thời
            throw new ApiException(HttpStatus.NOT_FOUND, "question_not_found", "Không tìm thấy câu hỏi");
        }
        String content = PlainText.required("content", req.content(), CONTENT_MAX);
        String detail = blankToNull(PlainText.clean("detail", req.detail(), DETAIL_MAX));
        long count = hints.countByQuestionId(questionId);
        if (count >= MAX_PER_QUESTION) {
            throw ApiException.validation("hints", "Tối đa " + MAX_PER_QUESTION + " gợi ý mỗi câu hỏi");
        }
        var saved = writes.save(hints, new QuestionHint(questionId, req.sortOrder() == null ? (int) count : req.sortOrder(),
                content, detail), "conflict", "Không lưu được gợi ý");
        audit.record("CREATE", "QUESTION_HINT", saved.getId(), null, view(saved));
        return view(saved);
    }

    @Transactional
    public HintView patch(long id, HintInput req) {
        QuestionHint h = find(id);
        var before = view(h);
        if (req.content() != null) {
            h.setContent(PlainText.required("content", req.content(), CONTENT_MAX));
        }
        if (req.detail() != null) {
            h.setDetail(blankToNull(PlainText.clean("detail", req.detail(), DETAIL_MAX)));
        }
        if (req.sortOrder() != null) {
            h.setSortOrder(req.sortOrder());
        }
        var after = view(writes.save(hints, h, "conflict", "Không lưu được gợi ý"));
        audit.record("UPDATE", "QUESTION_HINT", id, before, after);
        return after;
    }

    @Transactional
    public void delete(long id) {
        QuestionHint h = find(id);
        audit.record("DELETE", "QUESTION_HINT", id, view(h), null);
        writes.delete(hints, h);
    }

    private QuestionHint find(long id) {
        return hints.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "hint_not_found", "Không tìm thấy gợi ý"));
    }

    private void requireQuestion(long questionId) {
        if (!questions.existsById(questionId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "question_not_found", "Không tìm thấy câu hỏi");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    private static HintView view(QuestionHint h) {
        return new HintView(h.getId(), h.getQuestionId(), h.getSortOrder(), h.getContent(), h.getDetail());
    }
}
