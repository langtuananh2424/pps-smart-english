package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.catalog.service.CatalogReferences;
import com.pps.smartenglish.exercise.domain.QuestionBank;
import com.pps.smartenglish.exercise.listening.ListeningProperties;
import com.pps.smartenglish.exercise.dto.CmsQuestionDtos.BankDetail;
import com.pps.smartenglish.exercise.dto.CmsQuestionDtos.BankView;
import com.pps.smartenglish.exercise.dto.CmsQuestionDtos.CreateBank;
import com.pps.smartenglish.exercise.dto.CmsQuestionDtos.CreatePlacementBank;
import com.pps.smartenglish.exercise.dto.CmsQuestionDtos.GrammarElementView;
import com.pps.smartenglish.exercise.dto.CmsQuestionDtos.PatchBank;
import com.pps.smartenglish.exercise.repository.GrammarElementRepository;
import com.pps.smartenglish.exercise.repository.QuestionBankRepository;
import com.pps.smartenglish.exercise.repository.QuestionRepository;
import com.pps.smartenglish.shared.audit.AuditRecorder;
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
    private final CmsGroupService groups;
    private final AuditRecorder audit;
    private final ListeningProperties listening;

    CmsBankService(QuestionBankRepository banks, QuestionRepository questions, GrammarElementRepository grammar,
                   CatalogReferences catalog, QuestionDetails details, CmsWrites writes, AuditRecorder audit, CmsGroupService groups,
                   ListeningProperties listening) {
        this.listening = listening;
        this.groups = groups;
        this.audit = audit;
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

    /** FR-TEST-01: đề kiểm tra đầu vào, mọi trạng thái; các thao tác còn lại dùng chung /banks/{id}. */
    @Transactional(readOnly = true)
    public List<BankView> listPlacement() {
        return views(banks.findByPurposeOrderByIdAsc(QuestionBank.PLACEMENT));
    }

    @Transactional
    public BankView createPlacement(CreatePlacementBank req) {
        var after = view(writes.save(banks, QuestionBank.placement(req.title().strip(), req.timeLimitSec()), CONFLICT,
                "Không lưu được bộ câu hỏi"), 0);
        audit.record("CREATE", "BANK", after.id(), null, after);
        return after;
    }

    @Transactional
    public BankView create(long topicId, CreateBank req) {
        requireTopic(topicId);
        requireLessonInTopic(req.lessonId(), topicId);
        var bank = new QuestionBank(topicId, req.lessonId(), req.title().strip(), Boolean.TRUE.equals(req.isTrial()));
        bank.setTimeLimitSec(req.timeLimitSec());
        if (req.mode() != null) {
            bank.setMode(req.mode());
        }
        applyPlayback(bank, req.playbackMode(), req.playLimit());
        applyPassPct(bank, req.passPct());
        var after = view(writes.save(banks, bank, CONFLICT, "Không lưu được bộ câu hỏi"), 0);
        audit.record("CREATE", "BANK", after.id(), null, after);
        return after;
    }

    /** Bộ kèm câu hỏi đầy đủ gồm answerKey, isCorrect và thẻ, theo sortOrder. */
    @Transactional(readOnly = true)
    public BankDetail get(long id) {
        QuestionBank bank = find(id);
        var rows = questions.findActiveByBankId(id);
        return new BankDetail(view(bank, rows.size()), details.of(rows), groups.ofBank(id));
    }

    @Transactional
    public BankView patch(long id, PatchBank req) {
        QuestionBank bank = find(id);
        var before = view(bank, questions.countByBankId(id));
        if (req.title() != null) {
            bank.setTitle(req.title().strip());
        }
        if (bank.isPlacement() && (req.lessonId() != null || req.mode() != null)) {
            throw ApiException.validation("mode", "Đề kiểm tra đầu vào không gắn bài học và luôn ở chế độ EXAM");
        }
        if (req.lessonId() != null) {
            requireLessonInTopic(req.lessonId(), bank.getTopicId());
            bank.setLessonId(req.lessonId());
        }
        if (req.isTrial() != null) {
            bank.setTrial(req.isTrial());
        }
        if (req.timeLimitSec() != null) {
            bank.setTimeLimitSec(req.timeLimitSec());
        }
        if (req.mode() != null) {
            bank.setMode(req.mode());
        }
        applyPlayback(bank, req.playbackMode(), req.playLimit());
        applyPassPct(bank, req.passPct());
        var after = view(writes.save(banks, bank, CONFLICT, "Không lưu được bộ câu hỏi"), questions.countByBankId(id));
        audit.record("UPDATE", "BANK", id, before, after);
        return after;
    }

    /** D-145: ngưỡng Đạt; vắng mặt = giữ nguyên (bộ mới giữ mặc định 70). Đề kiểm tra đầu vào không có đạt/chưa. */
    private void applyPassPct(QuestionBank bank, Integer passPct) {
        if (passPct == null) {
            return;
        }
        if (bank.isPlacement()) {
            throw ApiException.validation("passPct", "Đề kiểm tra đầu vào không có ngưỡng đạt");
        }
        bank.setPassPct(passPct);
    }

    /** D-135: chế độ phát audio và số lần nghe của chế độ Thi; đề kiểm tra đầu vào không có audio theo lượt nên chỉ FREE. */
    private void applyPlayback(QuestionBank bank, String playbackMode, Integer playLimit) {
        if (playbackMode != null) {
            if (bank.isPlacement() && !QuestionBank.FREE.equals(playbackMode)) {
                throw ApiException.validation("playbackMode", "Đề kiểm tra đầu vào chỉ dùng chế độ phát FREE");
            }
            bank.setPlaybackMode(playbackMode);
        }
        if (playLimit != null) {
            if (playLimit > listening.maxPlayLimit()) {
                throw ApiException.validation("playLimit", "Tối đa " + listening.maxPlayLimit());
            }
            bank.setPlayLimit(playLimit);
        }
    }

    /** Còn câu hỏi, lượt làm bài... tham chiếu thì in_use; không xóa cascade. */
    @Transactional
    public void delete(long id) {
        QuestionBank bank = find(id);
        if (questions.existsByBankId(id)) {
            throw CmsWrites.inUse();
        }
        audit.record("DELETE", "BANK", id, view(bank, 0), null);
        writes.delete(banks, bank);
    }

    /** Idempotent. Xuất bản cần ít nhất 1 câu; hạ xuống DRAFT thì luôn được. */
    @Transactional
    public BankView setPublished(long id, boolean published) {
        QuestionBank bank = find(id);
        long count = questions.countByBankId(id);
        if (published && bank.isPlacement()) {
            throw ApiException.validation("status", "Đề kiểm tra đầu vào xuất bản qua /placement/banks/{id}/publish");
        }
        if (published && count == 0) {
            throw ApiException.validation("questions", "Cần ít nhất 1 câu hỏi trước khi xuất bản");
        }
        var before = view(bank, count);
        bank.setStatus(published ? QuestionBank.PUBLISHED : QuestionBank.DRAFT);
        var after = view(writes.save(banks, bank, CONFLICT, "Không lưu được bộ câu hỏi"), count);
        audit.record(published ? "PUBLISH" : "UNPUBLISH", "BANK", id, before, after);
        return after;
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
        return banks.findById(id).filter(b -> !b.isExtra()) // D-152: bộ neo Luyện thêm không có trong CMS
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "bank_not_found", "Không tìm thấy bộ câu hỏi"));
    }

    public static BankView view(QuestionBank b, long questionCount) {
        return new BankView(b.getId(), b.getTopicId(), b.getLessonId(), b.getTitle(), b.isTrial(), b.getStatus(), questionCount,
                b.getTimeLimitSec(), b.getMode(), b.getPurpose(), b.getPlaybackMode(), b.getPlayLimit(), b.getPassPct());
    }
}
