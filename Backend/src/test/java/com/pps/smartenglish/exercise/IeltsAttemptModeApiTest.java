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

/** D-155 (thay D-151 mục 1-2), FR-EX-05, FR-AI-06: chế độ lượt theo bộ, học viên không chọn; hành vi của lượt EXAM/PRACTICE giữ như D-151. */
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

    private JsonNode progress(Account u, long attempt) throws Exception {
        return json.readTree(send(get("/api/attempts/" + attempt + "/progress"), u.accessToken()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void examAttemptHasNoPauseAndNoHints_practiceHas() throws Exception {
        var u = register(newEmail(), PASSWORD);
        long examBank = bank("READING", "PRACTICE");
        long practiceBank = bank("READING", "PRACTICE");
        var exam = start(u, examBank, null, 201);
        long examId = exam.path("id").asLong();
        jdbc.update("update student_exercise_attempts set mode = 'EXAM' where id = ?", examId); // như bài giao Thi
        assertEquals("EXAM", progress(u, examId).path("mode").asText());
        expectStatus(u, "POST", "/api/attempts/" + examId + "/pause", 409);
        expectStatus(u, "GET", "/api/attempts/" + examId + "/hints", 403);

        long id = start(u, practiceBank, null, 201).path("id").asLong();
        expectStatus(u, "GET", "/api/attempts/" + id + "/hints", 200);
        expectStatus(u, "POST", "/api/attempts/" + id + "/pause", 200);
    }

    @Test
    void retryCheckFollowsAttemptMode() throws Exception {
        var u = register(newEmail(), PASSWORD);
        String url = "/api/attempts/%d/questions/%d/check";
        var examBank = bank("READING", "PRACTICE");
        var practiceBank = bank("READING", "PRACTICE");
        var exam = start(u, examBank, null, 201).path("id").asLong();
        jdbc.update("update student_exercise_attempts set mode = 'EXAM' where id = ?", exam); // như bài giao Thi
        var practice = start(u, practiceBank, null, 201).path("id").asLong();
        long eq = jdbc.queryForObject("select id from questions where bank_id = ?", Long.class, examBank);
        long pq = jdbc.queryForObject("select id from questions where bank_id = ?", Long.class, practiceBank);
        long ec = insert("question_choices", Map.of("question_id", eq, "label", "A", "content", "x", "is_correct", true));
        long pc = insert("question_choices", Map.of("question_id", pq, "label", "A", "content", "x", "is_correct", true));
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url.formatted(exam, eq)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("answer", Map.of("choiceId", ec)))), u.accessToken()).andExpect(status().isConflict());
        assertTrue(progress(u, exam).path("retry").isMissingNode());
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url.formatted(practice, pq)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("answer", Map.of("choiceId", pc)))), u.accessToken()).andExpect(status().isOk());
    }

    @Test
    void modeInBodyIsIgnoredAndStartIsImmediate() throws Exception {
        var u = register(newEmail(), PASSWORD);
        long practiceBank = bank("READING", "PRACTICE");
        // client cũ vẫn gửi mode: bỏ qua, chế độ theo bộ; kể cả giá trị lạ
        assertEquals("PRACTICE", start(u, practiceBank, "{\"mode\":\"EXAM\"}", 201).path("mode").asText());
        start(u, practiceBank, "{\"mode\":\"X\"}", 409); // đã có lượt mở (D-147)
        long other = bank(null, "PRACTICE"); // mọi bộ theo cùng quy tắc, không riêng IELTS
        assertEquals("PRACTICE", start(u, other, "{\"mode\":\"EXAM\"}", 201).path("mode").asText());
    }

    @Test
    void legacyAttemptWithoutModeFollowsBank() throws Exception {
        var u = register(newEmail(), PASSWORD);
        long b = bank("READING", "PRACTICE");
        long a = insert("student_exercise_attempts", Map.of("user_id", u.id(), "bank_id", b, "started_at", java.time.OffsetDateTime.now(), "time_limit_sec", 600));
        expectStatus(u, "POST", "/api/attempts/" + a + "/pause", 200);
        assertEquals("PRACTICE", progress(u, a).path("mode").asText());
    }
}
