package com.pps.smartenglish.catalog;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.support.ApiTestBase;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Một phần tử chỉ tới được học viên khi nó và mọi cấp cha đều PUBLISHED; DRAFT ở bất kỳ cấp nào cũng ẩn nội dung,
 * kể cả với người đã mua (enrollment), và hiện lại khi xuất bản.
 */
class ContentVisibilityTest extends ApiTestBase {

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private record Chain(String slug, long course, long level, long topic, long lesson, long bank, Account student) {}

    /** Chuỗi đầy đủ, mọi cấp PUBLISHED; topic KHÔNG học thử nên cần enrollment mới xem được. Bank gắn với bài học. */
    private Chain chain() throws Exception {
        String slug = "vis-" + UUID.randomUUID();
        long course = insert("courses", Map.of("slug", slug, "title", "Khóa", "status", "PUBLISHED"));
        long level = insert("course_levels", Map.of("course_id", course, "code", "A", "title", "Level A", "status", "PUBLISHED"));
        long topic = insert("topics", Map.of("level_id", level, "title", "Topic", "is_free_trial", false, "status", "PUBLISHED"));
        long lesson = insert("lessons", Map.of("topic_id", topic, "type", "CONNECTIVE_VIDEO", "title", "Bài", "status", "PUBLISHED",
                "video_source", "YOUTUBE", "video_ref", "dQw4w9WgXcQ", "duration_sec", 100));
        long bank = insert("question_banks", Map.of("topic_id", topic, "lesson_id", lesson, "title", "Bộ", "is_trial", false, "status", "PUBLISHED"));
        var student = register(newEmail(), "Password123!");
        jdbc.update("insert into user_course_enrollments (user_id, level_id, starts_at) values (?, ?, CURRENT_TIMESTAMP)", student.id(), level);
        return new Chain(slug, course, level, topic, lesson, bank, student);
    }

    private int lessonStatus(Chain c) throws Exception {
        return send(get("/api/lessons/" + c.lesson), c.student.accessToken()).andReturn().getResponse().getStatus();
    }

    private int bankStatus(Chain c) throws Exception {
        return send(get("/api/banks/" + c.bank), c.student.accessToken()).andReturn().getResponse().getStatus();
    }

    private int progressStatus(Chain c) throws Exception {
        var req = MockMvcRequestBuilders.post("/api/lessons/" + c.lesson + "/progress").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("positionSec", 1, "watchedDeltaSec", 1)));
        return send(req, c.student.accessToken()).andReturn().getResponse().getStatus();
    }

    private int startAttemptStatus(Chain c) throws Exception {
        var req = MockMvcRequestBuilders.post("/api/banks/" + c.bank + "/attempts");
        return send(req, c.student.accessToken()).andReturn().getResponse().getStatus();
    }

    private void set(String table, long id, String status) {
        jdbc.update("update " + table + " set status = ? where id = ?", status, id);
    }

    @Test
    void everythingPublishedIsVisibleToTheEnrolledStudent() throws Exception {
        var c = chain();
        org.junit.jupiter.api.Assertions.assertEquals(200, lessonStatus(c));
        org.junit.jupiter.api.Assertions.assertEquals(200, bankStatus(c));
        org.junit.jupiter.api.Assertions.assertEquals(200, progressStatus(c));
        org.junit.jupiter.api.Assertions.assertEquals(201, startAttemptStatus(c));
        mvc.perform(get("/api/catalog/courses/" + c.slug)).andExpect(status().isOk())
                .andExpect(jsonPath("$.levels", hasSize(1))).andExpect(jsonPath("$.levels[0].topics[0].lessons", hasSize(1)));
    }

    @Test
    void draftAtAnyAncestorHidesLessonAndBankEvenFromEnrolledStudentsAndComesBackWhenPublished() throws Exception {
        var c = chain();
        // table, id, bài học còn hiện khi cấp này DRAFT?, bộ câu hỏi còn hiện?
        Object[][] cases = {
                {"courses", c.course, false, false},
                {"course_levels", c.level, false, false},
                {"topics", c.topic, false, false},
                {"lessons", c.lesson, false, false}, // bài DRAFT cũng ẩn bộ câu hỏi gắn với bài đó
                {"question_banks", c.bank, true, false}};
        for (Object[] k : cases) {
            String table = (String) k[0];
            long id = (Long) k[1];
            set(table, id, "DRAFT");
            org.junit.jupiter.api.Assertions.assertEquals((boolean) k[2] ? 200 : 404, lessonStatus(c), table + " DRAFT: bài học");
            org.junit.jupiter.api.Assertions.assertEquals((boolean) k[3] ? 200 : 404, bankStatus(c), table + " DRAFT: bộ câu hỏi");
            if (!(boolean) k[3]) {
                org.junit.jupiter.api.Assertions.assertEquals(404, startAttemptStatus(c), table + " DRAFT: bắt đầu làm bài");
            }
            if (!(boolean) k[2]) {
                org.junit.jupiter.api.Assertions.assertEquals(404, progressStatus(c), table + " DRAFT: tiến độ");
            }
            set(table, id, "PUBLISHED");
            org.junit.jupiter.api.Assertions.assertEquals(200, lessonStatus(c), table + " PUBLISHED lại: bài học");
            org.junit.jupiter.api.Assertions.assertEquals(200, bankStatus(c), table + " PUBLISHED lại: bộ câu hỏi");
        }
    }

    @Test
    void catalogTreeSkipsDraftLevelsTopicsAndLessons() throws Exception {
        var c = chain();
        set("course_levels", c.level, "DRAFT");
        mvc.perform(get("/api/catalog/courses/" + c.slug)).andExpect(status().isOk()).andExpect(jsonPath("$.levels", hasSize(0)));
        set("course_levels", c.level, "PUBLISHED");
        set("topics", c.topic, "DRAFT");
        mvc.perform(get("/api/catalog/courses/" + c.slug)).andExpect(jsonPath("$.levels", hasSize(1)))
                .andExpect(jsonPath("$.levels[0].topics", hasSize(0)));
        set("topics", c.topic, "PUBLISHED");
        set("lessons", c.lesson, "DRAFT");
        mvc.perform(get("/api/catalog/courses/" + c.slug)).andExpect(jsonPath("$.levels[0].topics[0].lessons", hasSize(0)));
        set("lessons", c.lesson, "PUBLISHED");
        set("courses", c.course, "DRAFT");
        mvc.perform(get("/api/catalog/courses/" + c.slug)).andExpect(status().isNotFound());
        mvc.perform(get("/api/catalog/courses")).andExpect(jsonPath("$[?(@.slug=='" + c.slug + "')]", hasSize(0)));
        set("courses", c.course, "PUBLISHED");
        mvc.perform(get("/api/catalog/courses/" + c.slug)).andExpect(status().isOk()).andExpect(jsonPath("$.levels[0].topics[0].lessons", hasSize(1)));
    }

    @Test
    void existingAttemptsStayReadableWhenTheBankGoesBackToDraft() throws Exception {
        var c = chain();
        var attempt = send(MockMvcRequestBuilders.post("/api/banks/" + c.bank + "/attempts"), c.student.accessToken()).andReturn();
        long id = json.readTree(attempt.getResponse().getContentAsString()).get("id").asLong();
        set("question_banks", c.bank, "DRAFT");
        send(get("/api/attempts/" + id), c.student.accessToken()).andExpect(status().isOk());
    }
}
