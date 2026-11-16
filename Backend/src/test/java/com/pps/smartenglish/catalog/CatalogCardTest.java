package com.pps.smartenglish.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.support.ApiTestBase;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;

/** D-54: thẻ khóa học có dải band, số bài học thử, giá thấp nhất, số level và số bài; sắp theo bandFrom. */
class CatalogCardTest extends ApiTestBase {

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private long course(String slug, String from, String to) {
        long c = insert("courses", Map.of("slug", slug, "title", "K " + slug, "status", "PUBLISHED", "target_type", "IELTS"));
        if (from != null) {
            jdbc.update("update courses set band_from = ?, band_to = ? where id = ?", new BigDecimal(from), new BigDecimal(to), c);
        }
        return c;
    }

    private long level(long course, String code, String status) {
        return insert("course_levels", Map.of("course_id", course, "code", code, "title", code, "status", status));
    }

    private long topic(long level, int order, boolean free) {
        return insert("topics", Map.of("level_id", level, "title", "T" + order, "sort_order", order, "is_free_trial", free, "status", "PUBLISHED"));
    }

    private void lessons(long topic, int published, int draft) {
        for (int i = 0; i < published + draft; i++) {
            jdbc.update("insert into lessons (topic_id, type, title, sort_order, status) values (?, 'CONNECTIVE_VIDEO', ?, ?, ?)",
                    topic, "B" + i, i, i < published ? "PUBLISHED" : "DRAFT");
        }
    }

    private long product(String type, String name, long price, Integer days, String status, long... levels) {
        var cols = new java.util.HashMap<String, Object>(Map.of("type", type, "name", name + UUID.randomUUID(), "price_vnd", price,
                "status", status));
        if (days != null) {
            cols.put("access_days", days);
        }
        long p = insert("products", cols);
        for (long lv : levels) {
            jdbc.update("insert into product_levels (product_id, level_id) values (?, ?)", p, lv);
        }
        return p;
    }

    @Test
    void listCardsCarryBandTrialPriceAndCountsAndSortByBandFrom() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        String high = "hi-" + tag;
        String low = "lo-" + tag;
        String none = "no-" + tag;
        course(none, null, null);
        long hi = course(high, "5.0", "6.0");
        long lo = course(low, "3.0", "4.0");

        long l1 = level(lo, "A", "PUBLISHED");
        long l2 = level(lo, "B", "PUBLISHED");
        long draftLevel = level(lo, "C", "DRAFT");
        long free = topic(l1, 1, true);
        lessons(free, 2, 1); // 2 bài PUBLISHED + 1 nháp
        lessons(topic(l1, 2, false), 1, 0);
        lessons(topic(l2, 1, false), 1, 0);
        lessons(topic(draftLevel, 1, false), 5, 0); // level nháp không tính
        product("LEVEL", "180 ", 4_200_000, 180, "ACTIVE", l1, l2);
        product("LEVEL", "365 ", 6_720_000, 365, "ACTIVE", l1, l2);
        product("LEVEL", "inactive ", 100_000, 30, "INACTIVE", l1);
        product("AI_ADDON", "addon ", 49_000, null, "ACTIVE");
        level(hi, "A", "PUBLISHED"); // khóa không có gói bán

        JsonNode list = json.readTree(mvc.perform(get("/api/catalog/courses")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        List<String> mine = new ArrayList<>();
        JsonNode card = null;
        JsonNode noPrice = null;
        for (JsonNode n : list) {
            String s = n.get("slug").asText();
            if (s.endsWith(tag)) {
                mine.add(s);
            }
            if (s.equals(low)) {
                card = n;
            } else if (s.equals(high)) {
                noPrice = n;
            }
        }
        assertEquals(List.of(low, high, none), mine); // bandFrom tăng dần, không có band xuống cuối
        assertEquals(0, new BigDecimal("3.0").compareTo(card.get("bandFrom").decimalValue()));
        assertEquals(0, new BigDecimal("4.0").compareTo(card.get("bandTo").decimalValue()));
        assertEquals(2, card.get("trialLessonCount").asInt());
        assertEquals(4_200_000L, card.get("priceFromVnd").asLong());
        assertEquals(2, card.get("levelCount").asInt());
        assertEquals(4, card.get("lessonCount").asInt());
        assertTrue(noPrice.get("priceFromVnd").isNull());
        assertEquals(0, noPrice.get("trialLessonCount").asInt());

        JsonNode detail = json.readTree(mvc.perform(get("/api/catalog/courses/" + low)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals(2, detail.get("products").size());
        assertEquals(180, detail.get("products").get(0).get("accessDays").asInt());
        assertEquals(6_720_000L, detail.get("products").get(1).get("priceVnd").asLong());
        assertEquals(4, detail.get("lessonCount").asInt());
        assertEquals(2, detail.get("trialLessonCount").asInt());
    }
}
