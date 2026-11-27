package com.pps.smartenglish.classroom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.ai.AiTestSupport;
import com.pps.smartenglish.ai.dto.RewriteGrades;
import com.pps.smartenglish.ai.provider.AiCallException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Tester đợt #81: D-166 (điểm giáo viên thành điểm lượt, AI chấm xong không ghi đè, phân quyền, chặn maxScore),
 * D-147 câu viết lại lỗi hẳn (bài giao không hoàn lượt, XP một lần; chấm lại tự động thành công), D-165 (bài giao thi khóa miễn điều kiện).
 */
class D166TeacherScoreTesterTest extends AiTestSupport {

    private static final String HOWEVER = "It rained. However, we went out.";

    private record Fx(long course, long cls, long bank, long mcq, long right, long rewrite, Account teacher) {}

    private ResultActions call(HttpMethod method, String url, Account who, Object body) throws Exception {
        var req = MockMvcRequestBuilders.request(method, url);
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return send(req, who.accessToken());
    }

    private Account staff(long cls, String role) throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = ?", u.id(), role);
        jdbc.update("insert into class_staff (class_id, user_id, role, assigned_from) values (?, ?, ?, ?)", cls, u.id(), role, LocalDate.now().minusDays(10));
        return login(u.email(), PASSWORD);
    }

    private long newClass(long course) {
        return insert("classes", Map.of("course_id", course, "name", "Lớp " + UUID.randomUUID().toString().substring(0, 4),
                "starts_on", LocalDate.now().minusDays(10), "ends_on", LocalDate.now().plusDays(60), "mode", "ONLINE", "capacity", 20));
    }

    /** Bộ 2 câu: trắc nghiệm (đúng = right) và câu viết lại chỉ AI chấm được. */
    private Fx fixture(String mode) throws Exception {
        long c = insert("courses", Map.of("slug", "d166-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED",
                "delivery_mode", "CLASS_BASED", "sequential", false));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED", "sort_order", 0));
        long t = insert("topics", Map.of("level_id", lv, "title", "Unit A", "is_free_trial", false, "status", "PUBLISHED", "sort_order", 0));
        long bank = insert("question_banks", Map.of("topic_id", t, "title", "Bộ", "is_trial", false, "status", "PUBLISHED", "mode", mode, "pass_pct", 50));
        long mcq = question(bank, "CONNECTIVE_MCQ", 1, "Chọn từ nối", "{}", "{}");
        long right = insert("question_choices", Map.of("question_id", mcq, "label", "A", "content", "although", "is_correct", true));
        insert("question_choices", Map.of("question_id", mcq, "label", "B", "content", "so", "is_correct", false));
        long rw = question(bank, "CONNECTIVE_REWRITE", 2, "Viết lại với however",
                "{\"sentence\":\"It rained. We went out.\",\"requiredWord\":\"however\"}", "{}");
        long cls = newClass(c);
        return new Fx(c, cls, bank, mcq, right, rw, staff(cls, "TEACHER"));
    }

    private Account member(Fx f, long cls, int quota) throws Exception {
        var s = student(quota);
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', "
                + "TIMESTAMP '2020-01-01 00:00:00', TIMESTAMP '2099-01-01 00:00:00')", s.id(), f.course());
        if (cls > 0) {
            jdbc.update("insert into class_members (class_id, course_id, user_id, status, live_key, joined_at) values (?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP)",
                    cls, f.course(), s.id(), f.course() + ":" + s.id());
        }
        return s;
    }

    private long assign(Fx f, long cls, Account teacher, long bank) throws Exception {
        var body = Map.of("bankId", bank, "dueAt", Instant.now().plus(48, ChronoUnit.HOURS).toString());
        return idOf(call(HttpMethod.POST, "/api/classes/" + cls + "/assignments", teacher, body).andExpect(status().isCreated()), "id");
    }

    private long startAssigned(long cls, long asg, Account s) throws Exception {
        return idOf(call(HttpMethod.POST, "/api/classes/" + cls + "/assignments/" + asg + "/start", s, null).andExpect(status().isOk()), "id");
    }

    /** Nộp: trắc nghiệm đúng, câu viết lại cần AI. */
    private ResultActions submitBoth(Fx f, long attempt, Account s) throws Exception {
        guard.resetRateLimit(s.id());
        return postJson("/api/attempts/" + attempt + "/submit", s, Map.of("answers", List.of(
                Map.of("questionId", f.mcq(), "answer", Map.of("choiceId", f.right())),
                Map.of("questionId", f.rewrite(), "answer", Map.of("text", HOWEVER)))));
    }

    private String gradeUrl(long cls, long asg, long attempt) {
        return "/api/classes/" + cls + "/assignments/" + asg + "/attempts/" + attempt + "/grade";
    }

    private BigDecimal score(long attempt) {
        return jdbc.queryForObject("select score from student_exercise_attempts where id = ?", BigDecimal.class, attempt);
    }

    private BigDecimal machine(long attempt) {
        return jdbc.queryForObject("select machine_score from student_exercise_attempts where id = ?", BigDecimal.class, attempt);
    }

    private void runAll() {
        for (int i = 0; i < 8 && runner.runDueNow() > 0; i++) {
            jdbc.update("update ai_jobs set run_after = ? where status = 'PENDING'", OffsetDateTime.parse("2000-01-01T00:00:00Z"));
        }
    }

    // ---- D-166 ----

    /** Giáo viên sửa điểm lúc câu viết lại còn chờ AI: AI chấm xong không ghi đè điểm giáo viên, machine_score là điểm máy đầy đủ. */
    @Test
    void aiResultAfterTeacherGradeKeepsTeacherScore() throws Exception {
        var f = fixture("PRACTICE");
        var s = member(f, f.cls(), 0);
        long asg = assign(f, f.cls(), f.teacher(), f.bank());
        long attempt = startAssigned(f.cls(), asg, s);
        submitBoth(f, attempt, s).andExpect(status().isOk()).andExpect(jsonPath("$.pendingJobs.length()").value(1));

        call(HttpMethod.PUT, gradeUrl(f.cls(), asg, attempt), f.teacher(), Map.of("score", 0.5)).andExpect(status().isCreated());
        fake.override(RewriteGrades.class, new RewriteGrades(List.of(new RewriteGrades.Item(f.rewrite(), true, "Tốt.", HOWEVER))));
        runAll();

        assertEquals(0, new BigDecimal("0.50").compareTo(score(attempt)), "AI chấm xong ghi đè điểm giáo viên");
        assertEquals(0, new BigDecimal("2").compareTo(machine(attempt)), "machine_score phải là điểm máy sau khi AI chấm xong");
        getJson("/api/attempts/" + attempt, s).andExpect(jsonPath("$.score").value(0.5)).andExpect(jsonPath("$.passed").value(false));
    }

    /** Học viên tự sửa điểm, giáo viên lớp khác, dùng bài giao của lớp mình với lượt của lớp khác, điểm vượt maxScore: đều bị chặn, điểm lượt không đổi. */
    @Test
    void onlyStaffOfTheClassCanGradeAndScoreIsCappedAtMax() throws Exception {
        var f = fixture("PRACTICE");
        var s = member(f, f.cls(), 5);
        long asg = assign(f, f.cls(), f.teacher(), f.bank());
        long attempt = startAssigned(f.cls(), asg, s);
        guard.resetRateLimit(s.id());
        postJson("/api/attempts/" + attempt + "/submit", s, Map.of("answers", List.of(Map.of("questionId", f.mcq(), "answer", Map.of("choiceId", f.right())))))
                .andExpect(status().isOk());
        BigDecimal before = score(attempt);

        call(HttpMethod.PUT, gradeUrl(f.cls(), asg, attempt), s, Map.of("score", 2)).andExpect(r -> assertTrue(
                r.getResponse().getStatus() == 403 || r.getResponse().getStatus() == 404, "học viên tự sửa điểm"));
        long otherCls = newClass(f.course());
        var otherTeacher = staff(otherCls, "TEACHER");
        call(HttpMethod.PUT, gradeUrl(f.cls(), asg, attempt), otherTeacher, Map.of("score", 2)).andExpect(r -> assertTrue(
                r.getResponse().getStatus() == 403 || r.getResponse().getStatus() == 404, "giáo viên lớp khác sửa được điểm"));
        long otherAsg = assign(f, otherCls, otherTeacher, f.bank());
        call(HttpMethod.PUT, gradeUrl(otherCls, otherAsg, attempt), otherTeacher, Map.of("score", 2)).andExpect(status().isNotFound());
        call(HttpMethod.PUT, gradeUrl(f.cls(), asg, attempt), f.teacher(), Map.of("score", 2.01)).andExpect(status().isBadRequest());
        call(HttpMethod.PUT, gradeUrl(f.cls(), asg, attempt), f.teacher(), Map.of("score", -1)).andExpect(status().isBadRequest());

        assertEquals(0, before.compareTo(score(attempt)));
        assertNull(machine(attempt));
        assertEquals(0, count("select count(*) from grade_overrides where attempt_id = ?", attempt));

        var assistant = staff(f.cls(), "ASSISTANT");
        call(HttpMethod.PUT, gradeUrl(f.cls(), asg, attempt), assistant, Map.of("score", 2)).andExpect(status().isCreated());
        assertEquals(0, new BigDecimal("2").compareTo(score(attempt)));
        assertEquals(0, before.compareTo(machine(attempt)));
    }

    // ---- D-147: câu viết lại ----

    /** Bài giao (không trừ lượt): job viết lại lỗi hẳn hai lần, câu tính 0 kèm lý do, không báo hoàn lượt, XP lần nộp chỉ một lần. */
    @Test
    void assignedRewriteJobFailingTwiceScoresZeroWithoutRefundAndXpOnce() throws Exception {
        var f = fixture("PRACTICE");
        var s = member(f, f.cls(), 0);
        long asg = assign(f, f.cls(), f.teacher(), f.bank());
        long attempt = startAssigned(f.cls(), asg, s);
        submitBoth(f, attempt, s).andExpect(status().isOk());
        fake.failNext(2, new AiCallException(false, "từ chối", null, null));
        runAll();

        assertEquals("FAILED", jdbc.queryForObject("select status from ai_jobs where attempt_id = ?", String.class, attempt));
        getJson("/api/attempts/" + attempt, s).andExpect(jsonPath("$.pendingJobs.length()").value(0))
                .andExpect(jsonPath("$.score").value(1))
                .andExpect(jsonPath("$.results[1].isCorrect").value(false))
                .andExpect(jsonPath("$.results[1].aiFailure").value("Cam chưa chấm được câu này"))
                .andExpect(jsonPath("$.results[1].aiRefunded").value(false));
        assertEquals(1, count("select count(*) from xp_events where user_id = ? and source = 'EXERCISE'", s.id()));
        assertEquals(0, balance(s));
    }

    /** Tự học (trừ lượt): lần đầu lỗi hẳn, chấm lại tự động thành công: có điểm, không lý do lỗi, lượt bị trừ đúng một lần. */
    @Test
    void chargedRewriteAutoRetrySucceedsWithoutFailureReason() throws Exception {
        var u = student(1);
        long b = bank(true);
        long rw = question(b, "CONNECTIVE_REWRITE", 1, "Viết lại với however",
                "{\"sentence\":\"It rained. We went out.\",\"requiredWord\":\"however\"}", "{}");
        long attempt = idOf(postJson("/api/banks/" + b + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
        postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers", List.of(Map.of("questionId", rw, "answer", Map.of("text", HOWEVER)))))
                .andExpect(status().isOk());
        fake.failNext(1, new AiCallException(false, "từ chối", null, null));
        fake.override(RewriteGrades.class, new RewriteGrades(List.of(new RewriteGrades.Item(rw, true, "Tốt.", HOWEVER))));
        runAll();

        assertEquals("DONE", jdbc.queryForObject("select status from ai_jobs where attempt_id = ?", String.class, attempt));
        getJson("/api/attempts/" + attempt, u).andExpect(jsonPath("$.score").value(1))
                .andExpect(jsonPath("$.results[0].isCorrect").value(true)).andExpect(jsonPath("$.results[0].aiFailure").doesNotExist());
        assertEquals(0, balance(u));
        assertEquals(1, count("select count(*) from xp_events where user_id = ? and source = 'EXERCISE'", u.id()));
    }

    // ---- D-165 ----

    /** Bài giao bài thi cuối khóa: người nhận làm được dù chưa đủ tỉ lệ học; người ngoài lớp không vào được; lượt không tính vào số lần thi. */
    @Test
    void assignedCourseExamIgnoresUnlockButStaysRecipientOnly() throws Exception {
        var f = fixture("EXAM");
        jdbc.update("update courses set exam_bank_id = ?, exam_unlock_percent = 100, exam_retakes = 0 where id = ?", f.bank(), f.course());
        insert("lessons", Map.of("topic_id", jdbc.queryForObject("select topic_id from question_banks where id = ?", Long.class, f.bank()),
                "title", "Bài học", "type", "CONNECTIVE_VIDEO", "status", "PUBLISHED"));
        var s = member(f, f.cls(), 0);
        var outsider = member(f, 0, 0);
        call(HttpMethod.POST, "/api/banks/" + f.bank() + "/attempts", s, Map.of()).andExpect(r -> assertTrue(r.getResponse().getStatus() >= 400,
                "tự vào thi khi chưa đủ tỉ lệ học")); // đối chứng: điều kiện thi khóa vẫn áp cho lượt tự vào

        long asg = assign(f, f.cls(), f.teacher(), f.bank());
        call(HttpMethod.POST, "/api/classes/" + f.cls() + "/assignments/" + asg + "/start", outsider, null)
                .andExpect(r -> assertTrue(r.getResponse().getStatus() == 403 || r.getResponse().getStatus() == 404,
                        "người ngoài lớp vào được bài giao: " + r.getResponse().getStatus()));
        assertEquals(0, count("select count(*) from student_exercise_attempts where user_id = ?", outsider.id()));

        long attempt = startAssigned(f.cls(), asg, s);
        guard.resetRateLimit(s.id());
        postJson("/api/attempts/" + attempt + "/submit", s, Map.of("answers", List.of())).andExpect(status().isOk());
        getJson("/api/courses/" + f.course() + "/exam", s).andExpect(status().isOk()).andExpect(jsonPath("$.attemptsUsed").value(0));
    }
}
