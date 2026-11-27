package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** D-163 (tester): đáp án đúng và giải thích chỉ lộ cho chủ lượt, chỉ ở câu khóa sai của lượt Luyện tập. */
class D163RevealTesterTest extends ListeningTestSupport {

    private World practice() {
        var w = world("FREE", null);
        jdbc.update("update question_banks set mode = 'PRACTICE' where id = ?", w.bank());
        insert("question_hints", Map.of("question_id", w.q()[0], "content", "Gợi ý", "sort_order", 0));
        return w;
    }

    private ResultActions check(Account u, long attempt, long q, long choice) throws Exception {
        return postJson("/api/attempts/" + attempt + "/questions/" + q + "/check", u, Map.of("answer", Map.of("choiceId", choice)));
    }

    private String body(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString();
    }

    private void exhaust(Account u, long attempt, World w, int i) throws Exception {
        for (int k = 0; k < 3; k++) {
            check(u, attempt, w.q()[i], w.wrong()[i]).andExpect(status().isOk());
        }
    }

    @Test
    void strangerCannotReadChecksOrRevealedAnswerOfAnotherAttempt() throws Exception {
        var w = practice();
        var owner = student(0);
        var stranger = student(0);
        long a = startAttempt(owner, w);
        exhaust(owner, a, w, 0);
        for (var r : List.of(getJson("/api/attempts/" + a + "/progress", stranger), getJson("/api/attempts/" + a + "/hints", stranger),
                put("/api/attempts/" + a + "/draft", stranger, Map.of("answers", answers(w, true))),
                check(stranger, a, w.q()[0], w.right()[0]))) {
            var res = r.andReturn().getResponse();
            assertEquals(404, res.getStatus());
            assertFalse(res.getContentAsString().contains(SECRET_EXPLANATION), "lộ giải thích cho người khác");
        }
        // chủ lượt vẫn thấy đáp án câu khóa sai; draft của người lạ không ghi đè
        getJson("/api/attempts/" + a + "/progress", owner).andExpect(jsonPath("$.retry.checks[0].explanation").value(SECRET_EXPLANATION + 0))
                .andExpect(jsonPath("$.answers[0].answer.choiceId").value(w.wrong()[0]));
    }

    @Test
    void solvedAfterTwoWrongsNeverRevealsAnywhere() throws Exception {
        var w = practice();
        var u = student(0);
        long a = startAttempt(u, w);
        check(u, a, w.q()[0], w.wrong()[0]).andExpect(jsonPath("$.wrongCount").value(1)).andExpect(jsonPath("$.checksLeft").value(2));
        check(u, a, w.q()[0], w.wrong()[0]).andExpect(jsonPath("$.wrongCount").value(2)).andExpect(jsonPath("$.checksLeft").value(1))
                .andExpect(jsonPath("$.correctAnswer").doesNotExist());
        String right = body(check(u, a, w.q()[0], w.right()[0]).andExpect(jsonPath("$.correct").value(true)));
        assertFalse(right.contains("correctAnswer") || right.contains(SECRET_EXPLANATION), right);
        for (String url : List.of("/api/attempts/" + a + "/progress", "/api/attempts/" + a + "/hints", "/api/banks/" + w.bank())) {
            String b = body(getJson(url, u).andExpect(status().isOk()));
            assertFalse(b.contains(SECRET_EXPLANATION) || b.contains("correctAnswer"), url + " lộ đáp án: " + b);
        }
    }

    @Test
    void onlyLockedWrongQuestionIsRevealedInProgress() throws Exception {
        var w = practice();
        var u = student(0);
        long a = startAttempt(u, w);
        exhaust(u, a, w, 0);
        check(u, a, w.q()[1], w.wrong()[1]); // q1 còn lượt
        String p = body(getJson("/api/attempts/" + a + "/progress", u));
        assertTrue(p.contains(SECRET_EXPLANATION + 0));
        assertFalse(p.contains(SECRET_EXPLANATION + 1), "câu còn lượt bị lộ giải thích: " + p);
        String bank = body(getJson("/api/banks/" + w.bank(), u));
        assertFalse(bank.contains(SECRET_EXPLANATION), "GET bộ lộ giải thích sau khi khóa câu");
    }

    @Test
    void lockedWrongWithoutExplanationStillShowsCorrectAnswer() throws Exception {
        var w = practice();
        jdbc.update("update questions set explanation = null where id = ?", w.q()[1]);
        var u = student(0);
        long a = startAttempt(u, w);
        check(u, a, w.q()[1], w.wrong()[1]);
        check(u, a, w.q()[1], w.wrong()[1]);
        check(u, a, w.q()[1], w.wrong()[1]).andExpect(status().isOk()).andExpect(jsonPath("$.checksLeft").value(0))
                .andExpect(jsonPath("$.correctAnswer.choiceId").value(w.right()[1])).andExpect(jsonPath("$.explanation").doesNotExist());
    }

