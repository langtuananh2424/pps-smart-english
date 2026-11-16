package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.exercise.domain.BankFaq;
import com.pps.smartenglish.exercise.dto.CamDtos.FaqInput;
import com.pps.smartenglish.exercise.dto.CamDtos.FaqView;
import com.pps.smartenglish.exercise.repository.BankFaqRepository;
import com.pps.smartenglish.exercise.repository.QuestionBankRepository;
import com.pps.smartenglish.exercise.validation.PlainText;
import com.pps.smartenglish.shared.audit.AuditRecorder;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.persistence.CmsWrites;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-AI-07 (D-28): CMS hỏi đáp soạn sẵn theo bộ câu hỏi. */
@Service
public class CmsFaqService {

    static final int MAX_PER_BANK = 30;
    private static final int QUESTION_MAX = 300;
    private static final int ANSWER_MAX = 5000;

    private final BankFaqRepository faqs;
    private final QuestionBankRepository banks;
    private final CmsWrites writes;
    private final AuditRecorder audit;

    CmsFaqService(BankFaqRepository faqs, QuestionBankRepository banks, CmsWrites writes, AuditRecorder audit) {
        this.faqs = faqs;
        this.banks = banks;
        this.writes = writes;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<FaqView> list(long bankId) {
        requireBank(bankId);
        return faqs.findByBankIdOrderBySortOrderAscIdAsc(bankId).stream().map(CmsFaqService::view).toList();
    }

    @Transactional
    public FaqView create(long bankId, FaqInput req) {
        if (banks.lockById(bankId).isEmpty()) { // khóa dòng cha: đếm + chèn không vượt giới hạn khi đồng thời
            throw new ApiException(HttpStatus.NOT_FOUND, "bank_not_found", "Không tìm thấy bộ câu hỏi");
        }
        String question = PlainText.required("question", req.question(), QUESTION_MAX);
        String answer = PlainText.required("answer", req.answer(), ANSWER_MAX);
        long count = faqs.countByBankId(bankId);
        if (count >= MAX_PER_BANK) {
            throw ApiException.validation("faqs", "Tối đa " + MAX_PER_BANK + " câu hỏi đáp mỗi bộ câu hỏi");
        }
        var saved = writes.save(faqs, new BankFaq(bankId, req.sortOrder() == null ? (int) count : req.sortOrder(), question, answer),
                "conflict", "Không lưu được câu hỏi đáp");
        audit.record("CREATE", "BANK_FAQ", saved.getId(), null, view(saved));
        return view(saved);
    }

    @Transactional
    public FaqView patch(long id, FaqInput req) {
        BankFaq f = find(id);
        var before = view(f);
        if (req.question() != null) {
            f.setQuestion(PlainText.required("question", req.question(), QUESTION_MAX));
        }
        if (req.answer() != null) {
            f.setAnswer(PlainText.required("answer", req.answer(), ANSWER_MAX));
        }
        if (req.sortOrder() != null) {
            f.setSortOrder(req.sortOrder());
        }
        var after = view(writes.save(faqs, f, "conflict", "Không lưu được câu hỏi đáp"));
        audit.record("UPDATE", "BANK_FAQ", id, before, after);
        return after;
    }

    @Transactional
    public void delete(long id) {
        BankFaq f = find(id);
        audit.record("DELETE", "BANK_FAQ", id, view(f), null);
        writes.delete(faqs, f);
    }

    private BankFaq find(long id) {
        return faqs.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "faq_not_found", "Không tìm thấy câu hỏi đáp"));
    }

    private void requireBank(long bankId) {
        if (!banks.existsById(bankId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "bank_not_found", "Không tìm thấy bộ câu hỏi");
        }
    }

    private static FaqView view(BankFaq f) {
        return new FaqView(f.getId(), f.getBankId(), f.getSortOrder(), f.getQuestion(), f.getAnswer());
    }
}
