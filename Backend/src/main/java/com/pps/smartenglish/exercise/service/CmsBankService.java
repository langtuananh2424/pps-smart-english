package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.catalog.service.CatalogReferences;
import com.pps.smartenglish.exercise.domain.QuestionBank;
import com.pps.smartenglish.exercise.dto.CmsQuestionDtos.BankDetail;
import com.pps.smartenglish.exercise.dto.CmsQuestionDtos.BankView;
import com.pps.smartenglish.exercise.dto.CmsQuestionDtos.CreateBank;
import com.pps.smartenglish.exercise.dto.CmsQuestionDtos.GrammarElementView;
import com.pps.smartenglish.exercise.dto.CmsQuestionDtos.PatchBank;
import com.pps.smartenglish.exercise.repository.GrammarElementRepository;
import com.pps.smartenglish.exercise.repository.QuestionBankRepository;
import com.pps.smartenglish.exercise.repository.QuestionRepository;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.persistence.CmsWrites;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CMS cho bộ câu hỏi (FR-ADM-02). Chạy cho ADMIN và EDITOR. */
@Service
public class CmsBankService {

    private static final String CONFLICT = "conflict";

    private final QuestionBankRepository banks;
    private final QuestionRepository questions;
    private final GrammarElementRepository grammar;
    private final CatalogReferences catalog;
    private final QuestionDetails details;
    private final CmsWrites writes;

    CmsBankService(QuestionBankRepository banks, QuestionRepository questions, GrammarElementRepository grammar,
                   CatalogReferences catalog, QuestionDetails details, CmsWrites writes) {
        this.banks = banks;
        this.questions = questions;
        this.grammar = grammar;
        this.catalog = catalog;
        this.details = details;
        this.writes = writes;
    }

    @Transactional(readOnly = true)
    public List<BankView> list(long topicId) {
        requireTopic(topicId);
        List<QuestionBank> rows = banks.findByTopicIdOrderByIdAsc(topicId);
        return views(rows);
    }

    @Transactional
    public BankView create(long topicId, CreateBank req) {
        requireTopic(topicId);
        requireLessonInTopic(req.lessonId(), topicId);
        var bank = new QuestionBank(topicId, req.lessonId(), req.title().strip(), Boolean.TRUE.equals(req.isTrial()));
        return view(writes.save(banks, bank, CONFLICT, "Không lưu được bộ câu hỏi"), 0);
    }

    /** Bộ kèm câu hỏi đầy đủ gồm answerKey, isCorrect và thẻ, theo sortOrder. */
    @Transactional(readOnly = true)
    public BankDetail get(long id) {
        QuestionBank bank = find(id);
        var rows = questions.findByBankIdOrderBySortOrderAscIdAsc(id);
        return new BankDetail(view(bank, rows.size()), details.of(rows));
    }

    @Transactional
    public BankView patch(long id, PatchBank req) {
        QuestionBank bank = find(id);
        if (req.title() != null) {
            bank.setTitle(req.title().strip());
        }
        if (req.lessonId() != null) {
            requireLessonInTopic(req.lessonId(), bank.getTopicId());
            bank.setLessonId(req.lessonId());
        }
        if (req.isTrial() != null) {
            bank.setTrial(req.isTrial());
        }
        return view(writes.save(banks, bank, CONFLICT, "Không lưu được bộ câu hỏi"), questions.countByBankId(id));
    }

    /** Còn câu hỏi, lượt làm bài... tham chiếu thì in_use; không xóa cascade. */
    @Transactional
    public void delete(long id) {
        QuestionBank bank = find(id);
        if (questions.existsByBankId(id)) {
            throw CmsWrites.inUse();
        }
        writes.delete(banks, bank);
    }

    /** Idempotent. Xuất bản cần ít nhất 1 câu; hạ xuống DRAFT thì luôn được. */
    @Transactional
    public BankView setPublished(long id, boolean published) {
        QuestionBank bank = find(id);
        long count = questions.countByBankId(id);
        if (published && count == 0) {
            throw ApiException.validation("questions", "Cần ít nhất 1 câu hỏi trước khi xuất bản");
        }
        bank.setStatus(published ? QuestionBank.PUBLISHED : QuestionBank.DRAFT);
        return view(writes.save(banks, bank, CONFLICT, "Không lưu được bộ câu hỏi"), count);
    }

    @Transactional(readOnly = true)
    public List<GrammarElementView> grammarElements() {
        return grammar.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(e -> new GrammarElementView(e.getId(), e.getCode(), e.getName(), e.getColorHex(), e.getSortOrder())).toList();
    }

    private List<BankView> views(List<QuestionBank> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> counts = questions.countByBankIds(rows.stream().map(QuestionBank::getId).toList()).stream()
                .collect(Collectors.toMap(r -> ((Number) r[0]).longValue(), r -> ((Number) r[1]).longValue()));
        return rows.stream().map(b -> view(b, counts.getOrDefault(b.getId(), 0L))).toList();
    }

    private void requireTopic(long topicId) {
        if (!catalog.topicExists(topicId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "topic_not_found", "Không tìm thấy topic");
        }
    }

    /** lessonId null = không gắn bài. Bài phải thuộc đúng topic của bộ. */
    private void requireLessonInTopic(Long lessonId, long topicId) {
        if (lessonId != null && !catalog.lessonBelongsToTopic(lessonId, topicId)) {
            throw ApiException.validation("lessonId", "Bài học không tồn tại hoặc không thuộc topic này");
        }
    }

    private QuestionBank find(long id) {
        return banks.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "bank_not_found", "Không tìm thấy bộ câu hỏi"));
    }

    private static BankView view(QuestionBank b, long questionCount) {
        return new BankView(b.getId(), b.getTopicId(), b.getLessonId(), b.getTitle(), b.isTrial(), b.getStatus(), questionCount);
    }
}
