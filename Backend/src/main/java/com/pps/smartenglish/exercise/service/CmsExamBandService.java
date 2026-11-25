package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.exercise.domain.ExamBandRule;
import com.pps.smartenglish.exercise.domain.QuestionBank;
import com.pps.smartenglish.exercise.dto.ExamBandDtos.RuleInput;
import com.pps.smartenglish.exercise.dto.ExamBandDtos.RuleView;
import com.pps.smartenglish.exercise.repository.ExamBandRuleRepository;
import com.pps.smartenglish.exercise.repository.QuestionBankRepository;
import com.pps.smartenglish.shared.audit.AuditRecorder;
import com.pps.smartenglish.shared.error.ApiException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-ADM-06, FR-EXAM-03, D-12: bảng quy đổi số câu đúng -> band của một đề thi khóa. Chạy cho quyền cms.content.
 * Đề đầu vào có bảng riêng (CmsPlacementService), nên chỉ nhận bộ EXAM không phải đầu vào.
 */
@Service
public class CmsExamBandService {

    private final QuestionBankRepository banks;
    private final ExamBandRuleRepository rules;
    private final AuditRecorder audit;

    CmsExamBandService(QuestionBankRepository banks, ExamBandRuleRepository rules, AuditRecorder audit) {
        this.banks = banks;
        this.rules = rules;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<RuleView> rules(long bankId) {
        requireExamBank(bankId);
        return views(bankId);
    }

    /** Thay toàn bộ bảng của đề; mỗi (kỹ năng, minCorrect) một dòng. */
    @Transactional
    public List<RuleView> replace(long bankId, List<RuleInput> input) {
        requireExamBank(bankId);
        Set<String> seen = new HashSet<>();
        for (RuleInput r : input) {
            if (!seen.add(r.skill() + ":" + r.minCorrect())) {
                throw ApiException.validation("rules", "Trùng (kỹ năng, số câu đúng): " + r.skill() + " " + r.minCorrect());
            }
        }
        var before = views(bankId);
        rules.deleteByBank(bankId);
        rules.saveAll(input.stream().map(r -> new ExamBandRule(bankId, r.skill(), r.minCorrect(), r.band().setScale(1, java.math.RoundingMode.HALF_UP))).toList());
        rules.flush();
        var after = views(bankId);
        audit.record("UPDATE", "EXAM_BAND_RULES", bankId, before, after);
        return after;
    }

    private List<RuleView> views(long bankId) {
        return rules.findByBankIdOrderBySkillAscMinCorrectAsc(bankId).stream()
                .map(r -> new RuleView(r.getSkill(), r.getMinCorrect(), r.getBand())).toList();
    }

    private void requireExamBank(long bankId) {
        banks.findById(bankId).filter(b -> QuestionBank.EXAM.equals(b.getMode()) && !b.isPlacement())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "bank_not_found", "Không tìm thấy đề thi khóa"));
    }
}
