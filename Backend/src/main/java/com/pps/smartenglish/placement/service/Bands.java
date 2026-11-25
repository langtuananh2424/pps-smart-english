package com.pps.smartenglish.placement.service;

import com.pps.smartenglish.placement.domain.PlacementBandRule;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** Quy đổi điểm sang band (thuần tính toán, FR-TEST-02). */
public final class Bands {

    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private Bands() {}

    /** Band của dòng có min_percent lớn nhất không vượt percent; không dòng nào thỏa thì 0. */
    static BigDecimal bandFor(List<PlacementBandRule> rules, String skill, BigDecimal percent) {
        return rules.stream().filter(r -> r.getSkill().equals(skill) && BigDecimal.valueOf(r.getMinPercent()).compareTo(percent) <= 0)
                .max(Comparator.comparingInt(PlacementBandRule::getMinPercent)).map(PlacementBandRule::getBand)
                .orElse(BigDecimal.ZERO).setScale(1, RoundingMode.UNNECESSARY);
    }

    /** Trung bình band, làm tròn 0.5 gần nhất như IELTS (x.25 lên x.5, x.75 lên x+1). Làm tròn đúng một lần, không qua số lẻ trung gian. */
    public static BigDecimal overall(Collection<BigDecimal> bands) {
        if (bands.isEmpty()) {
            return BigDecimal.ZERO.setScale(1);
        }
        BigDecimal doubledSum = bands.stream().reduce(BigDecimal.ZERO, BigDecimal::add).multiply(TWO);
        return doubledSum.divide(BigDecimal.valueOf(bands.size()), 0, RoundingMode.HALF_UP).divide(TWO, 1, RoundingMode.UNNECESSARY);
    }
}
