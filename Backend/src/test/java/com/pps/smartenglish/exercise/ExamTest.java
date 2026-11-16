package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.support.ApiTestBase;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** FR-EXAM-02, D-35, D-36, D-46: cấu hình bài thi khóa, điều kiện mở, số lần thi, trạng thái cho giao diện. */
class ExamTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";

    /** Khóa 2 bài học + bài thi EXAM (một topic); trial = topic miễn phí. */
    private record Setup(long course, long topic, long bank, long lesson1, long lesson2) {}

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private Setup setup(boolean freeTrial, String bankMode, boolean configure) {
        long c = insert("courses", Map.of("slug", "ex-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED"));
        long t = insert("topics", Map.of("level_id", lv, "title", "Topic", "is_free_trial", freeTrial, "status", "PUBLISHED"));
        long l1 = insert("lessons", Map.of("topic_id", t, "type", "CONNECTIVE_VIDEO", "title", "B1", "status", "PUBLISHED"));
        long l2 = insert("lessons", Map.of("topic_id", t, "type", "CONNECTIVE_VIDEO", "title", "B2", "status", "PUBLISHED"));
        long b = insert("question_banks", new HashMap<>(Map.of("topic_id", t, "title", "Thi", "is_trial", freeTrial,
                "status", "PUBLISHED", "mode", bankMode)));
        if (configure) {
            jdbc.update("update courses set exam_bank_id = ?, exam_unlock_percent = 50 where id = ?", b, c);
        }
        return new Setup(c, t, b, l1, l2);
    }

    private void enroll(Account u, long course, OffsetDateTime expiresAt) {
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', ?, ?)",
                u.id(), course, OffsetDateTime.parse("2020-01-01T00:00:00Z"), expiresAt);
    }

    private void complete(Account u, long lesson) {
        jdbc.update("insert into lesson_progress (user_id, lesson_id, watched_sec, max_position_sec, completed_at) "
                + "values (?, ?, 10, 10, CURRENT_TIMESTAMP)", u.id(), lesson);
    }

    private ResultActions start(Account u, long bank) throws Exception {
        return send(MockMvcRequestBuilders.post("/api/banks/" + bank + "/attempts").contentType(MediaType.APPLICATION_JSON).content("{}"),
                u.accessToken());
    }

    private ResultActions exam(Account u, long course) throws Exception {
        return send(get("/api/courses/" + course + "/exam"), u.accessToken());
    }

    private void submitAttempt(long attemptId) {
        jdbc.update("update student_exercise_attempts set submitted_at = CURRENT_TIMESTAMP, score = 1, max_score = 2 where id = ?", attemptId);
    }

    private long attemptIdOf(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    /** Học viên đã mua và học đủ ngưỡng (1/2 bài = 50%). */
    private Account ready(Setup s) throws Exception {
        var u = register(newEmail(), PASSWORD);
        enroll(u, s.course(), null);
        complete(u, s.lesson1());
        return u;
    }

    @Test
    void notEnrolledGetsExamRequiresEnrollmentEvenInFreeTrialTopic() throws Exception {
        var s = setup(true, "EXAM", true);
        var u = register(newEmail(), PASSWORD);
        start(u, s.bank()).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("exam_requires_enrollment"));
        // bộ EXAM không gắn khóa: học thử vẫn không vào được (D-35)
        var other = setup(true, "EXAM", false);
        start(u, other.bank()).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("bank_locked"));
        jdbc.update("update question_banks set is_trial = true where id = ?", other.bank());
        start(u, other.bank()).andExpect(status().isForbidden());
    }

    @Test
    void lockedUntilProgressThenOpensAndCountsAttempts() throws Exception {
        var s = setup(false, "EXAM", true);
        var u = register(newEmail(), PASSWORD);
        enroll(u, s.course(), null);
        start(u, s.bank()).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("exam_locked"))
                .andExpect(jsonPath("$.error.details.progressPercent").value(0))
                .andExpect(jsonPath("$.error.details.requiredPercent").value(50));
        complete(u, s.lesson1());
        long first = attemptIdOf(start(u, s.bank()).andExpect(status().isCreated()));

        start(u, s.bank()).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("exam_in_progress"))
                .andExpect(jsonPath("$.error.details.attemptId").value(first));
        submitAttempt(first);
        // retakes mặc định 0: chỉ một lần
        start(u, s.bank()).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("exam_attempts_exhausted"))
                .andExpect(jsonPath("$.error.details.used").value(1)).andExpect(jsonPath("$.error.details.allowed").value(1));
    }

    @Test
    void retakesAllowExtraAttempts() throws Exception {
        var s = setup(false, "EXAM", true);
        jdbc.update("update courses set exam_retakes = 1 where id = ?", s.course());
        var u = ready(s);
        submitAttempt(attemptIdOf(start(u, s.bank()).andExpect(status().isCreated())));
        long second = attemptIdOf(start(u, s.bank()).andExpect(status().isCreated()));
        submitAttempt(second);
        start(u, s.bank()).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("exam_attempts_exhausted"))
                .andExpect(jsonPath("$.error.details.allowed").value(2));
    }

    @Test
    void parallelStartsCreateOnlyOneAttemptWhenOneIsLeft() throws Exception {
        var s = setup(false, "EXAM", true);
        var u = ready(s);
        var pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                calls.add(() -> start(u, s.bank()).andReturn().getResponse().getStatus());
            }
            var codes = new ArrayList<Integer>();
            for (var f : pool.invokeAll(calls)) {
                codes.add(f.get());
            }
            assertEquals(1, codes.stream().filter(c -> c == 201).count(), codes.toString());
            assertEquals(1, count("select count(*) from student_exercise_attempts where user_id = ? and bank_id = ?", u.id(), s.bank()));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void examStatusReportsEachReason() throws Exception {
        var none = setup(false, "EXAM", false);
        var u0 = register(newEmail(), PASSWORD);
        exam(u0, none.course()).andExpect(status().isOk()).andExpect(jsonPath("$.reason").value("NOT_CONFIGURED"))
                .andExpect(jsonPath("$.unlocked").value(false)).andExpect(jsonPath("$.bankId").doesNotExist());

        var s = setup(false, "EXAM", true);
        var u = register(newEmail(), PASSWORD);
        exam(u, s.course()).andExpect(jsonPath("$.reason").value("ENROLLMENT_REQUIRED")).andExpect(jsonPath("$.unlocked").value(false))
                .andExpect(jsonPath("$.requiredPercent").value(50)).andExpect(jsonPath("$.attemptsAllowed").value(1));
        enroll(u, s.course(), null);
        exam(u, s.course()).andExpect(jsonPath("$.reason").value("PROGRESS")).andExpect(jsonPath("$.progressPercent").value(0));
        complete(u, s.lesson1());
        long attempt = attemptIdOf(start(u, s.bank()).andExpect(status().isCreated()));
        exam(u, s.course()).andExpect(jsonPath("$.unlocked").value(true)).andExpect(jsonPath("$.progressPercent").value(50))
                .andExpect(jsonPath("$.inProgressAttemptId").value(attempt)).andExpect(jsonPath("$.attemptsUsed").value(1));
        submitAttempt(attempt);
        exam(u, s.course()).andExpect(jsonPath("$.reason").value("ATTEMPTS_EXHAUSTED")).andExpect(jsonPath("$.unlocked").value(false))
                .andExpect(jsonPath("$.lastResult.score").value(1)).andExpect(jsonPath("$.lastResult.maxScore").value(2));

        // D-36: hết hạn vẫn xem được kết quả
        jdbc.update("update user_course_enrollments set expires_at = ? where user_id = ?", OffsetDateTime.parse("2021-01-01T00:00:00Z"), u.id());
        exam(u, s.course()).andExpect(jsonPath("$.reason").value("ENROLLMENT_REQUIRED")).andExpect(jsonPath("$.lastResult.score").value(1));
        exam(u, 999999).andExpect(status().isNotFound());
    }

    @Test
    void courseProgressCountsOnlyPublishedCompletedLessons() throws Exception {
        var s = setup(false, "PRACTICE", false);
        insert("lessons", Map.of("topic_id", s.topic(), "type", "CONNECTIVE_VIDEO", "title", "Nháp", "status", "DRAFT"));
        var u = register(newEmail(), PASSWORD);
        send(get("/api/me/courses/" + s.course() + "/progress"), u.accessToken()).andExpect(jsonPath("$.percent").value(0))
                .andExpect(jsonPath("$.totalLessons").value(2));
        complete(u, s.lesson1());
        send(get("/api/me/courses/" + s.course() + "/progress"), u.accessToken()).andExpect(jsonPath("$.completedLessons").value(1))
                .andExpect(jsonPath("$.percent").value(50));
        send(get("/api/me/courses/999999/progress"), u.accessToken()).andExpect(status().isNotFound());
        mvc.perform(get("/api/me/courses/" + s.course() + "/progress")).andExpect(status().isUnauthorized());
    }

    private Account withRole(String role) throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = ?", u.id(), role);
        return login(u.email(), PASSWORD);
    }

    private ResultActions cmsPatch(Account a, long course, Object body) throws Exception {
        return send(patch("/api/admin/cms/courses/" + course).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)), a.accessToken());
    }

    @Test
    void cmsConfiguresExamAndRejectsWrongBanks() throws Exception {
        var s = setup(false, "EXAM", false);
        var practice = setup(false, "PRACTICE", false);
        var editor = withRole("EDITOR");
        // bank PRACTICE cùng khóa, bank EXAM khóa khác
        long practiceHere = insert("question_banks", Map.of("topic_id", s.topic(), "title", "Luyện", "is_trial", false,
                "status", "PUBLISHED", "mode", "PRACTICE"));
        cmsPatch(editor, s.course(), Map.of("examBankId", practiceHere)).andExpect(status().isBadRequest());
        cmsPatch(editor, s.course(), Map.of("examBankId", practice.bank())).andExpect(status().isBadRequest());
        cmsPatch(editor, s.course(), Map.of("examUnlockPercent", 101)).andExpect(status().isBadRequest());
        cmsPatch(editor, s.course(), Map.of("examBankId", s.bank(), "examUnlockPercent", 70, "examRetakes", 2))
                .andExpect(status().isOk()).andExpect(jsonPath("$.examBankId").value(s.bank()))
                .andExpect(jsonPath("$.examUnlockPercent").value(70)).andExpect(jsonPath("$.examRetakes").value(2));
        assertEquals(1, count("select count(*) from audit_logs where target_type = 'COURSE' and target_id = ? and action = 'UPDATE'", String.valueOf(s.course())));

        var student = register(newEmail(), PASSWORD);
        cmsPatch(student, s.course(), Map.of("examRetakes", 3)).andExpect(status().isForbidden());
    }

    @Test
    void examQuestionsAreHiddenUntilAnAttemptIsStarted() throws Exception {
        var s = setup(false, "EXAM", true);
        var u = register(newEmail(), PASSWORD);
        send(get("/api/banks/" + s.bank()), u.accessToken()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("exam_requires_enrollment"));
        enroll(u, s.course(), null);
        send(get("/api/banks/" + s.bank()), u.accessToken()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("exam_locked"));
        complete(u, s.lesson1());
        send(get("/api/banks/" + s.bank()), u.accessToken()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("exam_not_started"));
        long attempt = attemptIdOf(start(u, s.bank()).andExpect(status().isCreated()));
        send(get("/api/banks/" + s.bank()), u.accessToken()).andExpect(status().isOk());
        submitAttempt(attempt);
        send(get("/api/banks/" + s.bank()), u.accessToken()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("exam_not_started"));
        send(get("/api/attempts/" + attempt), u.accessToken()).andExpect(status().isOk()); // kết quả của chính họ
    }

    @Test
    void cmsClearsExamBankWithAudit() throws Exception {
        var s = setup(false, "EXAM", true);
        var editor = withRole("EDITOR");
        cmsPatch(editor, s.course(), Map.of("clearExamBank", true, "examBankId", s.bank())).andExpect(status().isBadRequest());
        cmsPatch(editor, s.course(), Map.of("clearExamBank", true)).andExpect(status().isOk())
                .andExpect(jsonPath("$.examBankId").doesNotExist());
        assertEquals(1, count("select count(*) from courses where id = ? and exam_bank_id is null", s.course()));
        assertEquals(1, count("select count(*) from audit_logs where target_type = 'COURSE' and target_id = ? and action = 'UPDATE'",
                String.valueOf(s.course())));
    }
}
