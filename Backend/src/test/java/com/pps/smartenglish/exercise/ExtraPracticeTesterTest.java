package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.ai.AiTestSupport;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Tester đợt #32 (FR-EX-07, D-152): phân quyền theo bản ghi, khóa hết hạn, bộ neo EXTRA không rò ra nơi khác,
 * đồng thời, trừ lượt AI đúng ví khóa, Cam/thống kê không vỡ khi có lượt Luyện thêm.
 */
class ExtraPracticeTesterTest extends AiTestSupport {

    private static final String MCQ = "CONNECTIVE_MCQ";

    private long course;
    private long level;

    private long newCourse(boolean sequential) {
        course = insert("courses", Map.of("slug", "ext-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED", "sequential", sequential));
        level = insert("course_levels", Map.of("course_id", course, "code", "A", "title", "Level A", "status", "PUBLISHED"));
        return course;
    }

    private long topic(String title, int order, boolean trial) {
        return insert("topics", Map.of("level_id", level, "title", title, "sort_order", order, "is_free_trial", trial, "status", "PUBLISHED"));
    }

    private long bank(long topic, String mode, String status) {
        return insert("question_banks", Map.of("topic_id", topic, "title", "Bộ", "is_trial", false, "status", status, "mode", mode));
    }

    private List<Long> questions(long bank, int n, String type) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ids.add(question(bank, type, i + 1, "Đề " + i, "{}", "{}"));
        }
        return ids;
    }

    private long topicWithQuestions(String title, int order, int n) {
        long t = topic(title, order, false);
        questions(bank(t, "PRACTICE", "PUBLISHED"), n, MCQ);
        return t;
    }

    private void enroll(Account u, OffsetDateTime expiresAt) {
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', ?, ?)",
                u.id(), course, OffsetDateTime.parse("2020-01-01T00:00:00Z"), expiresAt);
    }

    private void complete(Account u, long topic) {
        long bank = jdbc.queryForObject("select id from question_banks where topic_id = ? order by id limit 1", Long.class, topic);
        long q = jdbc.queryForObject("select id from questions where bank_id = ? order by id limit 1", Long.class, bank);
        long attempt = insert("student_exercise_attempts", Map.of("user_id", u.id(), "bank_id", bank,
                "started_at", OffsetDateTime.now(), "submitted_at", OffsetDateTime.now(), "score", 1, "max_score", 1));
        insert("student_answers", Map.of("attempt_id", attempt, "question_id", q, "is_correct", true, "credit", 1));
    }

    private ResultActions start(Account u, Map<String, Object> body) throws Exception {
        return postJson("/api/me/courses/" + course + "/extra-practice/attempts", u, body);
    }

    private long startOk(Account u, Map<String, Object> body) throws Exception {
        return idOf(start(u, body).andExpect(status().isCreated()), "id");
    }

    private Set<Long> attemptQuestions(long attempt) {
        return new HashSet<>(jdbc.queryForList("select question_id from attempt_questions where attempt_id = ?", Long.class, attempt));
    }

    private Account withRole(String role) throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = ?", u.id(), role);
        return login(u.email(), PASSWORD);
    }

    private long anchor() {
        return jdbc.queryForObject("select id from question_banks where purpose = 'EXTRA' order by id limit 1", Long.class);
    }

    // ---------- phân quyền theo bản ghi ----------

    @Test
    void expiredEnrollmentHidesCompletedTopicAndBlocksBothScopes() throws Exception {
        newCourse(false);
        var u = student(0);
        enroll(u, OffsetDateTime.parse("2021-01-01T00:00:00Z")); // đã hết hạn
        long done = topicWithQuestions("Đã học", 1, 20);
        complete(u, done);
        var overview = json.readTree(getJson("/api/me/courses/" + course + "/extra-practice", u).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals(0, overview.get("review").size(), "khóa hết hạn không còn Ôn lại");
        start(u, Map.of("scope", "CHALLENGE", "topicId", done)).andExpect(status().isForbidden());
        start(u, Map.of("scope", "REVIEW", "topicId", done)).andExpect(status().isForbidden());
        start(u, Map.of("scope", "REVIEW", "kind", "AUTO")).andExpect(status().isConflict());
        assertEquals(0, count("select count(*) from student_exercise_attempts where user_id = ? and extra = true", u.id()));
    }

    @Test
    void topicOfAnotherCourseUnknownOrUnpublishedCourseAndAnonymousAreRejected() throws Exception {
        newCourse(false);
        long foreignTopic = topicWithQuestions("Khóa khác", 1, 10);
        long foreignCourse = course;
        newCourse(false);
        topicWithQuestions("Khóa này", 1, 10);
        var u = student(0);
        enroll(u, null);
        // đã mua khóa này nhưng gửi topic của khóa khác (kể cả khi đã mua khóa kia)
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', ?, null)",
                u.id(), foreignCourse, OffsetDateTime.parse("2020-01-01T00:00:00Z"));
        start(u, Map.of("scope", "CHALLENGE", "topicId", foreignTopic)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("extra_topic_not_available"));
        getJson("/api/me/courses/999999999/extra-practice", u).andExpect(status().isNotFound());
        getJson("/api/me/courses/abc/extra-practice", u).andExpect(status().isNotFound());
        postJson("/api/me/courses/999999999/extra-practice/attempts", u, Map.of("scope", "REVIEW")).andExpect(status().isNotFound());
        jdbc.update("update courses set status = 'DRAFT' where id = ?", course);
        getJson("/api/me/courses/" + course + "/extra-practice", u).andExpect(status().isNotFound());
        jdbc.update("update courses set status = 'PUBLISHED' where id = ?", course);
        mvc.perform(MockMvcRequestBuilders.get("/api/me/courses/" + course + "/extra-practice")).andExpect(status().isUnauthorized());
        postJson("/api/me/courses/" + course + "/extra-practice/attempts", null, Map.of("scope", "REVIEW")).andExpect(status().isUnauthorized());
        mvc.perform(MockMvcRequestBuilders.get("/api/me/extra-practice/attempts/1")).andExpect(status().isUnauthorized());
        // body rỗng / scope null: 400 chứ không 500
        send(MockMvcRequestBuilders.post("/api/me/courses/" + course + "/extra-practice/attempts"), u.accessToken()).andExpect(status().isBadRequest());
        start(u, new java.util.HashMap<>()).andExpect(status().isBadRequest());
    }

    @Test
    void anotherStudentCannotReadOrWriteAnExtraAttempt() throws Exception {
        newCourse(false);
        var owner = student(0);
        var other = student(0);
        enroll(owner, null);
        enroll(other, null);
        long t = topicWithQuestions("Thử", 1, 20);
        long attempt = startOk(owner, Map.of("scope", "CHALLENGE", "topicId", t));
        long q = attemptQuestions(attempt).iterator().next();
        getJson("/api/me/extra-practice/attempts/" + attempt, other).andExpect(status().isNotFound());
        getJson("/api/attempts/" + attempt, other).andExpect(status().isNotFound());
        getJson("/api/attempts/" + attempt + "/hints", other).andExpect(status().isNotFound());
        send(MockMvcRequestBuilders.put("/api/attempts/" + attempt + "/draft").contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":[]}"), other.accessToken()).andExpect(status().isNotFound());
        postJson("/api/attempts/" + attempt + "/questions/" + q + "/check", other, Map.of("answer", Map.of("choiceId", 1))).andExpect(status().isNotFound());
        postJson("/api/attempts/" + attempt + "/submit", other, Map.of("answers", List.of())).andExpect(status().isNotFound());
        assertEquals(0, count("select count(*) from student_answers where attempt_id = ?", attempt));
        // lượt thường không mở được qua đường Luyện thêm
        long normalBank = jdbc.queryForObject("select id from question_banks where topic_id = ?", Long.class, t);
        jdbc.update("update student_exercise_attempts set submitted_at = CURRENT_TIMESTAMP where id = ?", attempt);
        long normal = idOf(postJson("/api/banks/" + normalBank + "/attempts", owner, Map.of()).andExpect(status().isCreated()), "id");
        getJson("/api/me/extra-practice/attempts/" + normal, owner).andExpect(status().isNotFound());
    }

    @Test
    void checkRejectsQuestionOutsideTheExtraAttempt() throws Exception {
        newCourse(false);
        var u = student(0);
        enroll(u, null);
        long t = topicWithQuestions("Thử", 1, 20); // trần 3 câu
        long attempt = startOk(u, Map.of("scope", "CHALLENGE", "topicId", t));
        var in = attemptQuestions(attempt);
        long outside = jdbc.queryForList("select q.id from questions q join question_banks b on b.id = q.bank_id where b.topic_id = ?", Long.class, t)
                .stream().filter(id -> !in.contains(id)).findFirst().orElseThrow();
        postJson("/api/attempts/" + attempt + "/questions/" + outside + "/check", u, Map.of("answer", Map.of("choiceId", 1)))
                .andExpect(status().isNotFound());
        postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers", List.of(Map.of("questionId", outside, "answer", Map.of("choiceId", 1)))))
                .andExpect(result -> assertTrue(result.getResponse().getStatus() == 400 || result.getResponse().getStatus() == 200));
        assertEquals(0, count("select count(*) from student_answers where attempt_id = ? and question_id = ?", attempt, outside));
    }

    // ---------- bộ neo EXTRA không rò ra ngoài ----------

    @Test
    void anchorBankCannotBeOpenedStartedOrAssigned() throws Exception {
        newCourse(false);
        var u = student(0);
        enroll(u, null);
        long anchor = anchor();
        getJson("/api/banks/" + anchor, u).andExpect(status().isNotFound());
        postJson("/api/banks/" + anchor + "/attempts", u, Map.of()).andExpect(status().isNotFound());
        assertEquals(0, count("select count(*) from student_exercise_attempts where user_id = ?", u.id()));
        // giáo viên không giao được bộ neo
        long cls = insert("classes", Map.of("course_id", course, "name", "Lớp X", "starts_on", java.time.LocalDate.now().minusDays(1),
                "ends_on", java.time.LocalDate.now().plusDays(30), "mode", "ONLINE", "capacity", 20));
        var teacher = withRole("TEACHER");
        jdbc.update("insert into class_staff (class_id, user_id, role, assigned_from) values (?, ?, 'TEACHER', ?)", cls, teacher.id(),
                java.time.LocalDate.now().minusDays(1));
        postJson("/api/classes/" + cls + "/assignments", teacher, Map.of("bankId", anchor,
                "dueAt", java.time.Instant.now().plusSeconds(86400).toString())).andExpect(status().isBadRequest());
        // CMS liệt kê theo topic không có bộ neo
        long t = topicWithQuestions("Có bộ", 1, 3);
        var admin = withRole("ADMIN");
        var list = getJson("/api/admin/cms/topics/" + t + "/banks", admin).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(!list.contains("\"id\":" + anchor + ","), list);
    }

    @Test
    void cmsCannotDeleteOrReshapeAnExtraAnchorBank() throws Exception {
        var admin = withRole("ADMIN");
        // bộ EXTRA mới chưa có lượt nào: giống bộ neo ngay sau khi deploy V44 (chưa ai luyện thêm), không có FK nào chặn
        long spare = insert("question_banks", Map.of("title", "Luyện thêm", "status", "DRAFT", "mode", "PRACTICE", "purpose", "EXTRA", "is_trial", false));
        int patch = send(MockMvcRequestBuilders.patch("/api/admin/cms/banks/" + spare).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"EXAM\"}"), admin.accessToken()).andReturn().getResponse().getStatus();
        int del = send(MockMvcRequestBuilders.delete("/api/admin/cms/banks/" + spare), admin.accessToken()).andReturn().getResponse().getStatus();
        jdbc.update("delete from question_banks where id = ?", spare); // dọn
        assertTrue(patch >= 400 && patch < 500, "PATCH mode bộ EXTRA trả " + patch);
        assertTrue(del >= 400 && del < 500, "CMS xóa được bộ EXTRA (" + del + "): xóa bộ neo thật thì mọi lần bắt đầu Luyện thêm lỗi 500");
    }

    @Test
    void statsProfileHistoryAndDashboardStillWorkWithExtraAttempts() throws Exception {
        newCourse(false);
        var u = student(0);
        enroll(u, null);
        long t = topicWithQuestions("Thống kê", 1, 20);
        long attempt = startOk(u, Map.of("scope", "CHALLENGE", "topicId", t));
        long q = attemptQuestions(attempt).iterator().next();
        postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers", List.of(Map.of("questionId", q, "answer", Map.of("choiceId", 1)))))
                .andExpect(status().isOk());
        getJson("/api/me/learning-profile", u).andExpect(status().isOk());
        getJson("/api/me/courses/" + course + "/exercise-stats", u).andExpect(status().isOk());
        getJson("/api/me/courses/" + course + "/roadmap", u).andExpect(status().isOk());
        getJson("/api/me/today", u).andExpect(status().isOk());
        getJson("/api/me/attempts", u).andExpect(status().isOk());
        getJson("/api/me/cam-messages", u).andExpect(status().isOk());
        getJson("/api/admin/reports/dashboard", withRole("ADMIN")).andExpect(status().isOk());
        // không tính vào tiến độ Topic
        var card = json.readTree(getJson("/api/me/courses/" + course + "/extra-practice", u).andReturn().getResponse().getContentAsString());
        assertEquals(0, card.get("review").size());
    }

    @Test
    void camContinueLinkOfAnOpenExtraAttemptOpensTheExtraRunPage() throws Exception {
        newCourse(false);
        var u = student(0);
        enroll(u, null);
        long t = topicWithQuestions("Cam", 1, 20);
        long attempt = startOk(u, Map.of("scope", "CHALLENGE", "topicId", t));
        JsonNode messages = json.readTree(getJson("/api/me/cam-messages", u).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("messages");
        for (var m : messages) {
            if ("IN_PROGRESS_ATTEMPT".equals(m.get("kind").asText())) {
                // /bai-tap/{bộ neo} trả 404 bank_not_found trên web; đích đúng là màn làm lượt Luyện thêm
                assertEquals("/bai-tap/luyen-them/" + attempt, m.get("action").get("path").asText());
                return;
            }
        }
        throw new AssertionError("Không có nhắc lượt dở: " + messages);
    }

    // ---------- đồng thời ----------

    @Test
    void concurrentStartsCreateOnlyOneOpenExtraAttempt() throws Exception {
        newCourse(false);
        var u = student(0);
        enroll(u, null);
        long t = topicWithQuestions("Song song", 1, 40);
        var pool = Executors.newFixedThreadPool(4);
        try {
            var gate = new CountDownLatch(1);
            List<Future<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                Callable<Integer> c = () -> {
                    gate.await();
                    return start(u, Map.of("scope", "CHALLENGE", "topicId", t)).andReturn().getResponse().getStatus();
                };
                calls.add(pool.submit(c));
            }
            gate.countDown();
            int created = 0;
            for (var f : calls) {
                int code = f.get();
                assertTrue(code == 201 || code == 409, "mã " + code);
                created += code == 201 ? 1 : 0;
            }
            assertEquals(1, created);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, count("select count(*) from student_exercise_attempts where user_id = ? and extra = true and submitted_at is null", u.id()));
    }

    // ---------- lượt AI ----------

    @Test
    void writingInExtraAttemptDebitsTheCoursePeriodicQuotaBeforeThePool() throws Exception {
        newCourse(false);
        jdbc.update("update courses set ai_period = 'WEEK', ai_period_quota = 3 where id = ?", course);
        var u = student(5);
        enroll(u, null);
        long t = topic("Viết", 1, false);
        long wq = writingQuestion(bank(t, "PRACTICE", "PUBLISHED"));
        long attempt = startOk(u, Map.of("scope", "CHALLENGE", "topicId", t, "kind", "WRITING"));
        assertEquals(Set.of(wq), attemptQuestions(attempt));
        guard.resetRateLimit(u.id());
        long session = idOf(postJson("/api/writing/sessions", u, Map.of("questionId", wq, "attemptId", attempt)).andExpect(status().isCreated()), "sessionId");
        guard.resetRateLimit(u.id());
        postJson("/api/writing/sessions/" + session + "/submit", u, Map.of("finalText", "I agree with online learning because it is flexible. However, students need discipline."),
                UUID.randomUUID().toString()).andExpect(status().isAccepted());
        var used = jdbc.queryForList("select used from ai_period_usage where user_id = ? and course_id = ?", Integer.class, u.id(), course);
        assertEquals(List.of(1), used, "trừ ví kỳ của đúng khóa chứa câu");
        assertEquals(5, balance(u), "pool chưa bị trừ khi ví khóa còn");
    }
}
