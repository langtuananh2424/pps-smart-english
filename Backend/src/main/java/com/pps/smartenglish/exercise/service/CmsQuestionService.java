package com.pps.smartenglish.exercise.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.GrammarElement;
import com.pps.smartenglish.exercise.domain.Question;
import com.pps.smartenglish.exercise.domain.QuestionChoice;
import com.pps.smartenglish.exercise.domain.QuestionTag;
import com.pps.smartenglish.exercise.domain.QuestionType;
import com.pps.smartenglish.exercise.dto.CmsQuestionDtos.ChoiceInput;
import com.pps.smartenglish.exercise.dto.CmsQuestionDtos.CreateQuestion;
import com.pps.smartenglish.exercise.dto.CmsQuestionDtos.PatchQuestion;
import com.pps.smartenglish.exercise.dto.CmsQuestionDtos.QuestionDetail;
import com.pps.smartenglish.exercise.repository.GrammarElementRepository;
import com.pps.smartenglish.exercise.repository.QuestionBankRepository;
import com.pps.smartenglish.exercise.repository.QuestionChoiceRepository;
import com.pps.smartenglish.exercise.repository.QuestionRepository;
import com.pps.smartenglish.exercise.repository.QuestionTagRepository;
import com.pps.smartenglish.exercise.repository.StudentAnswerRepository;
import com.pps.smartenglish.exercise.validation.QuestionDraft;
import com.pps.smartenglish.exercise.validation.QuestionDraft.ChoiceDraft;
import com.pps.smartenglish.exercise.validation.QuestionValidators;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.persistence.CmsWrites;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CMS cho câu hỏi (FR-ADM-02). Mọi thay đổi nội dung đi qua validator của đúng dạng. Đáp án đổi thật sự thì answer_version
 * tăng; bài cũ được chấm lại khi Admin xác nhận (RegradeService), không tự động.
 */
@Service
public class CmsQuestionService {

    private final QuestionRepository questions;
    private final QuestionBankRepository banks;
    private final QuestionChoiceRepository choices;
    private final QuestionTagRepository tags;
    private final GrammarElementRepository grammar;
    private final StudentAnswerRepository answers;
    private final QuestionValidators validators;
    private final QuestionDetails details;
    private final CmsWrites writes;

    CmsQuestionService(QuestionRepository questions, QuestionBankRepository banks, QuestionChoiceRepository choices,
                       QuestionTagRepository tags, GrammarElementRepository grammar, StudentAnswerRepository answers,
                       QuestionValidators validators, QuestionDetails details, CmsWrites writes) {
        this.questions = questions;
        this.banks = banks;
        this.choices = choices;
        this.tags = tags;
        this.grammar = grammar;
        this.answers = answers;
        this.validators = validators;
        this.details = details;
        this.writes = writes;
    }

    @Transactional
    public QuestionDetail create(long bankId, CreateQuestion req) {
        if (!banks.existsById(bankId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "bank_not_found", "Không tìm thấy bộ câu hỏi");
        }
        QuestionType type = parseType(req.type());
        var draft = new QuestionDraft(QuestionValidators.normalize(req.payload()), QuestionValidators.normalize(req.answerKey()),
                drafts(req.choices()));
        Map<String, GrammarElement> elements = elements();
        validators.validate(type, draft, elements.keySet());
        Set<Long> tagIds = tagIds(req.grammarElementCodes(), elements);

        var question = writes.save(questions, new Question(bankId, type, req.prompt().strip(), draft.payload(), draft.answerKey(),
                req.explanation(), req.sortOrder() == null ? 0 : req.sortOrder()), "conflict", "Không lưu được câu hỏi");
        saveChoices(question.getId(), draft.choices());
        saveTags(question.getId(), tagIds);
        return one(question);
    }

    /**
     * Chỉ kiểm tra lại nội dung khi nội dung thật sự đổi; gửi lại đúng dữ liệu cũ không tính là đổi (dữ liệu cũ không bị ép
     * theo luật mới). Câu đã có bài nộp vẫn sửa được đáp án (rồi Admin chấm lại), nhưng không đổi dạng và không bỏ lựa chọn.
     */
    @Transactional
    public QuestionDetail patch(long id, PatchQuestion req) {
        Question q = find(id);
        List<QuestionChoice> oldChoices = choices.findByQuestionIdInOrderByLabelAscIdAsc(List.of(id));
        boolean touchesContent = req.type() != null || req.payload() != null || req.answerKey() != null || req.choices() != null;
        if (touchesContent) {
            QuestionType type = req.type() != null ? parseType(req.type()) : q.getType();
            JsonNode payload = QuestionValidators.normalize(req.payload() != null ? req.payload() : q.getPayload());
            JsonNode key = QuestionValidators.normalize(req.answerKey() != null ? req.answerKey() : q.getAnswerKey());
            List<ChoiceDraft> newChoices = req.choices() != null ? drafts(req.choices()) : draftsOf(oldChoices);

            boolean contentChanged = !Objects.equals(payload, QuestionValidators.normalize(q.getPayload()))
                    || !Objects.equals(key, QuestionValidators.normalize(q.getAnswerKey())) || !sameChoices(newChoices, oldChoices);
            if (type != q.getType() || contentChanged) {
                requireUnlocked(id, q, type, newChoices, oldChoices);
                validators.validate(type, new QuestionDraft(payload, key, newChoices), elements().keySet());
                q.setType(type);
                q.setPayload(payload);
                q.setAnswerKey(key);
                syncChoices(id, newChoices, oldChoices);
                if (contentChanged && type != QuestionType.WRITING) {
                    q.bumpAnswerVersion(); // WRITING sửa đề tự do: không chấm lại nên không cần phiên bản
                }
            }
        }
        if (req.prompt() != null) {
            q.setPrompt(req.prompt().strip());
        }
        if (req.explanation() != null) {
            q.setExplanation(req.explanation());
        }
        if (req.sortOrder() != null) {
            q.setSortOrder(req.sortOrder());
        }
        if (req.grammarElementCodes() != null) {
            replaceTags(id, tagIds(req.grammarElementCodes(), elements()));
        }
        return one(writes.save(questions, q, "conflict", "Không lưu được câu hỏi"));
    }

