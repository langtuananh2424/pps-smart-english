package com.pps.smartenglish.exercise.listening;

import com.pps.smartenglish.exercise.domain.QuestionBank;
import com.pps.smartenglish.exercise.domain.QuestionGroup;
import com.pps.smartenglish.exercise.domain.StudentAttempt;
import com.pps.smartenglish.exercise.listening.ForcedRule.Values;
import com.pps.smartenglish.exercise.listening.ListeningDtos.RuleInput;
import com.pps.smartenglish.exercise.listening.ListeningDtos.RuleView;
import com.pps.smartenglish.exercise.listening.ListeningDtos.ThresholdInput;
import com.pps.smartenglish.exercise.repository.QuestionBankRepository;
import com.pps.smartenglish.exercise.repository.QuestionGroupRepository;
import com.pps.smartenglish.shared.audit.AuditRecorder;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.persistence.CmsWrites;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Luật Nghe ép (FR-EX-10, D-136, D-138). Thứ tự ưu tiên: luật ghi đè của bài giao, luật mặc định của bộ, mặc định cấu hình.
 * Người tạo đề đặt luật của bộ; giáo viên ghi đè khi giao bài (bản chụp đủ bốn con số và ngưỡng part tại thời điểm giao,
 * nên sửa luật của bộ sau đó không đổi bài đã giao).
 */
@Service
public class ForcedRules {

    private static final String CONFLICT = "conflict";

    private final ForcedRuleRepository rules;
    private final GroupThresholdRepository thresholds;
    private final QuestionBankRepository banks;
    private final QuestionGroupRepository groups;
    private final ListeningProperties props;
    private final CmsWrites writes;
    private final AuditRecorder audit;

    ForcedRules(ForcedRuleRepository rules, GroupThresholdRepository thresholds, QuestionBankRepository banks,
                QuestionGroupRepository groups, ListeningProperties props, CmsWrites writes, AuditRecorder audit) {
        this.rules = rules;
        this.thresholds = thresholds;
        this.banks = banks;
        this.groups = groups;
        this.props = props;
        this.writes = writes;
        this.audit = audit;
    }

    /** Luật mặc định của bộ (dòng của bộ hoặc mặc định cấu hình). */
    @Transactional(readOnly = true)
    public RuleView forBank(long bankId) {
        return rules.findByScopeKey("B:" + bankId).map(this::view).orElseGet(this::defaults);
    }

    /** CMS đọc: bộ không tồn tại là 404 thay vì trả mặc định. */
    @Transactional(readOnly = true)
    public RuleView cmsView(long bankId) {
        if (!banks.existsById(bankId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "bank_not_found", "Không tìm thấy bộ câu hỏi");
        }
        return forBank(bankId);
    }

    /** Luật đang áp cho lượt: dòng đã gắn lúc bắt đầu, hoặc luật hiện tại của bộ nếu lượt bắt đầu khi chưa có dòng nào. */
    @Transactional(readOnly = true)
    public RuleView forAttempt(StudentAttempt a) {
        if (a.getRuleId() == null) {
            return forBank(a.getBankId());
        }
        return rules.findById(a.getRuleId()).map(this::view).orElseGet(() -> forBank(a.getBankId()));
    }

    /** Luật của bài giao (bài giao ghi đè hoặc luật của bộ), dùng cho bảng tiến độ giáo viên. */
    @Transactional(readOnly = true)
    public RuleView forAssignment(long bankId, long assignmentId) {
        Long id = ruleIdFor(bankId, assignmentId);
        return id == null ? defaults() : rules.findById(id).map(this::view).orElseGet(this::defaults);
    }

    /** Id luật gắn vào lượt mới: bài giao trước, rồi bộ; null nếu chưa có dòng nào (dùng mặc định cấu hình). */
    @Transactional(readOnly = true)
    public Long ruleIdFor(long bankId, Long assignmentId) {
        if (assignmentId != null) {
            var own = rules.findByScopeKey("A:" + assignmentId);
            if (own.isPresent()) {
                return own.get().getId();
            }
        }
        return rules.findByScopeKey("B:" + bankId).map(ForcedRule::getId).orElse(null);
    }

    /** CMS: đặt luật của bộ. Chỉ bộ đang ở chế độ LOCKED. Trường null giữ giá trị hiện có. */
    @Transactional
    public RuleView upsertBank(long bankId, RuleInput in) {
        QuestionBank bank = requireLocked(bankId);
        var before = forBank(bankId);
        requireOneThresholdKind(in);
        Values values = merge(before, in);
        var rule = rules.findByScopeKey("B:" + bankId).orElse(null);
        if (rule == null) {
            rule = writes.save(rules, ForcedRule.ofBank(bank.getId(), values), CONFLICT, "Không lưu được luật Nghe ép");
        } else {
            rule.apply(values);
            rule = writes.save(rules, rule, CONFLICT, "Không lưu được luật Nghe ép");
        }
        saveThresholds(rule.getId(), bankId, wantedThresholds(before, in));
        var after = view(rule);
        audit.record("UPDATE", "FORCED_LISTENING", bankId, before, after);
        return after;
    }

