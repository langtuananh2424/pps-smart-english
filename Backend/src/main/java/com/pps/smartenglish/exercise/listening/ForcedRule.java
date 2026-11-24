package com.pps.smartenglish.exercise.listening;

import com.pps.smartenglish.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Luật Nghe ép (D-136): mặc định của bộ (assignmentId null) hoặc bản ghi đè khi giao bài. scopeKey chốt "mỗi phạm vi một luật". */
@Entity
@Table(name = "forced_listening_rules")
public class ForcedRule extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scope_key", nullable = false, updatable = false)
    private String scopeKey;

    @Column(name = "bank_id", nullable = false, updatable = false)
    private Long bankId;

    @Column(name = "assignment_id", updatable = false)
    private Long assignmentId;

    @Column(name = "play_limit", nullable = false)
    private int playLimit;

    @Column(name = "pass_pct_total", nullable = false)
    private int passPctTotal;

    @Column(name = "hint_every_n", nullable = false)
    private int hintEveryN;

    @Column(name = "wrong_threshold", nullable = false)
    private int wrongThreshold;

    protected ForcedRule() {}

    public static ForcedRule ofBank(long bankId, Values v) {
        return new ForcedRule("B:" + bankId, bankId, null, v);
    }

    public static ForcedRule ofAssignment(long bankId, long assignmentId, Values v) {
        return new ForcedRule("A:" + assignmentId, bankId, assignmentId, v);
    }

    private ForcedRule(String scopeKey, Long bankId, Long assignmentId, Values v) {
        this.scopeKey = scopeKey;
        this.bankId = bankId;
        this.assignmentId = assignmentId;
        apply(v);
    }

    /** Bốn con số của luật, đã kiểm cận trên ở ForcedRules. */
    public record Values(int playLimit, int passPctTotal, int hintEveryN, int wrongThreshold) {}

    public void apply(Values v) {
        this.playLimit = v.playLimit();
        this.passPctTotal = v.passPctTotal();
        this.hintEveryN = v.hintEveryN();
        this.wrongThreshold = v.wrongThreshold();
    }

    public Long getId() { return id; }
    public Long getBankId() { return bankId; }
    public Long getAssignmentId() { return assignmentId; }
    public int getPlayLimit() { return playLimit; }
    public int getPassPctTotal() { return passPctTotal; }
    public int getHintEveryN() { return hintEveryN; }
    public int getWrongThreshold() { return wrongThreshold; }
}
