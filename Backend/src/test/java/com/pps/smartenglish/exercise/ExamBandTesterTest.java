package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.placement.service.Bands;
import com.pps.smartenglish.support.ApiTestBase;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.web.servlet.ResultActions;

/** Tester đợt #18/#24/D-12: biên band-rules, phân quyền cms.content, làm tròn band tổng, attemptId trong lastResult. */
class ExamBandTesterTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";

    private record World(long course, long bank, long[] reading, long[] listening) {}

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private World world() {
        long c = insert("courses", Map.of("slug", "ebt-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED"));
        long t = insert("topics", Map.of("level_id", lv, "title", "Topic", "status", "PUBLISHED"));
        long b = insert("question_banks", Map.of("topic_id", t, "title", "Thi", "status", "PUBLISHED", "mode", "EXAM"));
        jdbc.update("update courses set exam_bank_id = ?, exam_unlock_percent = 0 where id = ?", b, c);
        long[] r = {question(b, "READING"), question(b, "READING")};
        long[] l = {question(b, "LISTENING"), question(b, "LISTENING")};
        return new World(c, b, r, l);
    }

    private long question(long bank, String skill) {
        return insert("questions", Map.of("bank_id", bank, "type", "CONNECTIVE_MCQ", "prompt", "Câu", "skill", skill));
    }

    private Account withRole(String role) throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = ?", u.id(), role);
        return login(u.email(), PASSWORD);
    }

    private Account enrolled(World w) throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at) values (?, ?, 'ACTIVE', ?)",
                u.id(), w.course(), OffsetDateTime.parse("2020-01-01T00:00:00Z"));
        return u;
    }

    private long submitted(Account u, World w, List<Long> correct) {
        long a = insert("student_exercise_attempts", Map.of("user_id", u.id(), "bank_id", w.bank(), "submitted_at", OffsetDateTime.now(),
                "score", correct.size(), "max_score", 4));
        for (long[] group : List.of(w.reading(), w.listening())) {
            for (long q : group) {
                boolean ok = correct.contains(q);
                insert("student_answers", Map.of("attempt_id", a, "question_id", q, "is_correct", ok, "credit", ok ? 1 : 0));
            }
        }
        return a;
    }

    private ResultActions putRules(Account a, long bank, Object body) throws Exception {
        return send(put("/api/admin/cms/banks/" + bank + "/band-rules").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)), a.accessToken());
    }

    private static Map<String, Object> rule(String skill, int min, double band) {
        return Map.of("skill", skill, "minCorrect", min, "band", band);
    }

    private JsonNode get200(String url, Account u) throws Exception {
        return json.readTree(send(get(url), u.accessToken()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void overallRoundsLikeIelts() {
        // trung bình x.25 -> x.5; x.75 -> x+1; dưới .25 -> x.0; giữa .5 và .75 -> x.5
        assertEquals(new BigDecimal("6.5"), Bands.overall(List.of(new BigDecimal("6.5"), new BigDecimal("6.0")))); // 6.25
        assertEquals(new BigDecimal("7.0"), Bands.overall(List.of(new BigDecimal("7.0"), new BigDecimal("6.5")))); // 6.75
        assertEquals(new BigDecimal("6.0"), Bands.overall(List.of(new BigDecimal("6.0"), new BigDecimal("6.0"), new BigDecimal("6.5")))); // 6.1667
        assertEquals(new BigDecimal("6.5"), Bands.overall(List.of(new BigDecimal("6.5"), new BigDecimal("6.5"), new BigDecimal("7.0")))); // 6.6667
        assertEquals(new BigDecimal("6.5"), Bands.overall(List.of(new BigDecimal("6.5"))));
        assertEquals(new BigDecimal("6.0"), Bands.overall(List.of(new BigDecimal("6.0"), new BigDecimal("6.0"), new BigDecimal("6.0"), new BigDecimal("6.5")))); // 6.125
        assertEquals(new BigDecimal("6.5"), Bands.overall(List.of(new BigDecimal("6.5"), new BigDecimal("6.5"), new BigDecimal("6.5"), new BigDecimal("6.0")))); // 6.375
    }

    @Test
    void ruleCountBoundaryIs200() throws Exception {
        var admin = withRole("ADMIN");
        var w = world();
        List<Map<String, Object>> rules = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            rules.add(rule(i < 100 ? "READING" : "LISTENING", i % 100, 5.0));
        }
        putRules(admin, w.bank(), Map.of("rules", rules)).andExpect(status().isOk());
        rules.add(rule("GRAMMAR", 0, 5.0));
        putRules(admin, w.bank(), Map.of("rules", rules)).andExpect(status().isBadRequest());
        assertEquals(200, count("select count(*) from exam_band_rules where bank_id = ?", w.bank())); // bảng cũ giữ nguyên
    }

    @Test
    void invalidBodiesAre400AndLeaveTableUnchanged() throws Exception {
        var admin = withRole("ADMIN");
        var w = world();
        putRules(admin, w.bank(), Map.of("rules", List.of(rule("READING", 0, 4.0)))).andExpect(status().isOk());
        var nullItem = new HashMap<String, Object>();
        nullItem.put("rules", Arrays.asList((Object) null));
        for (Object bad : List.<Object>of(Map.of(), nullItem, Map.of("rules", List.of(rule("READING", -1, 4.0))),
                Map.of("rules", List.of(rule("READING", 1001, 4.0))), Map.of("rules", List.of(rule("READING", 1, -0.5))),
                Map.of("rules", List.of(rule("reading", 1, 4.0))),
                Map.of("rules", List.of(rule("LISTENING", 3, 4.0), rule("LISTENING", 3, 5.0))))) {
            putRules(admin, w.bank(), bad).andExpect(status().isBadRequest());
        }
        assertEquals(1, count("select count(*) from exam_band_rules where bank_id = ?", w.bank()));
        // cùng minCorrect ở hai kỹ năng khác nhau là hợp lệ
        putRules(admin, w.bank(), Map.of("rules", List.of(rule("LISTENING", 3, 4.0), rule("READING", 3, 5.0)))).andExpect(status().isOk());
        // mảng rỗng = xóa bảng
        putRules(admin, w.bank(), Map.of("rules", List.of())).andExpect(status().isOk());
        assertEquals(0, count("select count(*) from exam_band_rules where bank_id = ?", w.bank()));
    }

    @Test
    void onlyCmsContentCanReadOrWriteRules() throws Exception {
        var w = world();
        for (String role : List.of("TEACHER", "SUPPORT", "ACCOUNTANT")) {
            var u = withRole(role);
            send(get("/api/admin/cms/banks/" + w.bank() + "/band-rules"), u.accessToken()).andExpect(status().isForbidden());
            putRules(u, w.bank(), Map.of("rules", List.of(rule("READING", 0, 9.0)))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/admin/cms/banks/" + w.bank() + "/band-rules")).andExpect(status().isUnauthorized());
        assertEquals(0, count("select count(*) from exam_band_rules where bank_id = ?", w.bank()));
    }

    @Test
    void practiceBankHasNoBandTable() throws Exception {
        var admin = withRole("ADMIN");
        var w = world();
        jdbc.update("update question_banks set mode = 'PRACTICE' where id = ?", w.bank());
        putRules(admin, w.bank(), Map.of("rules", List.of(rule("READING", 0, 4.0)))).andExpect(status().isNotFound());
    }

    @Test
    void lastResultAttemptIdIsAlwaysCallersOwn() throws Exception {
        var w = world();
        var a = enrolled(w);
        var b = enrolled(w);
        long attemptA = submitted(a, w, List.of(w.reading()[0]));
        putRules(withRole("ADMIN"), w.bank(), Map.of("rules", List.of(rule("READING", 0, 4.0)))).andExpect(status().isOk());

        assertEquals(attemptA, get200("/api/courses/" + w.course() + "/exam", login(a.email(), PASSWORD)).at("/lastResult/attemptId").asLong());
        var bStatus = get200("/api/courses/" + w.course() + "/exam", login(b.email(), PASSWORD));
        assertTrue(bStatus.path("lastResult").isMissingNode() || bStatus.path("lastResult").isNull(), "B chưa thi mà thấy lastResult: " + bStatus);
        for (JsonNode t : get200("/api/courses/" + w.course() + "/tests", login(b.email(), PASSWORD))) {
            assertTrue(t.path("lastResult").isMissingNode() || t.path("lastResult").isNull(), "tests của B lộ lượt của A: " + t);
        }
        // B mở thẳng id lượt của A -> 404, không lộ điểm/đáp án
        send(get("/api/attempts/" + attemptA), login(b.email(), PASSWORD).accessToken()).andExpect(status().isNotFound());

        long attemptB = submitted(b, w, List.of());
        assertNotEquals(attemptA, attemptB);
        assertEquals(attemptB, get200("/api/courses/" + w.course() + "/exam", login(b.email(), PASSWORD)).at("/lastResult/attemptId").asLong());
    }

    @Test
    void homeBandSourceIsExamForOwnerOnly() throws Exception {
        var w = world();
        var a = enrolled(w);
        var b = enrolled(w);
        submitted(a, w, List.of(w.reading()[0], w.reading()[1]));
        putRules(withRole("ADMIN"), w.bank(), Map.of("rules", List.of(rule("READING", 2, 7.0)))).andExpect(status().isOk());
        var statsA = get200("/api/me/courses/" + w.course() + "/exercise-stats", login(a.email(), PASSWORD));
        assertEquals("EXAM", statsA.at("/band/source").asText(), statsA.toString());
        var statsB = get200("/api/me/courses/" + w.course() + "/exercise-stats", login(b.email(), PASSWORD));
        assertTrue(statsB.path("band").isNull() || statsB.path("band").isMissingNode(), "B thấy band của A: " + statsB);
    }

    /** Đề thi rỗng (chưa có câu) nhưng đã có bảng quy đổi: xóa không được 500. */
    @Test
    void deletingEmptyExamBankWithRulesIsNot500() throws Exception {
        var admin = withRole("ADMIN");
        long c = insert("courses", Map.of("slug", "ebd-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED"));
        long t = insert("topics", Map.of("level_id", lv, "title", "Topic", "status", "PUBLISHED"));
        long b = insert("question_banks", Map.of("topic_id", t, "title", "Thi rỗng", "status", "DRAFT", "mode", "EXAM"));
        putRules(admin, b, Map.of("rules", List.of(rule("READING", 0, 4.0)))).andExpect(status().isOk());
        int code = send(delete("/api/admin/cms/banks/" + b), admin.accessToken()).andReturn().getResponse().getStatus();
        assertTrue(code < 500, "DELETE trả " + code);
    }
}