    /** Giáo viên giao bài: chụp luật hiệu lực của bộ, chồng phần ghi đè lên. Chỉ bộ LOCKED. */
    @Transactional
    public void createForAssignment(long assignmentId, long bankId, RuleInput in) {
        requireLocked(bankId);
        var base = forBank(bankId);
        requireOneThresholdKind(in);
        Values values = merge(base, in);
        var rule = writes.save(rules, ForcedRule.ofAssignment(bankId, assignmentId, values), CONFLICT, "Không lưu được luật Nghe ép");
        saveThresholds(rule.getId(), bankId, wantedThresholds(base, in));
    }

    private QuestionBank requireLocked(long bankId) {
        var bank = banks.findById(bankId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "bank_not_found", "Không tìm thấy bộ câu hỏi"));
        if (!QuestionBank.LOCKED.equals(bank.getPlaybackMode())) {
            throw new ApiException(HttpStatus.CONFLICT, "forced_listening_off", "Bộ câu hỏi chưa bật chế độ Nghe ép (playbackMode LOCKED)");
        }
        return bank;
    }

    /** D-142: cả bài HOẶC theo part, không cả hai. */
    private static void requireOneThresholdKind(RuleInput in) {
        if (in.passPctTotal() != null && in.groupThresholds() != null && !in.groupThresholds().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "threshold_conflict",
                    "Chỉ đặt ngưỡng cả bài hoặc ngưỡng theo part, không đặt cả hai", java.util.Map.of("passPctTotal", "Xung đột với groupThresholds"), null);
        }
    }

    /** Ngưỡng part sau khi áp đầu vào: đặt danh sách thì theo danh sách; đặt ngưỡng cả bài thì bỏ ngưỡng part; không đặt gì thì giữ. */
    private static List<ThresholdInput> wantedThresholds(RuleView base, RuleInput in) {
        if (in.groupThresholds() != null) {
            return in.groupThresholds();
        }
        return in.passPctTotal() != null ? List.of() : base.groupThresholds();
    }

    private Values merge(RuleView base, RuleInput in) {
        int playLimit = pick(in.playLimit(), base.playLimit());
        int hintEveryN = pick(in.hintEveryN(), base.hintEveryN());
        int wrong = pick(in.wrongThreshold(), base.wrongThreshold());
        cap("playLimit", playLimit, props.maxPlayLimit());
        cap("hintEveryN", hintEveryN, props.maxHintEveryN());
        cap("wrongThreshold", wrong, props.maxWrongThreshold());
        return new Values(playLimit, pick(in.passPctTotal(), base.passPctTotal()), hintEveryN, wrong);
    }

    /** Thay toàn bộ ngưỡng part; mọi part phải thuộc đúng bộ và không trùng. */
    private void saveThresholds(Long ruleId, long bankId, List<ThresholdInput> wanted) {
        Set<Long> ids = wanted.stream().map(ThresholdInput::groupId).collect(Collectors.toSet());
        if (ids.size() != wanted.size()) {
            throw ApiException.validation("groupThresholds", "Một part chỉ có một ngưỡng");
        }
        Set<Long> owned = groups.findAllById(ids).stream().filter(g -> Objects.equals(g.getBankId(), bankId))
                .map(QuestionGroup::getId).collect(Collectors.toSet());
        if (!owned.containsAll(ids)) {
            throw ApiException.validation("groupThresholds", "Có part không thuộc bộ câu hỏi này");
        }
        thresholds.deleteByRuleId(ruleId);
        thresholds.saveAll(wanted.stream().map(t -> new GroupThreshold(ruleId, t.groupId(), t.passPct())).toList());
    }

    private RuleView view(ForcedRule r) {
        var list = thresholds.findByRuleId(r.getId()).stream().map(t -> new ThresholdInput(t.getGroupId(), t.getPassPct()))
                .sorted(java.util.Comparator.comparing(ThresholdInput::groupId)).toList();
        return new RuleView(r.getId(), r.getPlayLimit(), r.getPassPctTotal(), r.getHintEveryN(), r.getWrongThreshold(), list, list.isEmpty() ? "TOTAL" : "PART");
    }

    private RuleView defaults() {
        return new RuleView(null, props.playLimit(), props.passPct(), props.hintEveryN(), props.wrongThreshold(), List.of(), "TOTAL");
    }

    private static int pick(Integer given, int fallback) {
        return given != null ? given : fallback;
    }

    private static void cap(String field, int v, int max) {
        if (v > max) {
            throw ApiException.validation(field, "Tối đa " + max);
        }
    }
}
