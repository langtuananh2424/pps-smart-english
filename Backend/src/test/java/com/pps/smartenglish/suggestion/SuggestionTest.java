package com.pps.smartenglish.suggestion;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.support.ApiTestBase;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.web.servlet.ResultActions;

/** D-55 (Q&A) và D-56 (gợi ý khóa). Dựng 6 khóa IELTS như seed demo, bỏ band của khóa do test khác để lại. */
class SuggestionTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";
    private static final String QA = "/api/me/qa";
    private static final String GUEST = "/api/catalog/course-suggestions";

    private String ae1;
    private String ae2;
    private String pre;
    private String foundation;
    private String zeroToThree;
    private String superIelts;
    private long bankId;

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private String course(String name, String from, String to) {
        String slug = name + "-" + UUID.randomUUID();
        long c = insert("courses", Map.of("slug", slug, "title", name, "status", "PUBLISHED", "target_type", "IELTS"));
        jdbc.update("update courses set band_from = ?, band_to = ? where id = ?", new BigDecimal(from), new BigDecimal(to), c);
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "A", "status", "PUBLISHED"));
        long t = insert("topics", Map.of("level_id", lv, "title", "T", "is_free_trial", true, "status", "PUBLISHED"));
        jdbc.update("insert into lessons (topic_id, type, title, status) values (?, 'CONNECTIVE_VIDEO', 'B1', 'PUBLISHED')", t);
        jdbc.update("insert into lessons (topic_id, type, title, status) values (?, 'CONNECTIVE_VIDEO', 'B2', 'PUBLISHED')", t);
        long p = insert("products", Map.of("type", "LEVEL", "name", name + UUID.randomUUID(), "price_vnd", 1_000_000L,
                "access_days", 180, "status", "ACTIVE"));
        jdbc.update("insert into product_levels (product_id, level_id) values (?, ?)", p, lv);
        bankId = insert("question_banks", Map.of("topic_id", t, "title", "Bộ", "is_trial", false, "status", "DRAFT"));
        return slug;
    }

    @BeforeEach
    void courses() {
        jdbc.update("update courses set band_from = null, band_to = null");
        zeroToThree = course("Begin", "0.0", "3.0");
        ae1 = course("AE1", "3.0", "4.0");
        ae2 = course("AE2", "4.0", "5.0");
        pre = course("Pre IELTS", "5.0", "5.5");
        foundation = course("IELTS Foundation", "5.5", "6.0");
        superIelts = course("Super IELTS", "6.0", "7.0");
    }

    private static Map<String, Object> qa(String level, Object band) {
        var m = new HashMap<String, Object>();
        m.put("selfLevel", level);
        m.put("goals", List.of("IELTS_CERT", "WORK"));
        m.put("targetBand", band);
        m.put("weeklyHours", "H3_5");
        m.put("skills", List.of("LISTENING", "WRITING"));
        return m;
    }

    private ResultActions putQa(Account a, Object body) throws Exception {
        return send(put(QA).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)), a.accessToken());
    }

    private ResultActions guest(Object body, String ip) throws Exception {
        return post(GUEST, body, ip);
    }

    private Account user() throws Exception {
        return register(newEmail(), PASSWORD);
    }

    // ---- Q&A ----

    @Test
    void qaRejectsInvalidAnswers() throws Exception {
        var u = user();
        var noBand = qa("CO_BAN", 6.0);
        noBand.remove("targetBand");
        putQa(u, noBand).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("validation_failed"));
        putQa(u, Map.of()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("validation_failed"));
        putQa(u, qa("GIOI", 6.0)).andExpect(status().isBadRequest()); // sai enum

        var three = qa("CO_BAN", 6.0);
        three.put("goals", List.of("IELTS_CERT", "WORK", "SCHOOL"));
        putQa(u, three).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("validation_failed"));
        var none = qa("CO_BAN", 6.0);
        none.put("goals", List.of());
        putQa(u, none).andExpect(status().isBadRequest());
        var dup = qa("CO_BAN", 6.0);
        dup.put("goals", List.of("WORK", "WORK"));
        putQa(u, dup).andExpect(status().isBadRequest());
        var dupSkill = qa("CO_BAN", 6.0);
        dupSkill.put("skills", List.of("LISTENING", "LISTENING"));
        putQa(u, dupSkill).andExpect(status().isBadRequest());

        putQa(u, qa("CO_BAN", 8.5)).andExpect(status().isBadRequest());
        putQa(u, qa("CO_BAN", 3.5)).andExpect(status().isBadRequest());
        putQa(u, qa("CO_BAN", 6.25)).andExpect(status().isBadRequest()); // sai bước 0.5
        assertEquals(0, count("select count(*) from user_profiles where user_id = ? and qa_answers is not null", u.id()));
        mvc.perform(put(QA).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }

    @Test
    void qaSavesAndOverwritesAnswersAndReplacesGoals() throws Exception {
        var u = user();
        send(get(QA), u.accessToken()).andExpect(status().isNoContent()); // chưa làm
        send(put("/api/me/goals").contentType(MediaType.APPLICATION_JSON)
                .content("{\"goals\":[{\"targetType\":\"TOEIC\",\"targetValue\":\"700\"},{\"targetType\":\"X\",\"targetValue\":\"1\"}]}"),
                u.accessToken()).andExpect(status().isOk());

        putQa(u, qa("CO_BAN", 6)).andExpect(status().isOk())
                .andExpect(jsonPath("$.selfLevel").value("CO_BAN"))
                .andExpect(jsonPath("$.goals", hasSize(2)))
                .andExpect(jsonPath("$.targetBand").value(6.0))
                .andExpect(jsonPath("$.weeklyHours").value("H3_5"))
                .andExpect(jsonPath("$.skills[1]").value("WRITING"))
                .andExpect(jsonPath("$.updatedAt").exists());
        assertEquals("CO_BAN", jdbc.queryForObject("select self_level from user_profiles where user_id = ?", String.class, u.id()));
        var goals = jdbc.queryForList("select target_type, target_value from user_goals where user_id = ?", u.id());
        assertEquals(1, goals.size());
        assertEquals("IELTS", goals.get(0).get("target_type"));
        assertEquals("6.0", goals.get(0).get("target_value"));

        var again = qa("KHA", 7.5);
        again.put("skills", List.of());
        putQa(u, again).andExpect(status().isOk());
        send(get(QA), u.accessToken()).andExpect(status().isOk()).andExpect(jsonPath("$.selfLevel").value("KHA"))
                .andExpect(jsonPath("$.targetBand").value(7.5)).andExpect(jsonPath("$.skills", hasSize(0)));
        goals = jdbc.queryForList("select target_value from user_goals where user_id = ?", u.id());
        assertEquals(1, goals.size());
        assertEquals("7.5", goals.get(0).get("target_value"));
    }

    // ---- gợi ý khóa ----

    private ResultActions mine(Account u) throws Exception {
        return send(get("/api/me/course-suggestions"), u.accessToken());
    }

    @Test
    void suggestionFromSelfLevelWalksThePathToTheTargetBand() throws Exception {
        var u = user();
        putQa(u, qa("CO_BAN", 6.0)).andExpect(status().isOk());
        mine(u).andExpect(status().isOk())
                .andExpect(jsonPath("$.basis").value("SELF_LEVEL"))
                .andExpect(jsonPath("$.currentBand").value(3.0))
                .andExpect(jsonPath("$.targetBand").value(6.0))
                .andExpect(jsonPath("$.primary.slug").value(ae1))
                .andExpect(jsonPath("$.primary.bandFrom").value(3.0))
                .andExpect(jsonPath("$.primary.trialLessonCount").value(2))
                .andExpect(jsonPath("$.primary.priceFromVnd").value(1_000_000))
                .andExpect(jsonPath("$.path", hasSize(3)))
                .andExpect(jsonPath("$.path[0].slug").value(ae2))
                .andExpect(jsonPath("$.path[1].slug").value(pre))
                .andExpect(jsonPath("$.path[2].slug").value(foundation));
    }

    private void placement(long userId, String band) {
        jdbc.update("insert into placement_attempts (bank_id, user_id, started_at, time_limit_sec, deadline_at, submitted_at, overall_band)"
                + " values (?, ?, ?, 1800, ?, ?, ?)", bankId, userId, OffsetDateTime.now().minusHours(2),
                OffsetDateTime.now().minusHours(1), OffsetDateTime.now().minusHours(1), new BigDecimal(band));
    }

    @Test
    void placementBandWinsOverSelfLevel() throws Exception {
        var u = user();
        putQa(u, qa("MAT_GOC", 7.0)).andExpect(status().isOk()); // tự đánh giá band 0
        placement(u.id(), "5.5");
        mine(u).andExpect(jsonPath("$.basis").value("PLACEMENT")).andExpect(jsonPath("$.currentBand").value(5.5))
                .andExpect(jsonPath("$.primary.slug").value(foundation))
                .andExpect(jsonPath("$.path", hasSize(1))).andExpect(jsonPath("$.path[0].slug").value(superIelts));
    }

    @Test
    void bandAboveEveryRangePicksTheTopCourseWithEmptyPath() throws Exception {
        var u = user();
        putQa(u, qa("KHA", 8.0));
        placement(u.id(), "8.0");
        mine(u).andExpect(jsonPath("$.primary.slug").value(superIelts)).andExpect(jsonPath("$.path", hasSize(0)));
    }

    @Test
    void noPathWithoutTargetAndNoneWithoutAnyData() throws Exception {
        var u = user();
        mine(u).andExpect(status().isOk()).andExpect(jsonPath("$.basis").value("NONE"))
                .andExpect(jsonPath("$.currentBand").doesNotExist()).andExpect(jsonPath("$.primary").doesNotExist())
                .andExpect(jsonPath("$.path", hasSize(0)));

        // có trình độ tự đánh giá (PATCH /api/me) nhưng chưa làm Q&A: có khóa chính, không có lộ trình
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/me")
                .contentType(MediaType.APPLICATION_JSON).content("{\"selfLevel\":\"TRUNG_CAP\"}"), u.accessToken())
                .andExpect(status().isOk());
        mine(u).andExpect(jsonPath("$.basis").value("SELF_LEVEL")).andExpect(jsonPath("$.primary.slug").value(ae2))
                .andExpect(jsonPath("$.targetBand").doesNotExist()).andExpect(jsonPath("$.path", hasSize(0)));
        mvc.perform(get("/api/me/course-suggestions")).andExpect(status().isUnauthorized());
    }

    @Test
    void guestGetsSuggestionWithoutTokenAndNothingIsStored() throws Exception {
        int profilesWithQa = count("select count(*) from user_profiles where qa_answers is not null");
        int goalRows = count("select count(*) from user_goals");
        guest(qa("MAT_GOC", 4.5), newIp()).andExpect(status().isOk())
                .andExpect(jsonPath("$.basis").value("SELF_LEVEL")).andExpect(jsonPath("$.currentBand").value(0.0))
                .andExpect(jsonPath("$.primary.slug").value(zeroToThree))
                .andExpect(jsonPath("$.path[0].slug").value(ae1)).andExpect(jsonPath("$.path[1].slug").value(ae2))
                .andExpect(jsonPath("$.path", hasSize(2))); // 0->3, 3->4, 4->5 phủ band 4.5
        assertEquals(profilesWithQa, count("select count(*) from user_profiles where qa_answers is not null"));
        assertEquals(goalRows, count("select count(*) from user_goals"));

        guest(Map.of("selfLevel", "CO_BAN"), newIp()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_failed"));
        guest(qa("CO_BAN", 9), newIp()).andExpect(status().isBadRequest());
    }

    @Test
    void guestSuggestionIsRateLimitedPerIp() throws Exception {
        String ip = newIp();
        for (int i = 0; i < 30; i++) {
            guest(qa("CO_BAN", 6.0), ip).andExpect(status().isOk());
        }
        guest(qa("CO_BAN", 6.0), ip).andExpect(status().isTooManyRequests());
        guest(qa("CO_BAN", 6.0), newIp()).andExpect(status().isOk());
    }
}
