package com.pps.smartenglish.exercise.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Một dòng bảng quy đổi của đề thi khóa (D-167): % điểm của kỹ năng >= minPercent thì đạt band này (lấy dòng lớn nhất thỏa). */
@Entity
@Table(name = "exam_band_rules")
public class ExamBandRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bank_id", nullable = false, updatable = false)
    private Long bankId;

    private String skill;

    @Column(name = "min_percent")
    private int minPercent;

    private BigDecimal band;

    protected ExamBandRule() {}

    public ExamBandRule(Long bankId, String skill, int minPercent, BigDecimal band) {
        this.bankId = bankId;
        this.skill = skill;
        this.minPercent = minPercent;
        this.band = band;
    }

    public Long getBankId() { return bankId; }
    public String getSkill() { return skill; }
    public int getMinPercent() { return minPercent; }
    public BigDecimal getBand() { return band; }
}
