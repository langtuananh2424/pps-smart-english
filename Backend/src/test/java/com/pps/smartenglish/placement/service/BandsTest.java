package com.pps.smartenglish.placement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.pps.smartenglish.placement.domain.PlacementBandRule;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** FR-TEST-02: quy đổi % -> band và làm tròn level tổng theo kiểu IELTS. */
class BandsTest {

    private static BigDecimal b(String v) {
        return new BigDecimal(v);
    }

    private static final List<PlacementBandRule> RULES = List.of(new PlacementBandRule("READING", 0, b("2.0")),
            new PlacementBandRule("READING", 40, b("4.5")), new PlacementBandRule("READING", 80, b("6.5")),
            new PlacementBandRule("GRAMMAR", 50, b("5.0")));

    @Test
    void bandIsTheRuleWithLargestMinPercentNotAbovePercent() {
        assertEquals(b("2.0"), Bands.bandFor(RULES, "READING", b("0")));
        assertEquals(b("2.0"), Bands.bandFor(RULES, "READING", b("39.99")));
        assertEquals(b("4.5"), Bands.bandFor(RULES, "READING", b("40")));
        assertEquals(b("6.5"), Bands.bandFor(RULES, "READING", b("100")));
        // dưới dòng thấp nhất, hoặc kỹ năng không có dòng: band 0
        assertEquals(b("0.0"), Bands.bandFor(RULES, "GRAMMAR", b("49")));
        assertEquals(b("0.0"), Bands.bandFor(RULES, "LISTENING", b("100")));
    }

    @Test
    void overallRoundsToNearestHalfLikeIelts() {
        assertEquals(b("6.5"), Bands.overall(List.of(b("6.5"), b("6.0")))); // 6.25 lên 6.5
        assertEquals(b("7.0"), Bands.overall(List.of(b("6.5"), b("7.0")))); // 6.75 lên 7.0
        assertEquals(b("6.0"), Bands.overall(List.of(b("6.0"), b("6.0"), b("6.5")))); // 6.17 xuống 6.0
        assertEquals(b("5.5"), Bands.overall(List.of(b("7.0"), b("7.0"), b("3.0")))); // 5.67 xuống 5.5
        assertEquals(b("6.5"), Bands.overall(List.of(b("6.0"), b("6.5"), b("7.0"), b("7.0")))); // 6.625 xuống 6.5
        assertEquals(b("4.5"), Bands.overall(List.of(b("4.5"))));
        assertEquals(b("0.0"), Bands.overall(List.of()));
    }
}
