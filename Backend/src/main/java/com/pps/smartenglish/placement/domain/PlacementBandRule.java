package com.pps.smartenglish.placement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Một dòng bảng quy đổi: % đúng của kỹ năng >= minPercent thì đạt band này (lấy dòng minPercent lớn nhất thỏa). */
@Entity
@Table(name = "placement_band_rules")
public class PlacementBandRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String skill;

    @Column(name = "min_percent")
    private int minPercent;

    private BigDecimal band;

    protected PlacementBandRule() {}

    public PlacementBandRule(String skill, int minPercent, BigDecimal band) {
        this.skill = skill;
        this.minPercent = minPercent;
        this.band = band;
    }

    public String getSkill() { return skill; }
    public int getMinPercent() { return minPercent; }
    public BigDecimal getBand() { return band; }
}
