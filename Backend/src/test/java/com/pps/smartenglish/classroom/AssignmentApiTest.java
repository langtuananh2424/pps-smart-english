package com.pps.smartenglish.classroom;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.classroom.service.AssignmentReminderService;
import com.pps.smartenglish.support.ApiTestBase;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** FR-ASG-01..04, D-121, D-130, NFR-SEC-05: giao bài, làm bài được giao, theo dõi, chấm. */
class AssignmentApiTest extends ApiTestBase {

    private static final String PASSWORD = "mat-khau-1";

    @Autowired AssignmentReminderService reminders;

    /** Khóa có lớp, học tuần tự, hai unit: bankA (unit A, mở), bankB (unit B, bị khóa), bankR (unit B, có câu viết lại cần AI). */
    private record Fx(long course, long classId, long bankA, long bankB, long bankR, long qB, Account teacher) {}

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private Account withRole(String role) throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = ?", u.id(), role);
        return login(u.email(), PASSWORD);
    }

    private ResultActions call(HttpMethod method, String url, String token, Object body) throws Exception {
        var req = MockMvcRequestBuilders.request(method, url);
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return send(req, token);
    }

    private long bank(long topic, String status) {
        return insert("question_banks", Map.of("topic_id", topic, "title", "Bộ " + UUID.randomUUID().toString().substring(0, 4),
                "is_trial", false, "status", status, "mode", "PRACTICE"));
    }

    private long newClass(long course) {
        return insert("classes", Map.of("course_id", course, "name", "Lớp " + UUID.randomUUID().toString().substring(0, 4),
                "starts_on", LocalDate.now().minusDays(10), "ends_on", LocalDate.now().plusDays(60), "mode", "ONLINE", "capacity", 20));
    }

    private Fx fixture() throws Exception {
        long c = insert("courses", Map.of("slug", "asg-" + UUID.randomUUID(), "title", "Khóa giao bài", "status", "PUBLISHED",
                "delivery_mode", "CLASS_BASED", "sequential", true));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED", "sort_order", 0));
        long ta = insert("topics", Map.of("level_id", lv, "title", "Unit A", "is_free_trial", false, "status", "PUBLISHED", "sort_order", 0));
        long tb = insert("topics", Map.of("level_id", lv, "title", "Unit B", "is_free_trial", false, "status", "PUBLISHED", "sort_order", 1));
        long bankA = bank(ta, "PUBLISHED");
        insert("questions", Map.of("bank_id", bankA, "type", "CONNECTIVE_MCQ", "prompt", "Câu A"));
        long bankB = bank(tb, "PUBLISHED");
        long qB = insert("questions", Map.of("bank_id", bankB, "type", "CONNECTIVE_MCQ", "prompt", "Câu B"));
        long bankR = bank(tb, "PUBLISHED");
        var rewrite = new HashMap<String, Object>(Map.of("bank_id", bankR, "type", "CONNECTIVE_REWRITE", "prompt", "Viết lại"));
        rewrite.put("payload", "{\"sentence\":\"It was hard. We won.\",\"requiredWord\":\"although\"}");
        long qR = insert("questions", rewrite);
        jdbc.update("update questions set payload = ? where id = ?", rewrite.get("payload"), qR);
        long cls = newClass(c);
        var teacher = withRole("TEACHER");
        jdbc.update("insert into class_staff (class_id, user_id, role, assigned_from) values (?, ?, 'TEACHER', ?)", cls, teacher.id(), LocalDate.now().minusDays(10));
        return new Fx(c, cls, bankA, bankB, bankR, qB, teacher);
    }

    private Account student(Fx f) throws Exception {
        var s = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', TIMESTAMP '2020-01-01 00:00:00', TIMESTAMP '2099-01-01 00:00:00')",
                s.id(), f.course());
        jdbc.update("insert into class_members (class_id, course_id, user_id, status, live_key, joined_at) values (?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP)",
                f.classId(), f.course(), s.id(), f.course() + ":" + s.id());
        return s;
    }

    private String base(Fx f) {
        return "/api/classes/" + f.classId() + "/assignments";
    }

    private Map<String, Object> body(long bankId, Instant due, List<Long> students) {
        var m = new HashMap<String, Object>();
        m.put("bankId", bankId);
        m.put("dueAt", due.toString());
        if (students != null) m.put("studentIds", students);
        return m;
    }

    private Instant inHours(int h) {
        return Instant.now().plus(h, ChronoUnit.HOURS);
    }

    private long create(Fx f, long bankId, Instant due, List<Long> students) throws Exception {
        String res = call(HttpMethod.POST, base(f), f.teacher().accessToken(), body(bankId, due, students))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("id").asLong();
    }

    private long start(Fx f, long asg, Account s) throws Exception {
        String res = call(HttpMethod.POST, base(f) + "/" + asg + "/start", s.accessToken(), null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("id").asLong();
    }

    private void submitBlank(long attemptId, Account s) throws Exception {
        call(HttpMethod.POST, "/api/attempts/" + attemptId + "/submit", s.accessToken(), Map.of("answers", List.of())).andExpect(status().isOk());
    }

    @Test
    void createValidatesAndBlocksDuplicates() throws Exception {
        var f = fixture();
        var s = student(f);
        var t = f.teacher().accessToken();
        Instant due = inHours(48);

        call(HttpMethod.POST, base(f), t, body(f.bankA(), Instant.now().minusSeconds(60), null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.dueAt").exists());
        long draft = bank(jdbc.queryForObject("select topic_id from question_banks where id = ?", Long.class, f.bankA()), "DRAFT");
        call(HttpMethod.POST, base(f), t, body(draft, due, null)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.bankId").exists());
        var other = fixture();
        call(HttpMethod.POST, base(f), t, body(other.bankA(), due, null)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.bankId").exists());
        call(HttpMethod.POST, base(f), t, body(f.bankA(), due, List.of(999999L))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.studentIds").exists());

        long first = create(f, f.bankA(), due, null);
        call(HttpMethod.POST, base(f), t, body(f.bankA(), due, null)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("assignment_duplicate"));
        create(f, f.bankA(), inHours(72), List.of(s.id())); // khác hạn: được
        call(HttpMethod.POST, base(f) + "/" + first + "/cancel", t, null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        create(f, f.bankA(), due, null); // đã hủy thì giao lại được
        assertEquals(3, count("select count(*) from notifications where user_id = ? and type = 'ASSIGNMENT_NEW'", s.id()),
                "học viên nhận thông báo cho mỗi bài được giao");
    }

    @Test
    void assignedBankOpensLockedUnitOnlyForRecipients() throws Exception {
        var f = fixture();
        var s1 = student(f);
        var s2 = student(f);
        var t = f.teacher().accessToken();
        call(HttpMethod.GET, "/api/banks/" + f.bankB(), s1.accessToken(), null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("unit_locked"));

        long asg = create(f, f.bankB(), inHours(48), List.of(s1.id()));
        call(HttpMethod.GET, "/api/banks/" + f.bankB(), s1.accessToken(), null).andExpect(status().isOk());
        call(HttpMethod.GET, "/api/banks/" + f.bankB(), s2.accessToken(), null).andExpect(status().isForbidden());
        call(HttpMethod.POST, base(f) + "/" + asg + "/start", s2.accessToken(), null).andExpect(status().isNotFound());

        long attempt = start(f, asg, s1);
        assertEquals(attempt, start(f, asg, s1), "bắt đầu lần nữa thì làm tiếp lượt đang dở");
        assertEquals(asg, jdbc.queryForObject("select assignment_id from student_exercise_attempts where id = ?", Long.class, attempt));
        submitBlank(attempt, s1);
        call(HttpMethod.POST, base(f) + "/" + asg + "/start", s1.accessToken(), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("assignment_submitted"));

        call(HttpMethod.POST, base(f) + "/" + asg + "/cancel", t, null).andExpect(status().isOk());
        // D-153: người nhận vẫn xem lại bộ (chỉ đọc); tự học thì khóa unit giữ nguyên, người không nhận vẫn bị chặn
        call(HttpMethod.GET, "/api/banks/" + f.bankB(), s1.accessToken(), null).andExpect(status().isOk());
        call(HttpMethod.GET, "/api/banks/" + f.bankB(), s2.accessToken(), null).andExpect(status().isForbidden());
        call(HttpMethod.POST, "/api/banks/" + f.bankB() + "/attempts", s1.accessToken(), null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("unit_locked"));
        call(HttpMethod.GET, base(f), s1.accessToken(), null).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void trackingShowsSubmittedLateAndOverdue() throws Exception {
        var f = fixture();
        var onTime = student(f);
        var late = student(f);
        var missing = student(f);
        var waiting = student(f);
        long asg = create(f, f.bankA(), inHours(48), null);
        long onTimeAttempt = start(f, asg, onTime);
        submitBlank(onTimeAttempt, onTime);
        jdbc.update("update student_exercise_attempts set submitted_at = TIMESTAMPADD(HOUR, -2, CURRENT_TIMESTAMP) where id = ?", onTimeAttempt);
        // đưa hạn về quá khứ rồi mới nộp: nộp trễ vẫn được chấm và đánh dấu trễ (D-121)
        long lateAttempt = start(f, asg, late);
        jdbc.update("update assignments set due_at = TIMESTAMPADD(HOUR, -1, CURRENT_TIMESTAMP) where id = ?", asg);
        submitBlank(lateAttempt, late);
        var t = f.teacher().accessToken();

        String res = call(HttpMethod.GET, base(f) + "/" + asg + "/progress", t, null).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(4)))
                .andReturn().getResponse().getContentAsString();
        Map<Long, String> byUser = new HashMap<>();
        json.readTree(res).forEach(n -> byUser.put(n.get("userId").asLong(), n.get("status").asText()));
        assertEquals("SUBMITTED", byUser.get(onTime.id()));
        assertEquals("LATE", byUser.get(late.id()));
        assertEquals("OVERDUE", byUser.get(missing.id()));
        assertEquals("OVERDUE", byUser.get(waiting.id()));

        call(HttpMethod.GET, base(f) + "/" + asg + "/mine", late.accessToken(), null).andExpect(jsonPath("$.status").value("LATE"));
        call(HttpMethod.GET, "/api/me/assignments", missing.accessToken(), null).andExpect(jsonPath("$[0].myStatus").value("OVERDUE"));
    }

    @Test
    void teacherGradeIsAppendOnlyAndStudentIsNotified() throws Exception {
        var f = fixture();
        var s = student(f);
        var classmate = student(f);
        long asg = create(f, f.bankA(), inHours(48), null);
        long attempt = start(f, asg, s);
        submitBlank(attempt, s);
        var t = f.teacher().accessToken();
        String url = base(f) + "/" + asg + "/attempts/" + attempt;

        call(HttpMethod.PUT, url + "/grade", t, Map.of()).andExpect(status().isBadRequest());
        call(HttpMethod.PUT, url + "/grade", t, Map.of("score", 99)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.score").exists());
        call(HttpMethod.PUT, url + "/grade", t, Map.of("score", 1, "comment", "Làm tốt")).andExpect(status().isCreated());
        call(HttpMethod.PUT, url + "/grade", t, Map.of("comment", "Chú ý liên từ")).andExpect(status().isCreated());
        call(HttpMethod.GET, url + "/grades", t, null).andExpect(jsonPath("$", hasSize(2)));
        assertEquals(2, count("select count(*) from notifications where user_id = ? and type = 'ASSIGNMENT_GRADED'", s.id()));

        call(HttpMethod.GET, base(f) + "/" + asg + "/mine", s.accessToken(), null)
                .andExpect(jsonPath("$.finalScore").value(1.0)).andExpect(jsonPath("$.teacherComment").value("Chú ý liên từ"));
        call(HttpMethod.GET, base(f) + "/" + asg + "/progress", t, null).andExpect(jsonPath("$[?(@.userId == " + s.id() + ")].finalScore").value(1.0));

        // học viên và giáo viên lớp khác không chấm được
        call(HttpMethod.PUT, url + "/grade", classmate.accessToken(), Map.of("score", 1)).andExpect(status().isNotFound());
        var otherTeacher = withRole("TEACHER");
        var otherClass = newClass(f.course());
        jdbc.update("insert into class_staff (class_id, user_id, role, assigned_from) values (?, ?, 'TEACHER', ?)", otherClass, otherTeacher.id(), LocalDate.now().minusDays(1));
        call(HttpMethod.PUT, url + "/grade", otherTeacher.accessToken(), Map.of("score", 1)).andExpect(status().isNotFound());
        call(HttpMethod.PUT, "/api/classes/" + otherClass + "/assignments/" + asg + "/attempts/" + attempt + "/grade", otherTeacher.accessToken(), Map.of("score", 1))
                .andExpect(status().isNotFound());
        call(HttpMethod.GET, base(f) + "/" + asg + "/progress", otherTeacher.accessToken(), null).andExpect(status().isNotFound());
        call(HttpMethod.POST, base(f), otherTeacher.accessToken(), body(f.bankA(), inHours(10), null)).andExpect(status().isNotFound());
    }

    @Test
    void assignedRewriteDoesNotChargeAiQuota() throws Exception {
        var f = fixture();
        var s = student(f);
        long asg = create(f, f.bankR(), inHours(48), null);
        long attempt = start(f, asg, s);
        long qr = jdbc.queryForObject("select id from questions where bank_id = ?", Long.class, f.bankR());
        call(HttpMethod.POST, "/api/attempts/" + attempt + "/submit", s.accessToken(),
                Map.of("answers", List.of(Map.of("questionId", qr, "answer", Map.of("text", "Although it was hard, we won.")))))
                .andExpect(status().isOk());
        var jobs = jdbc.queryForList("select charged from ai_jobs where attempt_id = ?", attempt);
        assertEquals(1, jobs.size(), "câu viết lại được đưa vào hàng đợi AI");
        assertEquals(false, jobs.get(0).get("CHARGED"));
    }

    @Test
    void dueReminderGoesOnlyToThoseWhoHaveNotSubmitted() throws Exception {
        var f = fixture();
        var done = student(f);
        var pending = student(f);
        long asg = create(f, f.bankA(), Instant.now().plus(2, ChronoUnit.HOURS), null);
        submitBlank(start(f, asg, done), done);
        // chỉ kiểm phần của hai học viên này; job có thể gồm bài của test khác
        reminders.sendDue();
        assertEquals(1, count("select count(*) from notifications where user_id = ? and type = 'ASSIGNMENT_DUE'", pending.id()));
        assertEquals(0, count("select count(*) from notifications where user_id = ? and type = 'ASSIGNMENT_DUE'", done.id()));
        reminders.sendDue();
        assertEquals(1, count("select count(*) from notifications where user_id = ? and type = 'ASSIGNMENT_DUE'", pending.id()));
    }
}
