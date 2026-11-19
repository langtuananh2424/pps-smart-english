package com.pps.smartenglish.lms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.support.ApiTestBase;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** FR-LMS-04, FR-EXAM-02, D-118, D-119, D-122: học tuần tự theo unit và bài kiểm tra chương. */
class SequentialLearningTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";

    private record Course(long id, long topicA, long topicB, long lessonA, long lessonB, long practiceA, long testA, long testB) {}

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private long lesson(long topic, String title) {
        var l = new HashMap<String, Object>(Map.of("topic_id", topic, "type", "CONNECTIVE_VIDEO", "title", title, "status", "PUBLISHED"));
        l.put("video_source", "YOUTUBE");
        l.put("video_ref", "abc");
        l.put("duration_sec", 60);
        return insert("lessons", l);
    }

    private long bank(long topic, String mode) {
        long b = insert("question_banks", Map.of("topic_id", topic, "title", mode + " " + topic, "is_trial", false, "status", "PUBLISHED", "mode", mode));
        insert("questions", Map.of("bank_id", b, "type", "CONNECTIVE_MCQ", "prompt", "Câu hỏi"));
        return b;
    }

    /** Khóa 2 unit: A có bài giảng, bài tập và kiểm tra chương; B có bài giảng và bộ kiểm tra chưa gắn. */
    private Course course() {
        long c = insert("courses", Map.of("slug", "seq-" + UUID.randomUUID(), "title", "Khóa tuần tự", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED", "sort_order", 0));
        long ta = insert("topics", Map.of("level_id", lv, "title", "Unit A", "is_free_trial", false, "status", "PUBLISHED", "sort_order", 0));
        long tb = insert("topics", Map.of("level_id", lv, "title", "Unit B", "is_free_trial", false, "status", "PUBLISHED", "sort_order", 1));
        return new Course(c, ta, tb, lesson(ta, "Bài A"), lesson(tb, "Bài B"), bank(ta, "PRACTICE"), bank(ta, "EXAM"), bank(tb, "EXAM"));
    }

    private Account student(Course c, boolean enroll) throws Exception {
        var u = register(newEmail(), PASSWORD);
        if (enroll) {
            jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) "
                    + "values (?, ?, 'ACTIVE', TIMESTAMP '2020-01-01 00:00:00', TIMESTAMP '2099-01-01 00:00:00')", u.id(), c.id());
        }
        return u;
    }

    private Account admin() throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = 'ADMIN'", u.id());
        return login(u.email(), PASSWORD);
    }

    private ResultActions patchJson(String url, Account a, String body) throws Exception {
        return send(patch(url).contentType(MediaType.APPLICATION_JSON).content(body), a.accessToken());
    }

    private JsonNode topicNode(Account u, Course c, long topicId) throws Exception {
        String body = send(get("/api/me/courses/" + c.id() + "/roadmap"), u.accessToken()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (JsonNode lv : json.readTree(body).get("levels")) {
            for (JsonNode t : lv.get("topics")) {
                if (t.get("id").asLong() == topicId) {
                    return t;
                }
            }
        }
        throw new AssertionError("Không thấy topic " + topicId);
    }

    private void submit(Account u, long bankId) {
        jdbc.update("insert into student_exercise_attempts (user_id, bank_id, submitted_at, score, max_score) "
                + "values (?, ?, CURRENT_TIMESTAMP, 0, 1)", u.id(), bankId);
    }

    @Test
    void sequentialCourseLocksNextUnitUntilLessonsExercisesAndChapterTestAreDone() throws Exception {
        var c = course();
        var admin = admin();
        var s = student(c, true);

        // chưa bật tuần tự: không nhảy cóc nào bị chặn
        send(get("/api/lessons/" + c.lessonB()), s.accessToken()).andExpect(status().isOk());

        patchJson("/api/admin/cms/topics/" + c.topicA(), admin, "{\"chapterTestBankId\":" + c.testA() + "}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.chapterTestBankId").value(c.testA()));
        patchJson("/api/admin/cms/courses/" + c.id(), admin, "{\"sequential\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.sequential").value(true));

        // unit A mở, unit B khóa
        send(get("/api/lessons/" + c.lessonA()), s.accessToken()).andExpect(status().isOk());
        send(get("/api/lessons/" + c.lessonB()), s.accessToken()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("unit_locked"))
                .andExpect(jsonPath("$.error.details.blockingTopicId").value(c.topicA()));
        assertEquals(false, topicNode(s, c, c.topicA()).get("locked").asBoolean());
        assertEquals("SEQUENTIAL", topicNode(s, c, c.topicB()).get("lockReason").asText());
        assertEquals("LOCKED", topicNode(s, c, c.topicB()).get("status").asText());

        // xem hết bài giảng: vẫn khóa vì còn bài tập và kiểm tra chương
        jdbc.update("insert into lesson_progress (user_id, lesson_id, watched_sec, completed_at) values (?, ?, 60, CURRENT_TIMESTAMP)", s.id(), c.lessonA());
        send(get("/api/lessons/" + c.lessonB()), s.accessToken()).andExpect(status().isForbidden());

        // nộp bài tập: vẫn khóa vì chưa nộp kiểm tra chương
        submit(s, c.practiceA());
        send(get("/api/lessons/" + c.lessonB()), s.accessToken()).andExpect(status().isForbidden());

        // nộp kiểm tra chương (không cần đạt điểm) thì mở
        submit(s, c.testA());
        send(get("/api/lessons/" + c.lessonB()), s.accessToken()).andExpect(status().isOk());
        assertEquals(false, topicNode(s, c, c.topicB()).get("locked").asBoolean());
    }

    @Test
    void chapterTestListsAttemptsAndHonoursRetakes() throws Exception {
        var c = course();
        var admin = admin();
        var s = student(c, true);
        patchJson("/api/admin/cms/topics/" + c.topicA(), admin, "{\"chapterTestBankId\":" + c.testA() + ",\"chapterTestRetakes\":1}").andExpect(status().isOk());

        send(get("/api/courses/" + c.id() + "/tests"), s.accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kind").value("CHAPTER")).andExpect(jsonPath("$[0].topicId").value(c.topicA()))
                .andExpect(jsonPath("$[0].unlocked").value(true)).andExpect(jsonPath("$[0].attemptsAllowed").value(2))
                .andExpect(jsonPath("$[1].kind").value("FINAL")).andExpect(jsonPath("$[1].reason").value("NOT_CONFIGURED"));

        submit(s, c.testA());
        send(get("/api/courses/" + c.id() + "/tests"), s.accessToken()).andExpect(jsonPath("$[0].attemptsUsed").value(1))
                .andExpect(jsonPath("$[0].unlocked").value(true));
        submit(s, c.testA());
        send(get("/api/courses/" + c.id() + "/tests"), s.accessToken()).andExpect(jsonPath("$[0].unlocked").value(false))
                .andExpect(jsonPath("$[0].reason").value("ATTEMPTS_EXHAUSTED"));

        send(MockMvcRequestBuilders.post("/api/banks/" + c.testA() + "/attempts"), s.accessToken())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("exam_attempts_exhausted"));
    }

    @Test
    void chapterTestNeedsEnrollmentAndAnUnlockedUnit() throws Exception {
        var c = course();
        var admin = admin();
        patchJson("/api/admin/cms/topics/" + c.topicB(), admin, "{\"chapterTestBankId\":" + c.testB() + "}").andExpect(status().isOk());
        patchJson("/api/admin/cms/courses/" + c.id(), admin, "{\"sequential\":true}").andExpect(status().isOk());

        var stranger = student(c, false);
        send(MockMvcRequestBuilders.post("/api/banks/" + c.testB() + "/attempts"), stranger.accessToken()).andExpect(status().isForbidden());

        // unit B còn khóa: kiểm tra chương của B khóa theo, báo qua danh sách và chặn bắt đầu
        var s = student(c, true);
        send(get("/api/courses/" + c.id() + "/tests"), s.accessToken()).andExpect(jsonPath("$[0].topicId").value(c.topicB()))
                .andExpect(jsonPath("$[0].reason").value("UNIT_LOCKED"));
        send(MockMvcRequestBuilders.post("/api/banks/" + c.testB() + "/attempts"), s.accessToken())
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("unit_locked"));
    }

    @Test
    void paywallAndCmsValidationStillApply() throws Exception {
        var c = course();
        var admin = admin();
        patchJson("/api/admin/cms/courses/" + c.id(), admin, "{\"sequential\":true}").andExpect(status().isOk());

        // chưa mua: vẫn là lesson_locked (quyền truy cập thường đi trước), không phải unit_locked
        var stranger = student(c, false);
        send(get("/api/lessons/" + c.lessonB()), stranger.accessToken()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("lesson_locked"));

        // kiểm tra chương phải là bộ EXAM của chính topic
        patchJson("/api/admin/cms/topics/" + c.topicB(), admin, "{\"chapterTestBankId\":" + c.testA() + "}").andExpect(status().isBadRequest());
        patchJson("/api/admin/cms/topics/" + c.topicA(), admin, "{\"chapterTestBankId\":" + c.practiceA() + "}").andExpect(status().isBadRequest());
        patchJson("/api/admin/cms/topics/" + c.topicA(), admin, "{\"chapterTestBankId\":" + c.testA() + "}").andExpect(status().isOk());
        patchJson("/api/admin/cms/topics/" + c.topicA(), admin, "{\"clearChapterTest\":true}").andExpect(status().isOk())
                .andExpect(jsonPath("$.chapterTestBankId").doesNotExist());
        patchJson("/api/admin/cms/topics/" + c.topicA(), admin, "{\"clearChapterTest\":true,\"chapterTestBankId\":" + c.testA() + "}")
                .andExpect(status().isBadRequest());
    }
}
