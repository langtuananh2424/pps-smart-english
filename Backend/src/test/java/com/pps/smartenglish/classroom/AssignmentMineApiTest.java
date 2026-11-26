package com.pps.smartenglish.classroom;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.ai.AiTestSupport;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** FR-ASG-02, D-130: một bài của tôi (/api/me/assignments/{id}) và lọc phiên Viết/lượt Nói theo bài giao. */
class AssignmentMineApiTest extends AiTestSupport {

    private record Fx(long classId, long qW, long qS, Account teacher) {}

    private Fx fixture() throws Exception {
        long c = insert("courses", Map.of("slug", "mine-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED",
                "delivery_mode", "CLASS_BASED", "sequential", false));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED", "sort_order", 0));
        long t = insert("topics", Map.of("level_id", lv, "title", "Unit", "is_free_trial", false, "status", "PUBLISHED", "sort_order", 0));
        long bank = insert("question_banks", Map.of("topic_id", t, "title", "Bộ", "is_trial", false, "status", "PUBLISHED", "mode", "PRACTICE"));
        long qW = writingQuestion(bank);
        long qS = question(bank, "SPEAKING", 2, "Describe a trip.", "{\"part\":1,\"prompt\":\"Describe a trip.\"}", "{}");
        long cls = insert("classes", Map.of("course_id", c, "name", "Lớp", "starts_on", LocalDate.now().minusDays(10),
                "ends_on", LocalDate.now().plusDays(60), "mode", "ONLINE", "capacity", 20));
        var tu = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = 'TEACHER'", tu.id());
        var teacher = login(tu.email(), PASSWORD);
        jdbc.update("insert into class_staff (class_id, user_id, role, assigned_from) values (?, ?, 'TEACHER', ?)", cls, teacher.id(), LocalDate.now().minusDays(10));
        return new Fx(cls, qW, qS, teacher);
    }

    private Account member(Fx f) throws Exception {
        var s = student(3);
        long course = jdbc.queryForObject("select course_id from classes where id = ?", Long.class, f.classId());
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', TIMESTAMP '2020-01-01 00:00:00', TIMESTAMP '2099-01-01 00:00:00')", s.id(), course);
        jdbc.update("insert into class_members (class_id, course_id, user_id, status, live_key, joined_at) values (?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP)",
                f.classId(), course, s.id(), course + ":" + s.id());
        return s;
    }

    private long assign(Fx f, long questionId, List<Long> students) throws Exception {
        var m = new HashMap<String, Object>();
        m.put("questionId", questionId);
        m.put("dueAt", Instant.now().plus(48, ChronoUnit.HOURS).toString());
        if (students != null) m.put("studentIds", students);
        var req = MockMvcRequestBuilders.request(HttpMethod.POST, "/api/classes/" + f.classId() + "/assignments")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(m));
        return idOf(send(req, f.teacher().accessToken()).andExpect(status().isCreated()), "id");
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

    @Test
    void myAssignmentReturnsOnlyRecipientsOwn() throws Exception {
        var f = fixture();
        var s = member(f);
        var other = member(f);
        long asg = assign(f, f.qW(), List.of(s.id()));
        getJson("/api/me/assignments/" + asg, s).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(asg))
                .andExpect(jsonPath("$.classId").value(f.classId())).andExpect(jsonPath("$.myStatus").value("NOT_SUBMITTED"));
        getJson("/api/me/assignments/" + asg, other).andExpect(status().isNotFound());
        getJson("/api/me/assignments/999999", s).andExpect(status().isNotFound());
        getJson("/api/me/assignments/abc", s).andExpect(status().isNotFound());
    }

    @Test
    void writingListFiltersByAssignmentAndOwner() throws Exception {
        var f = fixture();
        var s = member(f);
        var other = member(f);
        long asg = assign(f, f.qW(), null);
        long practice = startWriting(s, f.qW(), null);
        long mine = startWriting(s, f.qW(), asg);
        startWriting(other, f.qW(), asg);

        getJson("/api/me/writing?questionId=" + f.qW() + "&assignmentId=" + asg + "&limit=1", s).andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1))).andExpect(jsonPath("$.data[0].sessionId").value(mine))
                .andExpect(jsonPath("$.data[0].assignmentId").value(asg));
        getJson("/api/me/writing?questionId=" + f.qW(), s).andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[1].sessionId").value(practice));
        getJson("/api/me/writing?assignmentId=999999", s).andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void speakingListFiltersByAssignmentAndOwner() throws Exception {
        var f = fixture();
        var s = member(f);
        var other = member(f);
        long asg = assign(f, f.qS(), null);
        long practice = startSpeaking(s, f.qS(), null);
        long mine = startSpeaking(s, f.qS(), asg);
        startSpeaking(other, f.qS(), asg);

        getJson("/api/me/speaking?questionId=" + f.qS() + "&assignmentId=" + asg + "&limit=1", s).andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1))).andExpect(jsonPath("$.data[0].id").value(mine))
                .andExpect(jsonPath("$.data[0].assignmentId").value(asg));
        getJson("/api/me/speaking?questionId=" + f.qS(), s).andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[1].id").value(practice));
        assertEquals(1, count("select count(*) from speaking_submissions where user_id = ? and assignment_id is null", s.id()));
    }
}
