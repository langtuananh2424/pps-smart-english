package com.pps.smartenglish.exercise.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Một dòng bảng quy đổi của đề thi khóa: số câu đúng của kỹ năng >= minCorrect thì đạt band này (lấy dòng lớn nhất thỏa). */
@Entity
@Table(name = "exam_band_rules")
public class ExamBandRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bank_id", nullable = false, updatable = false)
    private Long bankId;

    private String skill;

    @Column(name = "min_correct")
    private int minCorrect;

    private BigDecimal band;

    protected ExamBandRule() {}

    public ExamBandRule(Long bankId, String skill, int minCorrect, BigDecimal band) {
        this.bankId = bankId;
        this.skill = skill;
        this.minCorrect = minCorrect;
        this.band = band;
    }

    public Long getBankId() { return bankId; }
    public String getSkill() { return skill; }
    public int getMinCorrect() { return minCorrect; }
    public BigDecimal getBand() { return band; }
}
