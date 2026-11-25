package com.pps.smartenglish.lms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.support.ApiTestBase;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;

/** Tester #63/#26: 403 unit_locked không lộ dữ liệu; học thử không thi được kiểm tra chương; đường dẫn id lạ của gate. */
class UnitGateTesterTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";

    private record Fx(long course, long topicA, long topicB, long lessonB, long bankB, long chapterTestA) {}

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    /** Khóa tuần tự: unit A học thử (có bài kiểm tra chương), unit B có bài giảng bí mật và bộ PRACTICE. */
    private Fx fx() {
        long c = insert("courses", Map.of("slug", "gt-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED", "sequential", true));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level", "status", "PUBLISHED", "sort_order", 0));
        long ta = insert("topics", Map.of("level_id", lv, "title", "Unit A", "is_free_trial", true, "status", "PUBLISHED", "sort_order", 0));
        long tb = insert("topics", Map.of("level_id", lv, "title", "Unit B", "is_free_trial", false, "status", "PUBLISHED", "sort_order", 1));
        insert("lessons", Map.of("topic_id", ta, "type", "CONNECTIVE_VIDEO", "title", "A1", "status", "PUBLISHED", "video_source", "YOUTUBE",
                "video_ref", "aaa", "duration_sec", 60, "sort_order", 0));
        long lb = insert("lessons", Map.of("topic_id", tb, "type", "CONNECTIVE_VIDEO", "title", "BAI-B-BI-MAT", "status", "PUBLISHED",
                "video_source", "YOUTUBE", "video_ref", "VIDEO-B-BI-MAT", "duration_sec", 60, "sort_order", 0));
        long bb = insert("question_banks", Map.of("topic_id", tb, "title", "Bo B", "is_trial", false, "status", "PUBLISHED", "mode", "PRACTICE"));
        insert("questions", Map.of("bank_id", bb, "type", "CONNECTIVE_MCQ", "prompt", "CAU-B-BI-MAT"));
        long test = insert("question_banks", Map.of("topic_id", ta, "title", "KT chương A", "is_trial", true, "status", "PUBLISHED", "mode", "EXAM"));
        insert("questions", Map.of("bank_id", test, "type", "CONNECTIVE_MCQ", "prompt", "Câu thi"));
        jdbc.update("update topics set chapter_test_bank_id = ? where id = ?", test, ta);
        return new Fx(c, ta, tb, lb, bb, test);
    }

    private Account enrolled(Fx f) throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at) values (?, ?, 'ACTIVE', ?)",
                u.id(), f.course(), OffsetDateTime.parse("2020-01-01T00:00:00Z"));
        return u;
    }

    private void assertNoSecret(String body) {
        for (String s : Set.of("BAI-B-BI-MAT", "VIDEO-B-BI-MAT", "CAU-B-BI-MAT")) {
            assertFalse(body.contains(s), "403 lộ " + s + ": " + body);
        }
    }

    @Test
    void unitLockedErrorCarriesOnlyIds() throws Exception {
        var f = fx();
        var u = enrolled(f);
        var lesson = send(get("/api/lessons/" + f.lessonB()), u.accessToken()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("unit_locked")).andReturn().getResponse().getContentAsString();
        assertNoSecret(lesson);
        JsonNode d = json.readTree(lesson).at("/error/details");
        assertEquals(f.topicA(), d.get("blockingTopicId").asLong());
        assertEquals(f.course(), d.get("courseId").asLong());
        assertEquals(f.topicB(), d.get("topicId").asLong());
        assertEquals(Set.of("blockingTopicId", "courseId", "topicId"), Set.copyOf(iter(d)));

        var bank = send(get("/api/banks/" + f.bankB()), u.accessToken()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("unit_locked")).andReturn().getResponse().getContentAsString();
        assertNoSecret(bank);
        var start = send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/banks/" + f.bankB() + "/attempts").contentType(MediaType.APPLICATION_JSON).content("{}"), u.accessToken())
                .andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString();
        assertNoSecret(start);
    }

    private static java.util.List<String> iter(JsonNode n) {
        var out = new java.util.ArrayList<String>();
        n.fieldNames().forEachRemaining(out::add);
        return out;
    }

    /** Chưa mua (đang học thử) thì không bắt đầu được kiểm tra chương dù unit là học thử và bộ có cờ is_trial. */
    @Test
    void trialUserCannotStartChapterTest() throws Exception {
        var f = fx();
        var guest = register(newEmail(), PASSWORD);
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/banks/" + f.chapterTestA() + "/attempts").contentType(MediaType.APPLICATION_JSON).content("{}"), guest.accessToken())
                .andExpect(status().isForbidden());
        assertEquals(0, count("select count(*) from student_exercise_attempts where user_id = ?", guest.id()));
        // danh sách kiểm tra của người chưa mua: mọi bài đều ENROLLMENT_REQUIRED, không mở
        var tests = json.readTree(send(get("/api/courses/" + f.course() + "/tests"), guest.accessToken()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        for (JsonNode t : tests) {
            if (!"CHAPTER".equals(t.get("kind").asText())) {
                continue;
            }
            assertEquals(false, t.get("unlocked").asBoolean(), t.toString());
            assertEquals("ENROLLMENT_REQUIRED", t.get("reason").asText(), t.toString());
        }
    }

    /** Gate với id sai định dạng hay quá lớn: 404, không 400/500 lộ chi tiết. */
    @Test
    void malformedIdsAre404() throws Exception {
        var f = fx();
        var u = enrolled(f);
        for (String[] p : new String[][] {{"abc", String.valueOf(f.topicA())}, {String.valueOf(f.course()), "1e9"},
                {"99999999999999999999999", "1"}, {String.valueOf(f.course()), "-1"}}) {
            int code = send(get("/api/me/courses/" + p[0] + "/units/" + p[1] + "/gate"), u.accessToken()).andReturn().getResponse().getStatus();
            assertEquals(404, code, p[0] + "/" + p[1]);
        }
    }

    /** Gate chỉ phản ánh tiến độ của chính người gọi: tiến độ của học viên khác không làm mở unit của mình. */
    @Test
    void gateIsPerCaller() throws Exception {
        var f = fx();
        var a = enrolled(f);
        var b = enrolled(f);
        jdbc.update("insert into lesson_progress (user_id, lesson_id, watched_sec, max_position_sec, completed_at) "
                + "select ?, id, 60, 60, CURRENT_TIMESTAMP from lessons where topic_id = ?", a.id(), f.topicA());
        jdbc.update("insert into student_exercise_attempts (user_id, bank_id, submitted_at, score, max_score) values (?, ?, CURRENT_TIMESTAMP, 1, 1)",
                a.id(), f.chapterTestA());
        String url = "/api/me/courses/" + f.course() + "/units/" + f.topicB() + "/gate";
        send(get(url), a.accessToken()).andExpect(status().isOk()).andExpect(jsonPath("$.locked").value(false));
        send(get(url), b.accessToken()).andExpect(status().isOk()).andExpect(jsonPath("$.locked").value(true))
                .andExpect(jsonPath("$.lectures.completed").value(0)).andExpect(jsonPath("$.chapterTest.submitted").value(false));
    }
}
