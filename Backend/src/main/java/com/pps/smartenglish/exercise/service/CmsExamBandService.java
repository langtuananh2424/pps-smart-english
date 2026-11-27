package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.exercise.domain.ExamBandRule;
import com.pps.smartenglish.exercise.domain.QuestionBank;
import com.pps.smartenglish.exercise.domain.Question;
import com.pps.smartenglish.exercise.grading.GraderRegistry;
import com.pps.smartenglish.exercise.repository.QuestionRepository;
import com.pps.smartenglish.exercise.dto.ExamBandDtos.RuleInput;
import com.pps.smartenglish.exercise.dto.ExamBandDtos.RuleView;
import com.pps.smartenglish.exercise.repository.ExamBandRuleRepository;
import com.pps.smartenglish.exercise.repository.QuestionBankRepository;
import com.pps.smartenglish.shared.audit.AuditRecorder;
import com.pps.smartenglish.shared.error.ApiException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-ADM-06, FR-EXAM-03, D-12: bảng quy đổi % điểm kỹ năng -> band (D-167) của một đề thi khóa. Chạy cho quyền cms.content.
 * Đề đầu vào có bảng riêng (CmsPlacementService), nên chỉ nhận bộ EXAM không phải đầu vào.
 */
@Service
public class CmsExamBandService {

    private final QuestionBankRepository banks;
    private final ExamBandRuleRepository rules;
    private final AuditRecorder audit;
    private final QuestionRepository questions;
    private final GraderRegistry graders;

    CmsExamBandService(QuestionBankRepository banks, ExamBandRuleRepository rules, AuditRecorder audit,
                       QuestionRepository questions, GraderRegistry graders) {
        this.questions = questions;
        this.graders = graders;
        this.banks = banks;
        this.rules = rules;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<RuleView> rules(long bankId) {
        requireExamBank(bankId);
        return views(bankId);
    }

    /** Thay toàn bộ bảng của đề; mỗi (kỹ năng, minPercent) một dòng. */
    @Transactional
    public List<RuleView> replace(long bankId, List<RuleInput> input) {
        requireExamBank(bankId);
        Set<String> seen = new HashSet<>();
        for (RuleInput r : input) {
            if (!seen.add(r.skill() + ":" + r.minPercent())) {
                throw ApiException.validation("rules", "Trùng (kỹ năng, % tối thiểu): " + r.skill() + " " + r.minPercent());
            }
        }
        if (!input.isEmpty()) {
            requireSkills(bankId);
        }
        var before = views(bankId);
        rules.deleteByBank(bankId);
        rules.saveAll(input.stream().map(r -> new ExamBandRule(bankId, r.skill(), r.minPercent(), r.band().setScale(1, java.math.RoundingMode.HALF_UP))).toList());
        rules.flush();
        var after = views(bankId);
        audit.record("UPDATE", "EXAM_BAND_RULES", bankId, before, after);
        return after;
    }

    /** D-164: bộ thi khóa có bảng band thì mọi câu tự chấm còn hiệu lực phải có skill, nếu không xuất bản bị từ chối. */
    public void requireSkillsIfBanded(QuestionBank bank) {
        if (QuestionBank.EXAM.equals(bank.getMode()) && !bank.isPlacement() && !rules.findByBankIdOrderBySkillAscMinPercentAsc(bank.getId()).isEmpty()) {
            requireSkills(bank.getId());
        }
    }

    private void requireSkills(long bankId) {
        List<Long> missing = questions.findActiveByBankId(bankId).stream()
                .filter(q -> q.getSkill() == null && graders.isAutoGraded(q.getType())).map(Question::getId).toList();
        if (!missing.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation_failed", "Dữ liệu không hợp lệ",
                    Map.of("skill", "Còn câu tự chấm chưa có kỹ năng, cần chọn kỹ năng trước khi dùng bảng band", "questionIds", missing), null);
        }
    }

    private List<RuleView> views(long bankId) {
        return rules.findByBankIdOrderBySkillAscMinPercentAsc(bankId).stream()
                .map(r -> new RuleView(r.getSkill(), r.getMinPercent(), r.getBand())).toList();
    }

    private void requireExamBank(long bankId) {
        banks.findById(bankId).filter(b -> QuestionBank.EXAM.equals(b.getMode()) && !b.isPlacement())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "bank_not_found", "Không tìm thấy đề thi khóa"));
    }
}
