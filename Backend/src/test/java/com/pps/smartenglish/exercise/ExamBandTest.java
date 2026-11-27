package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.support.ApiTestBase;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;

/** FR-EXAM-03, D-12, D-63: bảng quy đổi band đề thi khóa, band trong kết quả lượt thi, band ước tính ưu tiên thi khóa. */
class ExamBandTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";

    private record World(long course, long bank, long[] reading, long[] listening) {}

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    /** Khóa có đề thi cuối khóa 4 câu: 2 Reading, 2 Listening. */
    private World world() {
        long c = insert("courses", Map.of("slug", "eb-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
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

    private Account admin() throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = 'ADMIN'", u.id());
        return login(u.email(), PASSWORD);
    }

    private Account enrolled(World w) throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at) values (?, ?, 'ACTIVE', ?)",
                u.id(), w.course(), OffsetDateTime.parse("2020-01-01T00:00:00Z"));
        return u;
    }

    /** Lượt đã nộp; correct = id các câu trả lời đúng, câu còn lại sai. */
    private long submitted(Account u, World w, List<Long> correct) {
        long a = insert("student_exercise_attempts", Map.of("user_id", u.id(), "bank_id", w.bank(), "submitted_at", OffsetDateTime.now(),
                "score", correct.size(), "max_score", 4));
        for (long q : concat(w.reading(), w.listening())) {
            boolean ok = correct.contains(q);
            insert("student_answers", Map.of("attempt_id", a, "question_id", q, "is_correct", ok, "credit", ok ? 1 : 0));
        }
        return a;
    }

    private static long[] concat(long[] x, long[] y) {
        return java.util.stream.LongStream.concat(java.util.stream.LongStream.of(x), java.util.stream.LongStream.of(y)).toArray();
    }

    private void putRules(Account admin, long bank, List<Map<String, Object>> rules) throws Exception {
        send(put("/api/admin/cms/banks/" + bank + "/band-rules").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("rules", rules))), admin.accessToken()).andExpect(status().isOk());
    }

    private static Map<String, Object> rule(String skill, int min, double band) {
        return Map.of("skill", skill, "minPercent", min, "band", band);
    }

    private JsonNode examStatus(Account u, World w) throws Exception {
        return json.readTree(send(get("/api/courses/" + w.course() + "/exam"), u.accessToken()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void lastResultCarriesAttemptIdAndBandsWhenTableExists() throws Exception {
        var w = world();
        var u = enrolled(w);
        long a = submitted(u, w, List.of(w.reading()[0], w.reading()[1], w.listening()[0]));

        assertTrue(examStatus(u, w).at("/lastResult/bands").isMissingNode() || examStatus(u, w).at("/lastResult/bands").isNull(), "chưa có bảng thì không có band");
        assertEquals(a, examStatus(u, w).at("/lastResult/attemptId").asLong());

        putRules(admin(), w.bank(), List.of(rule("READING", 0, 4.0), rule("READING", 100, 6.5), rule("LISTENING", 0, 4.0), rule("LISTENING", 100, 6.0)));
        var bands = examStatus(u, w).at("/lastResult/bands");
        assertEquals(6.5, bands.at("/skills/READING/band").asDouble());
        assertEquals(2, bands.at("/skills/READING/total").asInt());
        assertEquals(4.0, bands.at("/skills/LISTENING/band").asDouble()); // 1/2 câu đúng = 50% chưa đủ mốc 100%
        assertEquals(5.5, bands.at("/overall").asDouble()); // (6.5 + 4.0) / 2 = 5.25 -> 5.5
        assertEquals(false, bands.at("/gradingPending").asBoolean());
    }

    @Test
    void cmsValidatesAndProtectsRules() throws Exception {
        var w = world();
        var admin = admin();
        var dup = Map.of("rules", List.of(rule("READING", 1, 5.0), rule("READING", 1, 6.0)));
        send(put("/api/admin/cms/banks/" + w.bank() + "/band-rules").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(dup)), admin.accessToken()).andExpect(status().isBadRequest());
        var badSkill = Map.of("rules", List.of(rule("FOO", 1, 5.0)));
        send(put("/api/admin/cms/banks/" + w.bank() + "/band-rules").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(badSkill)), admin.accessToken()).andExpect(status().isBadRequest());
        // đề đầu vào có bảng riêng; bộ không tồn tại
        long placement = insert("question_banks", Map.of("title", "Đầu vào", "status", "DRAFT", "mode", "EXAM", "purpose", "PLACEMENT",
                "time_limit_sec", 600));
        send(get("/api/admin/cms/banks/" + placement + "/band-rules"), admin.accessToken()).andExpect(status().isNotFound());
        send(get("/api/admin/cms/banks/999999/band-rules"), admin.accessToken()).andExpect(status().isNotFound());
        // học viên không có cms.content
        var student = register(newEmail(), PASSWORD);
        send(get("/api/admin/cms/banks/" + w.bank() + "/band-rules"), student.accessToken()).andExpect(status().isForbidden());
    }

    @Test
    void rejectsBandWithMoreThanOneDecimalAtThatRow() throws Exception {
        var w = world();
        var body = Map.of("rules", List.of(rule("READING", 1, 5.0), rule("READING", 2, 6.25)));
        send(put("/api/admin/cms/banks/" + w.bank() + "/band-rules").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)), admin().accessToken())
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                        org.hamcrest.Matchers.containsString("rules[1].band")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                        org.hamcrest.Matchers.containsString("1 chữ số thập phân")));
    }

    @Test
    void estimatedBandPrefersLatestExamOverPlacement() throws Exception {
        var w = world();
        var u = enrolled(w);
        long pb = insert("question_banks", Map.of("title", "Đầu vào", "status", "DRAFT", "mode", "EXAM", "purpose", "PLACEMENT",
                "time_limit_sec", 600));
        insert("placement_attempts", Map.of("bank_id", pb, "user_id", u.id(), "started_at", OffsetDateTime.now().minusDays(3),
                "time_limit_sec", 600, "deadline_at", OffsetDateTime.now().minusDays(3).plusMinutes(10),
                "submitted_at", OffsetDateTime.now().minusDays(3), "overall_band", 4.5));
        var url = "/api/me/courses/" + w.course() + "/exercise-stats";
        var before = json.readTree(send(get(url), u.accessToken()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("PLACEMENT", before.at("/band/source").asText());

        putRules(admin(), w.bank(), List.of(rule("READING", 0, 5.0), rule("READING", 100, 7.0), rule("LISTENING", 0, 5.0)));
        submitted(u, w, List.of(w.reading()[0], w.reading()[1]));
        var after = json.readTree(send(get(url), u.accessToken()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("EXAM", after.at("/band/source").asText());
        assertEquals(6.0, after.at("/band/value").asDouble()); // (7.0 + 5.0) / 2
    }
}