    /** Câu khóa sai: đổi đáp án sau khi thấy đáp án đúng bị bỏ qua, kể cả khi nộp không gửi câu đó. */
    @Test
    void lockedWrongAnswerIsPinnedAfterReveal() throws Exception {
        var w = practice();
        var u = student(0);
        long a = startAttempt(u, w);
        exhaust(u, a, w, 0);
        put("/api/attempts/" + a + "/draft", u, Map.of("answers", answers(w, true, true))).andExpect(status().isOk())
                .andExpect(jsonPath("$.answers[?(@.questionId == " + w.q()[0] + ")].answer.choiceId").value(org.hamcrest.Matchers.contains((int) w.wrong()[0])));
        postJson("/api/attempts/" + a + "/submit", u, Map.of("answers", answers(w, true, true, true, true))).andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(3));
    }

    /** Hai lần kiểm tra cuối song song: một lần 200 kèm đáp án, lần kia 409 không kèm đáp án. */
    @Test
    void parallelFinalChecksRevealExactlyOnce() throws Exception {
        var w = practice();
        var u = student(0);
        long a = startAttempt(u, w);
        check(u, a, w.q()[0], w.wrong()[0]);
        check(u, a, w.q()[0], w.wrong()[0]);
        var pool = Executors.newFixedThreadPool(2);
        var go = new CountDownLatch(1);
        List<Future<MvcResult>> fs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            fs.add(pool.submit(() -> {
                go.await();
                return check(u, a, w.q()[0], w.wrong()[0]).andReturn();
            }));
        }
        go.countDown();
        List<String> ok = new ArrayList<>();
        List<String> conflict = new ArrayList<>();
        for (var f : fs) {
            var r = f.get(60, TimeUnit.SECONDS).getResponse();
            (r.getStatus() == 200 ? ok : conflict).add(r.getContentAsString());
        }
        pool.shutdown();
        assertEquals(1, ok.size(), "ok=" + ok + " conflict=" + conflict);
        assertEquals(1, conflict.size());
        assertTrue(ok.get(0).contains(SECRET_EXPLANATION + 0), ok.get(0));
        assertTrue(conflict.get(0).contains("retry_exhausted") && !conflict.get(0).contains(SECRET_EXPLANATION), conflict.get(0));
    }

    /** Lượt đổi sang EXAM (D-151) sau khi đã khóa sai: progress không còn retry, không còn đáp án. */
    @Test
    void examModeAttemptNeverReturnsRevealedAnswer() throws Exception {
        var w = practice();
        var u = student(0);
        long a = startAttempt(u, w);
        exhaust(u, a, w, 0);
        jdbc.update("update student_exercise_attempts set mode = 'EXAM' where id = ?", a);
        String p = body(getJson("/api/attempts/" + a + "/progress", u).andExpect(status().isOk()).andExpect(jsonPath("$.retry").doesNotExist()));
        assertFalse(p.contains(SECRET_EXPLANATION), p);
        check(u, a, w.q()[1], w.wrong()[1]).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("retry_not_available"));
    }

    // ---- bài giao lớp (D-162) ----

    private record Fx(World w, long cls, Account teacher, Account s) {}

    private Fx klass() throws Exception {
        var w = practice();
        jdbc.update("update courses set delivery_mode = 'CLASS_BASED' where id = ?", w.course());
        long cls = insert("classes", Map.of("course_id", w.course(), "name", "Lớp", "starts_on", LocalDate.now().minusDays(1),
                "ends_on", LocalDate.now().plusDays(60), "mode", "ONLINE", "capacity", 20));
        var teacher = withRole("TEACHER");
        jdbc.update("insert into class_staff (class_id, user_id, role, assigned_from) values (?, ?, 'TEACHER', ?)", cls, teacher.id(),
                LocalDate.now().minusDays(2));
        var s = student(0);
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', "
                + "TIMESTAMP '2020-01-01 00:00:00', TIMESTAMP '2099-01-01 00:00:00')", s.id(), w.course());
        jdbc.update("insert into class_members (class_id, course_id, user_id, status, live_key, joined_at) values (?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP)",
                cls, w.course(), s.id(), w.course() + ":" + s.id());
        return new Fx(w, cls, teacher, s);
    }

    private long assign(Fx f, String mode) throws Exception {
        return idOf(postJson("/api/classes/" + f.cls() + "/assignments", f.teacher(), Map.of("bankId", f.w().bank(), "mode", mode,
                "dueAt", Instant.now().plus(48, ChronoUnit.HOURS).toString())).andExpect(status().isCreated()), "id");
    }

    /** D-162: lượt tự học đã khóa sai, sau đó bộ được giao Thi: đáp án đã khóa không còn trả ra, không kiểm tra thêm được. */
    @Test
    void selfAttemptOnBankAssignedAsExamHidesRevealedAnswers() throws Exception {
        var f = klass();
        long self = startAttempt(f.s(), f.w());
        exhaust(f.s(), self, f.w(), 0);
        assign(f, "EXAM");
        String p = body(getJson("/api/attempts/" + self + "/progress", f.s()).andExpect(status().isOk()));
        assertFalse(p.contains(SECRET_EXPLANATION) || p.contains("correctAnswer"), p);
        check(f.s(), self, f.w().q()[1], f.w().wrong()[1]).andExpect(status().isConflict());
    }

    @Test
    void examAssignmentAttemptHasNoChecks() throws Exception {
        var f = klass();
        long asg = assign(f, "EXAM");
        long a = idOf(postJson("/api/classes/" + f.cls() + "/assignments/" + asg + "/start", f.s(), Map.of()).andExpect(status().isOk()), "id");
        check(f.s(), a, f.w().q()[0], f.w().wrong()[0]).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("retry_not_available"));
        String p = body(getJson("/api/attempts/" + a + "/progress", f.s()).andExpect(jsonPath("$.retry").doesNotExist()));
        assertFalse(p.contains(SECRET_EXPLANATION), p);
    }
}
