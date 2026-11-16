package com.pps.smartenglish.placement.service;

import com.pps.smartenglish.exercise.domain.Question;
import com.pps.smartenglish.exercise.domain.QuestionBank;
import com.pps.smartenglish.exercise.dto.CmsQuestionDtos.BankView;
import com.pps.smartenglish.exercise.repository.QuestionBankRepository;
import com.pps.smartenglish.exercise.repository.QuestionRepository;
import com.pps.smartenglish.exercise.service.CmsBankService;
import com.pps.smartenglish.placement.domain.PlacementBandRule;
import com.pps.smartenglish.placement.domain.PlacementConfig;
import com.pps.smartenglish.placement.dto.PlacementDtos.BandRuleInput;
import com.pps.smartenglish.placement.dto.PlacementDtos.BandRuleView;
import com.pps.smartenglish.placement.dto.PlacementDtos.ConfigView;
import com.pps.smartenglish.placement.repository.PlacementBandRuleRepository;
import com.pps.smartenglish.placement.repository.PlacementConfigRepository;
import com.pps.smartenglish.shared.audit.AuditRecorder;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.persistence.CmsWrites;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CMS cho kiểm tra đầu vào (FR-ADM-06): chọn đề đang hoạt động, số lần làm lại, bảng quy đổi band. Soạn câu hỏi dùng
 * chung CmsQuestionService. Chạy cho quyền cms.content.
 */
@Service
public class CmsPlacementService {

    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private final QuestionBankRepository banks;
    private final QuestionRepository questions;
    private final PlacementConfigRepository config;
    private final PlacementBandRuleRepository rules;
    private final CmsWrites writes;
    private final AuditRecorder audit;

    CmsPlacementService(QuestionBankRepository banks, QuestionRepository questions, PlacementConfigRepository config,
                        PlacementBandRuleRepository rules, CmsWrites writes, AuditRecorder audit) {
        this.banks = banks;
        this.questions = questions;
        this.config = config;
        this.rules = rules;
        this.writes = writes;
        this.audit = audit;
    }

    /**
     * Chỉ một đề PUBLISHED tại một thời điểm: xuất bản đề này sẽ hạ các đề khác xuống DRAFT. Cần có câu hỏi và bảng quy đổi
     * cho mọi kỹ năng có trong đề, nếu không học viên sẽ nhận band 0 oan.
     */
    @Transactional
    public BankView publish(long id) {
        QuestionBank bank = placementBank(id);
        List<Question> qs = questions.findByBankIdOrderBySortOrderAscIdAsc(id);
        if (qs.isEmpty()) {
            throw ApiException.validation("questions", "Cần ít nhất 1 câu hỏi trước khi xuất bản");
        }
        for (String skill : qs.stream().map(Question::getSkill).collect(Collectors.toCollection(TreeSet::new))) {
            if (!rules.existsBySkill(skill)) {
                throw ApiException.validation("bandRules", "Thiếu bảng quy đổi band cho kỹ năng " + skill);
            }
        }
        for (QuestionBank other : banks.findByPurposeAndStatusOrderByIdDesc(QuestionBank.PLACEMENT, QuestionBank.PUBLISHED)) {
            if (!other.getId().equals(id)) {
                var before = CmsBankService.view(other, questions.countByBankId(other.getId()));
                other.setStatus(QuestionBank.DRAFT);
                audit.record("UNPUBLISH", "BANK", other.getId(), before,
                        CmsBankService.view(writes.save(banks, other, "conflict", "Không lưu được bộ câu hỏi"), before.questionCount()));
            }
        }
        return setStatus(bank, QuestionBank.PUBLISHED, "PUBLISH", qs.size());
    }

    @Transactional
    public BankView unpublish(long id) {
        QuestionBank bank = placementBank(id);
        return setStatus(bank, QuestionBank.DRAFT, "UNPUBLISH", questions.countByBankId(id));
    }

    @Transactional(readOnly = true)
    public ConfigView config() {
        return new ConfigView(current().getMaxRetakes());
    }

    @Transactional
    public ConfigView updateConfig(Integer maxRetakes) {
        PlacementConfig row = current();
        var before = new ConfigView(row.getMaxRetakes());
        row.setMaxRetakes(maxRetakes);
        var after = new ConfigView(config.save(row).getMaxRetakes());
        audit.record("UPDATE", "PLACEMENT_CONFIG", PlacementConfig.ID, before, after);
        return after;
    }

    @Transactional(readOnly = true)
    public List<BandRuleView> bandRules() {
        return views(rules.findAllByOrderBySkillAscMinPercentAsc());
    }

    /** Thay toàn bộ bảng: band bước 0.5, mỗi (kỹ năng, minPercent) một dòng. */
    @Transactional
    public List<BandRuleView> replaceBandRules(List<BandRuleInput> input) {
        Set<String> seen = new HashSet<>();
        for (BandRuleInput r : input) {
            if (r.band().multiply(TWO).stripTrailingZeros().scale() > 0) {
                throw ApiException.validation("band", "Band tính theo bước 0.5");
            }
            if (!seen.add(r.skill() + ":" + r.minPercent())) {
                throw ApiException.validation("rules", "Trùng (kỹ năng, minPercent): " + r.skill() + " " + r.minPercent());
            }
        }
        var before = bandRules();
        rules.deleteAll(rules.findAll());
        rules.flush();
        rules.saveAll(input.stream().map(r -> new PlacementBandRule(r.skill(), r.minPercent(), r.band().setScale(1))).toList());
        rules.flush();
        var after = bandRules();
        audit.record("UPDATE", "PLACEMENT_BAND_RULES", PlacementConfig.ID, before, after);
        return after;
    }

    private BankView setStatus(QuestionBank bank, String status, String action, long count) {
        var before = CmsBankService.view(bank, count);
        bank.setStatus(status);
        var after = CmsBankService.view(writes.save(banks, bank, "conflict", "Không lưu được bộ câu hỏi"), count);
        audit.record(action, "BANK", bank.getId(), before, after);
        return after;
    }

    /** Bộ không phải đề đầu vào coi như không tồn tại ở đường này. */
    private QuestionBank placementBank(long id) {
        return banks.findById(id).filter(QuestionBank::isPlacement)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "bank_not_found", "Không tìm thấy đề kiểm tra đầu vào"));
    }

    private PlacementConfig current() {
        return config.findById(PlacementConfig.ID).orElseThrow();
    }

    private static List<BandRuleView> views(List<PlacementBandRule> rows) {
        return rows.stream().map(r -> new BandRuleView(r.getSkill(), r.getMinPercent(), r.getBand())).toList();
    }
}
