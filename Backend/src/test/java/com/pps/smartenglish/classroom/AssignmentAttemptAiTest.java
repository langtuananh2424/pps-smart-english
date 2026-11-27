package com.pps.smartenglish.classroom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.ai.AiTestSupport;
import com.pps.smartenglish.ai.dto.WritingGrade;
import com.pps.smartenglish.ai.provider.AiCallException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** D-130, D-147: phiên Viết trong lượt của bài giao dạng bộ bài không trừ lượt AI; điểm tạm không lộ ở kết quả của tôi. */
class AssignmentAttemptAiTest extends AiTestSupport {

    private static final String ESSAY = "I agree with online learning because it is flexible. However, students need discipline.";

    private record Fx(long classId, long bank, long q, Account teacher) {}

    private ResultActions call(HttpMethod method, String url, Account who, Object body) throws Exception {
        var req = MockMvcRequestBuilders.request(method, url);
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return send(req, who.accessToken());
    }

    private Fx fixture() throws Exception {
        long c = insert("courses", Map.of("slug", "asg-" + UUID.randomUUID(), "title", "Khóa giao bài", "status", "PUBLISHED",
                "delivery_mode", "CLASS_BASED", "sequential", false));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED", "sort_order", 0));
        long t = insert("topics", Map.of("level_id", lv, "title", "Unit A", "is_free_trial", false, "status", "PUBLISHED", "sort_order", 0));
        long bank = insert("question_banks", Map.of("topic_id", t, "title", "Bộ giao", "is_trial", false, "status", "PUBLISHED", "mode", "PRACTICE"));
        long q = writingQuestion(bank);
        long cls = insert("classes", Map.of("course_id", c, "name", "Lớp " + UUID.randomUUID().toString().substring(0, 4),
                "starts_on", LocalDate.now().minusDays(10), "ends_on", LocalDate.now().plusDays(60), "mode", "ONLINE", "capacity", 20));
        var teacher = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = 'TEACHER'", teacher.id());
        teacher = login(teacher.email(), PASSWORD);
        jdbc.update("insert into class_staff (class_id, user_id, role, assigned_from) values (?, ?, 'TEACHER', ?)", cls, teacher.id(), LocalDate.now().minusDays(10));
        return new Fx(cls, bank, q, teacher);
    }