    /** Xóa luôn lựa chọn và thẻ của câu; còn bài nộp hoặc phiên viết tham chiếu thì in_use và rollback toàn bộ. */
    @Transactional
    public void delete(long id) {
        Question q = find(id);
        choices.deleteByQuestionId(id);
        tags.deleteByQuestionId(id);
        choices.flush();
        writes.delete(questions, q);
    }

    private QuestionDetail one(Question q) {
        return details.of(List.of(q)).get(0);
    }

    private Question find(long id) {
        return questions.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "question_not_found", "Không tìm thấy câu hỏi"));
    }

    private static QuestionType parseType(String raw) {
        try {
            return QuestionType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw ApiException.validation("type", "Dạng câu hỏi không hợp lệ");
        }
    }

    private Map<String, GrammarElement> elements() {
        return grammar.findAll().stream().collect(Collectors.toMap(GrammarElement::getCode, e -> e));
    }

    /** Mã không tồn tại là 400. */
    private static Set<Long> tagIds(List<String> codes, Map<String, GrammarElement> elements) {
        Set<Long> ids = new HashSet<>();
        for (String code : codes == null ? List.<String>of() : codes) {
            GrammarElement e = elements.get(code);
            if (e == null) {
                throw ApiException.validation("grammarElementCodes", "Mã yếu tố ngữ pháp không tồn tại: " + code);
            }
            ids.add(e.getId());
        }
        return ids;
    }

    private void replaceTags(long questionId, Set<Long> wanted) {
        Set<Long> current = tags.findByQuestionIdIn(List.of(questionId)).stream().map(QuestionTag::getGrammarElementId)
                .collect(Collectors.toSet());
        if (!current.equals(wanted)) {
            tags.deleteByQuestionId(questionId);
            tags.flush();
            saveTags(questionId, wanted);
        }
    }

    private void saveTags(long questionId, Set<Long> ids) {
        tags.saveAll(ids.stream().map(g -> new QuestionTag(questionId, g)).toList());
    }

    /** Đã có bài nộp: không đổi dạng; câu trắc nghiệm không được bỏ nhãn đang có (student_answers lưu choiceId). */
    private void requireUnlocked(long id, Question q, QuestionType newType, List<ChoiceDraft> wanted, List<QuestionChoice> old) {
        if (!answers.existsByQuestionId(id)) {
            return;
        }
        if (newType != q.getType()) {
            throw new ApiException(HttpStatus.CONFLICT, "has_submissions", "Câu hỏi đã có bài nộp: không đổi được dạng câu hỏi");
        }
        Set<String> kept = wanted.stream().map(c -> c.label().strip()).collect(Collectors.toSet());
        if (old.stream().anyMatch(c -> !kept.contains(c.getLabel()))) {
            throw new ApiException(HttpStatus.CONFLICT, "has_submissions", "Câu hỏi đã có bài nộp: không được bỏ lựa chọn đang có");
        }
    }

    /** Cập nhật tại chỗ theo nhãn để id lựa chọn không đổi; nhãn mới thì thêm, nhãn vắng mặt thì xóa. */
    private void syncChoices(long questionId, List<ChoiceDraft> wanted, List<QuestionChoice> old) {
        Map<String, QuestionChoice> byLabel = old.stream().collect(Collectors.toMap(QuestionChoice::getLabel, c -> c, (a, b) -> a));
        Set<String> kept = new HashSet<>();
        List<QuestionChoice> added = new java.util.ArrayList<>();
        for (ChoiceDraft d : wanted) {
            String label = d.label().strip();
            kept.add(label);
            QuestionChoice existing = byLabel.get(label);
            if (existing == null) {
                added.add(new QuestionChoice(questionId, label, d.content(), d.correct(), d.explanation()));
            } else {
                existing.update(d.content(), d.correct(), d.explanation());
            }
        }
        choices.deleteAll(old.stream().filter(c -> !kept.contains(c.getLabel())).toList());
        choices.saveAll(added);
    }

    private void saveChoices(long questionId, List<ChoiceDraft> drafts) {
        choices.saveAll(drafts.stream()
                .map(c -> new QuestionChoice(questionId, c.label().strip(), c.content(), c.correct(), c.explanation())).toList());
    }

    private static List<ChoiceDraft> drafts(List<ChoiceInput> in) {
        return (in == null ? List.<ChoiceInput>of() : in).stream()
                .map(c -> new ChoiceDraft(c.label(), c.content(), Boolean.TRUE.equals(c.isCorrect()), c.explanation())).toList();
    }

    private static List<ChoiceDraft> draftsOf(List<QuestionChoice> rows) {
        return rows.stream().map(c -> new ChoiceDraft(c.getLabel(), c.getContent(), c.isCorrect(), c.getExplanation())).toList();
    }

    /** So sánh không phụ thuộc thứ tự gửi lên. */
    private static boolean sameChoices(List<ChoiceDraft> wanted, List<QuestionChoice> current) {
        Comparator<ChoiceDraft> byLabel = Comparator.comparing(c -> c.label().strip());
        return wanted.stream().map(c -> new ChoiceDraft(c.label().strip(), c.content(), c.correct(), c.explanation())).sorted(byLabel)
                .toList().equals(draftsOf(current).stream().sorted(byLabel).toList());
    }
}
