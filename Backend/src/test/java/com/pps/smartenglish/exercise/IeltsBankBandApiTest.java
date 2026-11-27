package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.support.ApiTestBase;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;

/** FR-EX-05, FR-EXAM-03: BankResponse.skill (D-58) và AttemptResponse.bands của lượt luyện tập đã nộp (D-147 ẩn khi còn câu AI). */
class IeltsBankBandApiTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    /** Bộ học thử (ai đăng nhập cũng xem được) trong topic có kỹ năng skill (null = không gắn). */
    private long bank(String skill) {
        long c = insert("courses", Map.of("slug", "ib-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED"));
        var topic = new java.util.HashMap<String, Object>(Map.of("level_id", lv, "title", "Topic", "is_free_trial", true, "status", "PUBLISHED"));
        if (skill != null) topic.put("skill", skill);
        long t = insert("topics", topic);
        return insert("question_banks", Map.of("topic_id", t, "title", "Reading 1", "is_trial", true, "status", "PUBLISHED"));
    }

    private long question(long bank, String type) {
        return insert("questions", Map.of("bank_id", bank, "type", type, "prompt", "Câu", "skill", "READING"));
    }

    /** Lượt đã nộp: câu đầu đúng; câu viết (nếu có) chưa có credit. */
    private long attempt(Account u, long bank, long right, Long pendingWriting) {
        long a = insert("student_exercise_attempts", Map.of("user_id", u.id(), "bank_id", bank, "submitted_at", OffsetDateTime.now(),
                "score", 1, "max_score", 2));
        insert("student_answers", Map.of("attempt_id", a, "question_id", right, "is_correct", true, "credit", 1));
        if (pendingWriting != null) insert("student_answers", Map.of("attempt_id", a, "question_id", pendingWriting));
        return a;
    }

    private JsonNode get200(String url, Account u) throws Exception {
        return json.readTree(send(get(url), u.accessToken()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void bankExposesTopicSkillOnlyWhenSet() throws Exception {
        var u = register(newEmail(), PASSWORD);
        assertEquals("READING", get200("/api/banks/" + bank("READING"), u).path("skill").asText());
        assertTrue(get200("/api/banks/" + bank(null), u).path("skill").isMissingNode());
    }

    @Test
    void attemptBandsFollowRulesAndHideWhileAiPending() throws Exception {
        var u = register(newEmail(), PASSWORD);
        long b = bank("READING");
        long q1 = question(b, "CONNECTIVE_MCQ");
        long q2 = question(b, "CONNECTIVE_MCQ");
        long a = attempt(u, b, q1, null);
        assertTrue(get200("/api/attempts/" + a, u).path("bands").isMissingNode()); // chưa có bảng quy đổi
        jdbc.update("insert into exam_band_rules (bank_id, skill, min_percent, band) values (?, 'READING', 0, 4.0), (?, 'READING', 50, 6.5)", b, b);
        var bands = get200("/api/attempts/" + a, u).path("bands");
        assertEquals(6.5, bands.path("skills").path("READING").path("band").asDouble());
        assertEquals(2, bands.path("skills").path("READING").path("total").asInt());

        long w = question(b, "WRITING");
        long pending = attempt(u, b, q2, w);
        var res = get200("/api/attempts/" + pending, u);
        assertTrue(res.path("bands").isMissingNode()); // D-147: còn câu AI chưa chốt thì không có band tạm
        assertTrue(res.path("score").isMissingNode());
    }
}