    private Account enrolled(Fx f, int quota) throws Exception {
        var s = student(quota);
        long course = jdbc.queryForObject("select course_id from classes where id = ?", Long.class, f.classId());
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', TIMESTAMP '2020-01-01 00:00:00', TIMESTAMP '2099-01-01 00:00:00')",
                s.id(), course);
        jdbc.update("insert into class_members (class_id, course_id, user_id, status, live_key, joined_at) values (?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP)",
                f.classId(), course, s.id(), course + ":" + s.id());
        return s;
    }

    private long assign(Fx f) throws Exception {
        var body = Map.of("bankId", f.bank(), "dueAt", Instant.now().plus(48, ChronoUnit.HOURS).toString());
        return idOf(call(HttpMethod.POST, "/api/classes/" + f.classId() + "/assignments", f.teacher(), body).andExpect(status().isCreated()), "id");
    }

    private static WritingGrade grade(int pct) {
        return new WritingGrade(pct, pct, pct, pct, List.of(), "fixed text", new WritingGrade.Connectives(2, 1, 2, List.of(), "ok"), List.of(), null, "fb");
    }

    /** Học viên hết lượt AI vẫn nộp chấm được phiên trong lượt của bài giao; job không trừ lượt, kết quả của tôi không lộ điểm tạm. */
    @Test
    void sessionInAssignedAttemptIsFreeAndMyResultHidesProvisionalScore() throws Exception {
        var f = fixture();
        var s = enrolled(f, 0);
        long asg = assign(f);
        long attempt = idOf(call(HttpMethod.POST, "/api/classes/" + f.classId() + "/assignments/" + asg + "/start", s, null).andExpect(status().isOk()), "id");
        guard.resetRateLimit(s.id());
        long session = idOf(postJson("/api/writing/sessions", s, Map.of("questionId", f.q(), "attemptId", attempt)).andExpect(status().isCreated()), "sessionId");
        guard.resetRateLimit(s.id());
        postJson("/api/writing/sessions/" + session + "/submit", s, Map.of("finalText", ESSAY), UUID.randomUUID().toString()).andExpect(status().isAccepted());
        assertEquals(0, balance(s));
        assertEquals(0, count("select count(*) from ai_jobs where attempt_id = ? and charged = true", attempt));

        postJson("/api/attempts/" + attempt + "/submit", s, Map.of("answers", List.of())).andExpect(status().isOk());
        String mine = "/api/classes/" + f.classId() + "/assignments/" + asg + "/mine";
        call(HttpMethod.GET, mine, s, null).andExpect(jsonPath("$.aiScore").doesNotExist()).andExpect(jsonPath("$.finalScore").doesNotExist());
        fake.override(WritingGrade.class, grade(80));
        assertEquals(1, runner.runDueNow());
        call(HttpMethod.GET, mine, s, null).andExpect(jsonPath("$.aiScore").value(0.8));
    }

    /** Chấm lỗi cả hai lần ở bài giao: không có lượt để hoàn, câu tính 0 và cờ aiRefunded là false. */
    @Test
    void failureInAssignedAttemptIsNotReportedAsRefunded() throws Exception {
        var f = fixture();
        var s = enrolled(f, 0);
        long asg = assign(f);
        long attempt = idOf(call(HttpMethod.POST, "/api/classes/" + f.classId() + "/assignments/" + asg + "/start", s, null).andExpect(status().isOk()), "id");
        guard.resetRateLimit(s.id());
        long session = idOf(postJson("/api/writing/sessions", s, Map.of("questionId", f.q(), "attemptId", attempt)).andExpect(status().isCreated()), "sessionId");
        guard.resetRateLimit(s.id());
        postJson("/api/writing/sessions/" + session + "/submit", s, Map.of("finalText", ESSAY), UUID.randomUUID().toString()).andExpect(status().isAccepted());
        postJson("/api/attempts/" + attempt + "/submit", s, Map.of("answers", List.of())).andExpect(status().isOk());
        fake.failNext(2, new AiCallException(false, "từ chối", null, null));
        assertEquals(1, runner.runDueNow());
        assertEquals(1, runner.runDueNow());
        assertEquals(0, balance(s));
        getJson("/api/attempts/" + attempt, s).andExpect(jsonPath("$.results[0].aiFailure").value("Cam chưa chấm được câu này"))
                .andExpect(jsonPath("$.results[0].aiRefunded").value(false));
    }

    private String startUrl(Fx f, long asg) {
        return "/api/classes/" + f.classId() + "/assignments/" + asg + "/start";
    }

    /** D-147: mỗi bộ một lượt mở, tính chung tự học và bài giao; vào lại bài giao đang dở thì mở đúng lượt cũ. */
    @Test
    void oneOpenAttemptPerBankAcrossSelfStudyAndAssignment() throws Exception {
        var f = fixture();
        var s = enrolled(f, 5);
        long asg = assign(f);

        long self = idOf(postJson("/api/banks/" + f.bank() + "/attempts", s, Map.of()).andExpect(status().isCreated()), "id");
        call(HttpMethod.POST, startUrl(f, asg), s, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("attempt_in_progress")).andExpect(jsonPath("$.error.details.attemptId").value(self));

        postJson("/api/attempts/" + self + "/submit", s, Map.of("answers", List.of())).andExpect(status().isOk());
        long assigned = idOf(call(HttpMethod.POST, startUrl(f, asg), s, null).andExpect(status().isOk()), "id");
        assertEquals(assigned, idOf(call(HttpMethod.POST, startUrl(f, asg), s, null).andExpect(status().isOk()), "id")); // vào lại: lượt cũ

        postJson("/api/banks/" + f.bank() + "/attempts", s, Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("attempt_in_progress")).andExpect(jsonPath("$.error.details.attemptId").value(assigned));
    }
}
