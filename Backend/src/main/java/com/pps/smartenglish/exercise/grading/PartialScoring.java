package com.pps.smartenglish.exercise.grading;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.function.Function;

/** Chấm từng phần cho kiểu "map id -> giá trị" (WORD_BANK, MATCH_OPTIONS); payload.scoring PARTIAL (mặc định) hoặc ALL (D-140). */
final class PartialScoring {

    private PartialScoring() {}

    static boolean isAll(JsonNode payload) {
        return payload != null && "ALL".equals(payload.path("scoring").asText());
    }

    /** ALL: 1 khi khớp hết. PARTIAL: số mục đúng ÷ max(số mục của key, số mục đã trả lời), nên khóa thừa không cộng điểm. */
    static BigDecimal credit(JsonNode payload, JsonNode expected, JsonNode given, Function<JsonNode, String> norm, boolean fullMatch) {
        if (isAll(payload)) {
            return Credit.of(fullMatch);
        }
        if (!expected.isObject() || !given.isObject() || expected.isEmpty()) {
            return Credit.ZERO;
        }
        int hits = 0;
        for (var e : expected.properties()) {
            String want = norm.apply(e.getValue());
            if (want != null && want.equals(norm.apply(given.get(e.getKey())))) {
                hits++;
            }
        }
        return Credit.ratio(hits, Math.max(expected.size(), given.size()));
    }
}
