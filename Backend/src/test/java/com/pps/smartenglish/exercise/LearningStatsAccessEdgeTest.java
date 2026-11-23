package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.ai.AiTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Tester đợt #68 (FR-EX-08/09, NFR-SEC-05): ca biên phân quyền của 3 endpoint thống kê, timeSpentMs qua nháp và đầu vào lạ.
 * Bổ sung cho LearningStatsApiTest, không lặp lại các ca đã có ở đó.
 */
class LearningStatsAccessEdgeTest extends AiTestSupport {

    private record World(long course, long bank, long q, long right, long wrong) {}

    private World world(Integer timeLimitSec) {
        long c = insert("courses", Map.of("slug", "lse-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "A", "status", "PUBLISHED"));
        long t = insert("topics", Map.of("level_id", lv, "title", "T", "is_free_trial", true, "status", "PUBLISHED"));
        var cols = new HashMap<String, Object>(Map.of("topic_id", t, "title", "Bộ", "is_trial", false, "status", "PUBLISHED"));
        if (timeLimitSec != null) {
            cols.put("time_limit_sec", timeLimitSec);
        }
        long b = insert("question_banks", cols);
        long q = question(b, "CONNECTIVE_MCQ", 1, "Câu", "{}", "{}");
        long right = insert("question_choices", Map.of("question_id", q, "label", "B", "content", "đúng", "is_correct", true));
        long wrong = insert("question_choices", Map.of("question_id", q, "label", "A", "content", "sai", "is_correct", false));
        return new World(c, b, q, right, wrong);
    }

    private Account withRole(String role) throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = ?", u.id(), role);
        return login(u.email(), PASSWORD);
    }

    private long newClass(long course) {
        return insert("classes", Map.of("course_id", course, "name", "Lớp " + UUID.randomUUID().toString().substring(0, 4),
                "starts_on", LocalDate.now().minusDays(30), "ends_on", LocalDate.now().plusDays(60), "mode", "ONLINE", "capacity", 20));
    }

    private void staff(long cls, Account teacher, LocalDate from, LocalDate to) {
        jdbc.update("insert into class_staff (class_id, user_id, role, assigned_from, assigned_to) values (?, ?, 'TEACHER', ?, ?)",
                cls, teacher.id(), from, to);
    }

    private void member(long cls, long course, Account s, String status) {
        jdbc.update("insert into class_members (class_id, course_id, user_id, status, live_key, joined_at) values (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                cls, course, s.id(), status, "ACTIVE".equals(status) ? course + ":" + s.id() : null);
    }

    private String url(long cls, long user) {
        return "/api/classes/" + cls + "/students/" + user + "/learning-profile";
    }

    @Test
    void statsEndpointsRequireLogin() throws Exception {
        var w = world(null);
        long cls = newClass(w.course());
        mvc.perform(get("/api/me/learning-profile")).andExpect(status().isUnauthorized());
        mvc.perform(get(url(cls, 1))).andExpect(status().isUnauthorized());
    }

    @Test
    void classProfileFollowsStaffAssignmentAndMemberStatus() throws Exception {
        var w = world(null);
        long cls = newClass(w.course());
        var current = withRole("TEACHER");
        var expired = withRole("TEACHER");
        var future = withRole("TEACHER");
        staff(cls, current, LocalDate.now().minusDays(30), null);
        staff(cls, expired, LocalDate.now().minusDays(30), LocalDate.now().minusDays(1));
        staff(cls, future, LocalDate.now().plusDays(5), null);
        var active = student(0);
        var completed = student(0);
        var withdrawn = student(0);
        var transferred = student(0);
        member(cls, w.course(), active, "ACTIVE");
        member(cls, w.course(), completed, "COMPLETED");
        member(cls, w.course(), withdrawn, "WITHDRAWN");
        member(cls, w.course(), transferred, "TRANSFERRED");

        getJson(url(cls, active.id()), current).andExpect(status().isOk());
        getJson(url(cls, completed.id()), current).andExpect(status().isOk());
        getJson(url(cls, withdrawn.id()), current).andExpect(status().isNotFound());
        getJson(url(cls, transferred.id()), current).andExpect(status().isNotFound());
        getJson(url(cls, current.id()), current).andExpect(status().isNotFound()); // nhân sự không phải học viên của lớp
        getJson(url(cls, active.id()), expired).andExpect(status().isNotFound()); // hết phân công
        getJson(url(cls, active.id()), future).andExpect(status().isNotFound()); // chưa đến ngày phân công
        // học viên cùng lớp không xem được hồ sơ bạn học
        getJson(url(cls, completed.id()), login(active.email(), PASSWORD)).andExpect(status().isNotFound());
    }

    @Test
    void trainingManagerSeesAnyClassButStillOnlyItsStudents() throws Exception {
        var w = world(null);
        long cls = newClass(w.course());
        var mgr = withRole("TRAINING_MANAGER");
        var s = student(0);
        var outsider = student(0);
        member(cls, w.course(), s, "ACTIVE");
        getJson(url(cls, s.id()), mgr).andExpect(status().isOk());
        getJson(url(cls, outsider.id()), mgr).andExpect(status().isNotFound());
    }

    @Test
    void malformedIdsAre404NotServerError() throws Exception {
        var t = withRole("TEACHER");
        for (String u : List.of("/api/classes/abc/students/1/learning-profile", "/api/classes/1/students/abc/learning-profile",
                "/api/classes/-1/students/1/learning-profile", "/api/classes/99999999999999999999/students/1/learning-profile")) {
            getJson(u, t).andExpect(status().isNotFound());
        }
    }

    @Test
    void breakdownOnlyOnSubmittedAttemptAndNeverInSubmitResponse() throws Exception {
        var w = world(null);
        var u = student(0);
        long id = idOf(postJson("/api/banks/" + w.bank() + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
        getJson("/api/attempts/" + id, u).andExpect(status().isOk()).andExpect(jsonPath("$.breakdown").doesNotExist())
                .andExpect(jsonPath("$.results").doesNotExist());
        postJson("/api/attempts/" + id + "/submit", u, Map.of("answers", List.of(Map.of("questionId", w.q(),
                "answer", Map.of("choiceId", w.wrong()), "timeSpentMs", 1500)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.breakdown").doesNotExist());
        getJson("/api/attempts/" + id, u).andExpect(status().isOk()).andExpect(jsonPath("$.breakdown.wrong").value(1));
    }

    @Test
    void timeSpentFromDraftIsKeptWhenServerAutoSubmits() throws Exception {
        var w = world(60);
        var u = student(0);
        long id = idOf(postJson("/api/banks/" + w.bank() + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
        send(put("/api/attempts/" + id + "/draft").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                Map.of("answers", List.of(Map.of("questionId", w.q(), "answer", Map.of("choiceId", w.right()), "timeSpentMs", 4200))))),
                u.accessToken()).andExpect(status().isOk());
        // hết giờ: máy chủ nộp bằng nháp, bỏ qua đáp án và thời gian gửi lên lúc này
        jdbc.update("update student_exercise_attempts set resumed_at = ? where id = ?",
                Timestamp.from(Instant.now().minus(2, ChronoUnit.HOURS)), id);
        postJson("/api/attempts/" + id + "/submit", u, Map.of("answers", List.of(Map.of("questionId", w.q(),
                "answer", Map.of("choiceId", w.wrong()), "timeSpentMs", 9)))).andExpect(status().isOk());
        assertEquals(4200, count("select time_spent_ms from student_answers where attempt_id = ?", id));
        assertEquals(1, count("select count(*) from student_answers where attempt_id = ? and is_correct = true", id));
    }

    @Test
    void malformedTimeSpentIsClientErrorAndDoesNotSubmit() throws Exception {
        var w = world(null);
        var u = student(0);
        long id = idOf(postJson("/api/banks/" + w.bank() + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
        for (Object bad : List.of("abc", Map.of("x", 1), new java.math.BigInteger("99999999999999999999999"))) {
            var answer = new HashMap<String, Object>(Map.of("questionId", w.q(), "answer", Map.of("choiceId", w.right())));
            answer.put("timeSpentMs", bad);
            postJson("/api/attempts/" + id + "/submit", u, Map.of("answers", List.of(answer))).andExpect(status().isBadRequest());
        }
        assertNull(jdbc.queryForObject("select submitted_at from student_exercise_attempts where id = ?", Timestamp.class, id));
        // Long.MAX_VALUE hợp lệ về kiểu nhưng phi lý: bỏ, vẫn nộp được
        postJson("/api/attempts/" + id + "/submit", u, Map.of("answers", List.of(Map.of("questionId", w.q(),
                "answer", Map.of("choiceId", w.right()), "timeSpentMs", Long.MAX_VALUE)))).andExpect(status().isOk());
        assertNull(jdbc.queryForObject("select time_spent_ms from student_answers where attempt_id = ?", Integer.class, id));
    }

    @Test
    void profileOfOneStudentNeverCountsAnotherStudentsAnswers() throws Exception {
        var w = world(null);
        var a = student(0);
        var b = student(0);
        for (var u : List.of(a, b, b)) {
            long id = idOf(postJson("/api/banks/" + w.bank() + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
            postJson("/api/attempts/" + id + "/submit", u, Map.of("answers", List.of(Map.of("questionId", w.q(),
                    "answer", Map.of("choiceId", u == a ? w.right() : w.wrong()))))).andExpect(status().isOk());
        }
        JsonNode p = json.readTree(getJson("/api/me/learning-profile", a).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals(1, p.get("answered").asInt());
        assertEquals(100, p.get("accuracy").asInt());
        assertFalse(p.toString().contains("\"email\""));
    }
}
