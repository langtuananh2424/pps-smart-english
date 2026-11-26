package com.pps.smartenglish.classroom;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.ai.AiTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Tester (FR-ASG-02, D-130): lọc Viết/Nói theo bài giao không lộ dữ liệu người khác; danh sách bài của tôi đủ bài chưa nộp + 50 đã nộp. */
class AssignmentMineTesterTest extends AiTestSupport {

    private record Fx(long classId, long courseId, long bank, long qW, long qS, Account teacher) {}

    private Fx fixture() throws Exception {
        long c = insert("courses", Map.of("slug", "mt-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED",
                "delivery_mode", "CLASS_BASED", "sequential", false));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED", "sort_order", 0));
        long t = insert("topics", Map.of("level_id", lv, "title", "Unit", "is_free_trial", false, "status", "PUBLISHED", "sort_order", 0));
        long bank = insert("question_banks", Map.of("topic_id", t, "title", "Bộ", "is_trial", false, "status", "PUBLISHED", "mode", "PRACTICE"));
        long qW = writingQuestion(bank);
        long qS = question(bank, "SPEAKING", 2, "Describe a trip.", "{\"part\":1,\"prompt\":\"Describe a trip.\"}", "{}");
        long cls = insert("classes", Map.of("course_id", c, "name", "Lớp", "starts_on", LocalDate.now().minusDays(10),
                "ends_on", LocalDate.now().plusDays(600), "mode", "ONLINE", "capacity", 20));
        var tu = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = 'TEACHER'", tu.id());
        var teacher = login(tu.email(), PASSWORD);
        jdbc.update("insert into class_staff (class_id, user_id, role, assigned_from) values (?, ?, 'TEACHER', ?)", cls, teacher.id(), LocalDate.now().minusDays(10));
        return new Fx(cls, c, bank, qW, qS, teacher);
    }

    private Account member(Fx f) throws Exception {
        var s = student(3);
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', TIMESTAMP '2020-01-01 00:00:00', TIMESTAMP '2099-01-01 00:00:00')", s.id(), f.courseId());
        jdbc.update("insert into class_members (class_id, course_id, user_id, status, live_key, joined_at) values (?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP)",
                f.classId(), f.courseId(), s.id(), f.courseId() + ":" + s.id());
        return s;
    }

    /** Bài giao cả bộ, chèn thẳng DB (nhanh, không qua giới hạn tạo). */
    private long rawAssignment(Fx f, Instant due) {
        return insert("assignments", Map.of("class_id", f.classId(), "bank_id", f.bank(), "due_at", Timestamp.from(due),
                "status", "ACTIVE", "created_by", f.teacher().id()));
    }

