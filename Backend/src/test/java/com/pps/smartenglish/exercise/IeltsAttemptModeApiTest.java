package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.support.ApiTestBase;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;

/** D-151, FR-EX-05, FR-AI-06: chế độ Luyện tập/Mô phỏng thi chọn lúc bắt đầu lượt của bộ IELTS Reading/Listening. */
class IeltsAttemptModeApiTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    /** Bộ học thử có 1 câu và 1 gợi ý, giới hạn 600s; skill topic tùy ý. */
    private long bank(String skill, String mode) {
        long c = insert("courses", Map.of("slug", "im-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED"));
        var topic = new HashMap<String, Object>(Map.of("level_id", lv, "title", "Topic", "is_free_trial", true, "status", "PUBLISHED"));
        if (skill != null) topic.put("skill", skill);
        long t = insert("topics", topic);
        long b = insert("question_banks", Map.of("topic_id", t, "title", "Bộ", "is_trial", true, "status", "PUBLISHED",
                "mode", mode, "time_limit_sec", 600));
        long q = insert("questions", Map.of("bank_id", b, "type", "CONNECTIVE_MCQ", "prompt", "Câu"));
        insert("question_hints", Map.of("question_id", q, "content", "Gợi ý", "sort_order", 0));
        return b;
    }

    private JsonNode start(Account u, long bank, String body, int expected) throws Exception {
        var req = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/banks/" + bank + "/attempts");
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(body);
        return json.readTree(send(req, u.accessToken()).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    private void expectStatus(Account u, String method, String url, int expected) throws Exception {
        send("POST".equals(method) ? org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url) : get(url), u.accessToken()).andExpect(status().is(expected));
    }

    @Test
    void examAttemptHasNoPauseAndNoHints_practiceHas() throws Exception {
        var u = register(newEmail(), PASSWORD);
        long b = bank("READING", "PRACTICE");
        var bankJson = json.readTree(send(get("/api/banks/" + b), u.accessToken()).andReturn().getResponse().getContentAsString());
        assertTrue(bankJson.path("questions").get(0).path("hints").isMissingNode()
                || bankJson.path("questions").get(0).path("hints").isEmpty()); // bộ chọn được chế độ không trả gợi ý ở bank

        var exam = start(u, b, "{\"mode\":\"EXAM\"}", 201);
        assertEquals("EXAM", exam.path("mode").asText());
        long examId = exam.path("id").asLong();
        expectStatus(u, "POST", "/api/attempts/" + examId + "/pause", 409);
        expectStatus(u, "GET", "/api/attempts/" + examId + "/hints", 403);
        assertEquals("EXAM", json.readTree(send(get("/api/attempts/" + examId + "/progress"), u.accessToken()).andReturn()
                .getResponse().getContentAsString()).path("mode").asText());
        jdbc.update("update student_exercise_attempts set submitted_at = now() where id = ?", examId);

        var practice = start(u, b, "{\"mode\":\"PRACTICE\"}", 201);
        long id = practice.path("id").asLong();
        expectStatus(u, "GET", "/api/attempts/" + id + "/hints", 200);
        expectStatus(u, "POST", "/api/attempts/" + id + "/pause", 200);
    }

    @Test
    void defaultsToBankModeAndRejectsBadModes() throws Exception {
        var u = register(newEmail(), PASSWORD);
        long ielts = bank("LISTENING", "PRACTICE");
        assertEquals("PRACTICE", start(u, ielts, null, 201).path("mode").asText()); // không body: theo bộ
        start(u, ielts, "{\"mode\":\"EXAM\"}", 409); // D-147: đã có lượt mở, tính chung hai chế độ
        long other = bank(null, "PRACTICE"); // ngoài phạm vi: mode khác bank.mode bị từ chối
        assertEquals("attempt_mode_not_allowed", start(u, other, "{\"mode\":\"EXAM\"}", 400).path("error").path("code").asText());
        assertEquals("PRACTICE", start(u, other, "{\"mode\":\"PRACTICE\"}", 201).path("mode").asText());
        long v = bank("READING", "PRACTICE");
        assertEquals("attempt_mode_invalid", start(u, v, "{\"mode\":\"X\"}", 400).path("error").path("code").asText());
    }

    @Test
    void legacyAttemptWithoutModeFollowsBank() throws Exception {
        var u = register(newEmail(), PASSWORD);
        long b = bank("READING", "PRACTICE");
        long a = insert("student_exercise_attempts", Map.of("user_id", u.id(), "bank_id", b, "started_at", java.time.OffsetDateTime.now(), "time_limit_sec", 600));
        expectStatus(u, "POST", "/api/attempts/" + a + "/pause", 200);
        assertEquals("PRACTICE", json.readTree(send(get("/api/attempts/" + a + "/progress"), u.accessToken()).andReturn()
                .getResponse().getContentAsString()).path("mode").asText());
    }
}
