package com.pps.smartenglish.exercise.listening;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Ngưỡng đạt (% số câu đúng) riêng của một part trong một luật. */
@Entity
@Table(name = "forced_listening_group_thresholds")
public class GroupThreshold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_id", nullable = false, updatable = false)
    private Long ruleId;

    @Column(name = "group_id", nullable = false, updatable = false)
    private Long groupId;

    @Column(name = "pass_pct", nullable = false)
    private int passPct;

    protected GroupThreshold() {}

    public GroupThreshold(Long ruleId, Long groupId, int passPct) {
        this.ruleId = ruleId;
        this.groupId = groupId;
        this.passPct = passPct;
    }

    public Long getRuleId() { return ruleId; }
    public Long getGroupId() { return groupId; }
    public int getPassPct() { return passPct; }
}