    private void rawSubmit(long asg, Fx f, Account s) {
        insert("student_exercise_attempts", Map.of("user_id", s.id(), "bank_id", f.bank(), "assignment_id", asg,
                "submitted_at", Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)), "score", 1, "max_score", 1));
    }

    private List<Long> mineIds(Account s) throws Exception {
        String body = getJson("/api/me/assignments", s).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Long> out = new ArrayList<>();
        json.readTree(body).forEach(n -> out.add(n.get("id").asLong()));
        return out;
    }

    private long startWriting(Account s, long q, Long asg) throws Exception {
        guard.resetRateLimit(s.id());
        var body = new HashMap<String, Object>();
        body.put("questionId", q);
        body.put("assignmentId", asg);
        return idOf(postJson("/api/writing/sessions", s, body).andExpect(status().isCreated()), "sessionId");
    }

    private long startSpeaking(Account s, long q, Long asg) throws Exception {
        guard.resetRateLimit(s.id());
        var body = new HashMap<String, Object>();
        body.put("questionId", q);
        body.put("assignmentId", asg);
        return idOf(postJson("/api/speaking/submissions", s, body).andExpect(status().isCreated()), "submissionId");
    }

    private long aiAssignment(Fx f, long q, List<Long> students) throws Exception {
        var m = new HashMap<String, Object>();
        m.put("questionId", q);
        m.put("dueAt", Instant.now().plus(48, ChronoUnit.HOURS).toString());
        if (students != null) m.put("studentIds", students);
        return idOf(postJson("/api/classes/" + f.classId() + "/assignments", f.teacher(), m).andExpect(status().isCreated()), "id");
    }

    @Test
    void writingFilterNeverShowsAnotherUsersSessionsOfSameAssignment() throws Exception {
        var f = fixture();
        var a = member(f);
        var b = member(f);
        long asg = aiAssignment(f, f.qW(), null);
        long aSess = startWriting(a, f.qW(), asg);
        long bSess = startWriting(b, f.qW(), asg);
        long bPractice = startWriting(b, f.qW(), null);

        getJson("/api/me/writing?assignmentId=" + asg, a).andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1))).andExpect(jsonPath("$.data[0].sessionId").value(aSess));
        getJson("/api/me/writing?questionId=" + f.qW() + "&assignmentId=" + asg + "&limit=50", b)
                .andExpect(jsonPath("$.data", hasSize(1))).andExpect(jsonPath("$.data[0].sessionId").value(bSess));
        // Người ngoài lớp (không nhận bài) dò assignmentId: rỗng, không lộ phiên của a/b
        var stranger = student(3);
        getJson("/api/me/writing?assignmentId=" + asg, stranger).andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(0)));
        // Không có tham số: hành vi cũ, gồm cả phiên tự luyện và phiên bài giao của chính mình
        getJson("/api/me/writing?questionId=" + f.qW(), b).andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].sessionId").value(bPractice)).andExpect(jsonPath("$.data[1].sessionId").value(bSess))
                .andExpect(jsonPath("$.data[0].assignmentId").doesNotExist());
        getJson("/api/me/writing?assignmentId=", b).andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(2)));
        getJson("/api/me/writing?assignmentId=abc", b).andExpect(status().isNotFound());
        int neg = getJson("/api/me/writing?assignmentId=-1", b).andReturn().getResponse().getStatus();
        assertTrue(neg == 404 || neg == 200, "assignmentId âm: " + neg);
        if (neg == 200) getJson("/api/me/writing?assignmentId=-1", b).andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void speakingFilterOnAssignmentNotMineReturnsNothing() throws Exception {
        var f = fixture();
        var a = member(f);
        var b = member(f);
        long onlyA = aiAssignment(f, f.qS(), List.of(a.id()));
        long aSub = startSpeaking(a, f.qS(), onlyA);
        startSpeaking(b, f.qS(), null);

        getJson("/api/me/speaking?assignmentId=" + onlyA, a).andExpect(jsonPath("$.data", hasSize(1))).andExpect(jsonPath("$.data[0].id").value(aSub));
        // b cùng lớp nhưng không phải người nhận: không thấy lượt của a, cũng không rơi về lượt tự luyện của b
        getJson("/api/me/speaking?questionId=" + f.qS() + "&assignmentId=" + onlyA, b).andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(0)));
        getJson("/api/me/speaking?questionId=" + f.qS(), b).andExpect(jsonPath("$.data", hasSize(1)));
        // b cũng không tạo được lượt gắn bài không thuộc mình
        guard.resetRateLimit(b.id());
        var body = new HashMap<String, Object>();
        body.put("questionId", f.qS());
        body.put("assignmentId", onlyA);
        int code = postJson("/api/speaking/submissions", b, body).andReturn().getResponse().getStatus();
        assertTrue(code == 404 || code == 403, "b tạo được lượt gắn bài của a: " + code);
        assertEquals(0, count("select count(*) from speaking_submissions where user_id = ? and assignment_id = ?", b.id(), onlyA));
    }

    @Test
    void mineKeepsEveryUnsubmittedPlusFiftyNewestSubmitted() throws Exception {
        var f = fixture();
        var s = member(f);
        Instant base = Instant.now().plus(1, ChronoUnit.DAYS);
        List<Long> submitted = new ArrayList<>();
        List<Long> todo = new ArrayList<>();
        // 55 bài đã nộp hạn xa hơn (mới hơn) xen kẽ 3 bài chưa nộp: bài chưa nộp hạn gần nhất phải còn
        todo.add(rawAssignment(f, base));
        for (int i = 1; i <= 55; i++) {
            long id = rawAssignment(f, base.plus(i, ChronoUnit.HOURS));
            rawSubmit(id, f, s);
            submitted.add(id);
            if (i == 30) todo.add(rawAssignment(f, base.plus(30 * 60 + 1, ChronoUnit.MINUTES)));
        }
        todo.add(rawAssignment(f, base.plus(100, ChronoUnit.HOURS)));

        List<Long> ids = mineIds(s);
        assertTrue(ids.containsAll(todo), "thiếu bài chưa nộp: " + todo + " / " + ids);
        assertEquals(53, ids.size());
        assertTrue(ids.containsAll(submitted.subList(5, 55)), "phải giữ 50 bài đã nộp hạn mới nhất");
        submitted.subList(0, 5).forEach(id -> assertTrue(!ids.contains(id), "bài đã nộp cũ " + id + " phải bị cắt"));
        assertEquals(todo.get(0), ids.get(0), "hạn gần nhất trước");
        assertEquals(todo.get(2), ids.get(ids.size() - 1));
    }

    @Test
    void mineOneHidesCancelledAndLeftClass() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = rawAssignment(f, Instant.now().plus(2, ChronoUnit.DAYS));
        getJson("/api/me/assignments/" + asg, s).andExpect(status().isOk()).andExpect(jsonPath("$.myStatus").value("NOT_SUBMITTED"));
        getJson("/api/me/assignments/" + asg, f.teacher()).andExpect(status().isNotFound());
        jdbc.update("update class_members set status = 'WITHDRAWN', live_key = null where user_id = ? and class_id = ?", s.id(), f.classId());
        getJson("/api/me/assignments/" + asg, s).andExpect(status().isNotFound());
        jdbc.update("update class_members set status = 'ACTIVE', live_key = course_id || ':' || user_id where user_id = ? and class_id = ?", s.id(), f.classId());
        // D-153: bài đã hủy vẫn xem được (status CANCELLED) với người nhận; người khác vẫn 404; không bắt đầu được; không có trong danh sách
        jdbc.update("update assignments set status = 'CANCELLED' where id = ?", asg);
        getJson("/api/me/assignments/" + asg, s).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        getJson("/api/me/assignments/" + asg, f.teacher()).andExpect(status().isNotFound());
        getJson("/api/classes/" + f.classId() + "/assignments/" + asg + "/mine", s).andExpect(status().isOk());
        postJson("/api/classes/" + f.classId() + "/assignments/" + asg + "/start", s, Map.of())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("assignment_cancelled"));
        assertTrue(!mineIds(s).contains(asg), "bài hủy không hiện trong danh sách");
    }
}
