package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.classroom.service.AssignmentReminderService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** #69, D-143, D-142: bài giao Nghe ép làm lại không giới hạn; bảng tiến độ, mine, nhắc hạn, chặn sửa điểm. */
class ForcedAssignmentApiTest extends ListeningTestSupport {

    @Autowired AssignmentReminderService reminders;

    private record Fx(World w, long classId, Account teacher) {}

    private Fx fixture(String playback) throws Exception {
        var w = world(playback, null);
        jdbc.update("update courses set delivery_mode = 'CLASS_BASED' where id = ?", w.course());
        long cls = insert("classes", Map.of("course_id", w.course(), "name", "Lớp nghe", "starts_on", LocalDate.now().minusDays(1),
                "ends_on", LocalDate.now().plusDays(60), "mode", "ONLINE", "capacity", 20));
        var teacher = withRole("TEACHER");
        jdbc.update("insert into class_staff (class_id, user_id, role, assigned_from) values (?, ?, 'TEACHER', ?)", cls, teacher.id(),
                LocalDate.now().minusDays(2));
        return new Fx(w, cls, teacher);
    }

    private Account member(Fx f) throws Exception {
        var s = student(0);
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', "
                + "TIMESTAMP '2020-01-01 00:00:00', TIMESTAMP '2099-01-01 00:00:00')", s.id(), f.w().course());
        jdbc.update("insert into class_members (class_id, course_id, user_id, status, live_key, joined_at) values (?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP)",
                f.classId(), f.w().course(), s.id(), f.w().course() + ":" + s.id());
        return s;
    }

    private String base(Fx f) {
        return "/api/classes/" + f.classId() + "/assignments";
    }

    private ResultActions call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req, Account a, Object body) throws Exception {
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return send(req, a.accessToken());
    }

    private long assign(Fx f, Instant due) throws Exception {
        var res = call(MockMvcRequestBuilders.post(base(f)), f.teacher(), Map.of("bankId", f.w().bank(), "dueAt", due.toString()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("id").asLong();
    }

    private long startAssigned(Fx f, long asg, Account s) throws Exception {
        var res = call(MockMvcRequestBuilders.post(base(f) + "/" + asg + "/start"), s, null).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
        return json.readTree(res).get("id").asLong();
    }

    private JsonNode submitAttempt(Fx f, long attempt, Account s, boolean... correct) throws Exception {
        return json.readTree(postJson("/api/attempts/" + attempt + "/submit", s, Map.of("answers", answers(f.w(), correct)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode rowOf(Fx f, long asg, long userId) throws Exception {
        var rows = json.readTree(call(MockMvcRequestBuilders.get(base(f) + "/" + asg + "/progress"), f.teacher(), null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode r : rows) {
            if (r.get("userId").asLong() == userId) {
                return r;
            }
        }
        throw new AssertionError("không thấy học viên " + userId);
    }

    @Test
    void retakeIsUnlimitedAndMilestoneCountsPerAssignment() throws Exception {
        var f = fixture("LOCKED");
        var admin = withRole("ADMIN");
        put("/api/admin/cms/banks/" + f.w().bank() + "/forced-listening", admin, Map.of("hintEveryN", 2, "wrongThreshold", 1)).andExpect(status().isOk());
        var s = member(f);
        submit(s, f.w(), false, false, false, false); // tự học trước: không được tính vào mốc của bài giao
        long asg = assign(f, Instant.now().plus(48, ChronoUnit.HOURS));

        long a1 = startAssigned(f, asg, s);
        assertEquals(a1, startAssigned(f, asg, s), "lượt đang dở thì làm tiếp");
        var first = submitAttempt(f, a1, s, false, true, true, true);
        assertEquals(1, first.at("/forced/attemptNo").asInt());
        assertFalse(first.at("/forced").has("hardQuestions"));

        long a2 = startAssigned(f, asg, s); // đã nộp một lần vẫn làm lại được
        assertNotEquals(a1, a2);
        var second = submitAttempt(f, a2, s, false, true, true, true);
        assertEquals(2, second.at("/forced/attemptNo").asInt(), "mốc N tính theo bài giao, không tính lượt tự học");
        assertEquals(1, second.at("/forced/hardQuestions").size());
        assertEquals(2, second.at("/forced/hardQuestions/0/wrongCount").asInt());
        getJson("/api/attempts/" + a2 + "/scripts/" + f.w().q()[0], s).andExpect(status().isOk());

        long a3 = startAssigned(f, asg, s);
        assertFalse(submitAttempt(f, a3, s, true, true, true, true).at("/forced").has("hardQuestions"));
        assertEquals(3, count("select count(*) from student_exercise_attempts where assignment_id = ? and user_id = ?", asg, s.id()));
    }

    @Test
    void submissionStatusFollowsTheFirstAttemptAndLateIsJudgedOnIt() throws Exception {
        var f = fixture("LOCKED");
        var s = member(f);
        long asg = assign(f, Instant.now().plus(48, ChronoUnit.HOURS));
        long a1 = startAssigned(f, asg, s);
        submitAttempt(f, a1, s, false, false, false, false);
        // hạn dời về quá khứ sau lượt đầu: lượt đầu vẫn đúng hạn, lượt làm lại sau hạn không làm bài thành trễ
        jdbc.update("update assignments set due_at = TIMESTAMPADD(HOUR, -1, CURRENT_TIMESTAMP) where id = ?", asg);
        jdbc.update("update student_exercise_attempts set submitted_at = TIMESTAMPADD(HOUR, -3, CURRENT_TIMESTAMP) where id = ?", a1);
        long a2 = startAssigned(f, asg, s);
        submitAttempt(f, a2, s, true, true, true, true);

        var row = rowOf(f, asg, s.id());
        assertEquals("SUBMITTED", row.get("status").asText());
        assertEquals(a1, row.get("attemptId").asLong());
        call(MockMvcRequestBuilders.get(base(f) + "/" + asg + "/mine"), s, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED")).andExpect(jsonPath("$.attemptId").value(a1))
                .andExpect(jsonPath("$.aiScore").doesNotExist()).andExpect(jsonPath("$.finalScore").doesNotExist());

        // lượt đầu nộp trễ thì trễ, dù lượt sau thế nào
        var late = member(f);
        long l1 = startAssigned(f, asg, late);
        submitAttempt(f, l1, late, true, true, true, true);
        assertEquals("LATE", rowOf(f, asg, late.id()).get("status").asText());
    }

    @Test
    void teacherSeesLatestAttemptAttemptCountAndEverPassedWithoutScores() throws Exception {
        var f = fixture("LOCKED");
        var s = member(f);
        var failedLater = member(f);
        var onlyFailed = member(f);
        var idle = member(f);
        long asg = assign(f, Instant.now().plus(48, ChronoUnit.HOURS));
        // luật mặc định: cả bài 70%
        submitAttempt(f, startAssigned(f, asg, s), s, false, false, false, false);
        long best = startAssigned(f, asg, s);
        submitAttempt(f, best, s, true, true, true, true);
        submitAttempt(f, startAssigned(f, asg, failedLater), failedLater, true, true, true, true);
        long last = startAssigned(f, asg, failedLater);
        submitAttempt(f, last, failedLater, true, false, false, false);
        submitAttempt(f, startAssigned(f, asg, onlyFailed), onlyFailed, true, false, false, false);

        var r1 = rowOf(f, asg, s.id());
        assertEquals(2, r1.at("/forced/attemptCount").asInt());
        assertEquals(best, r1.at("/forced/latestAttemptId").asLong());
        assertEquals(4, r1.at("/forced/latestCorrect").asInt());
        assertEquals(4, r1.at("/forced/latestTotal").asInt());
        assertTrue(r1.at("/forced/latestPassed").asBoolean());
        assertTrue(r1.at("/forced/everPassed").asBoolean());
        assertTrue(r1.path("aiScore").isNull() && r1.path("maxScore").isNull() && r1.path("finalScore").isNull(), r1.toString());

        var r2 = rowOf(f, asg, failedLater.id());
        assertEquals(last, r2.at("/forced/latestAttemptId").asLong());
        assertEquals(1, r2.at("/forced/latestCorrect").asInt());
        assertFalse(r2.at("/forced/latestPassed").asBoolean());
        assertTrue(r2.at("/forced/everPassed").asBoolean(), "từng đạt ở lượt trước");

        var r3 = rowOf(f, asg, onlyFailed.id());
        assertEquals(1, r3.at("/forced/attemptCount").asInt());
        assertFalse(r3.at("/forced/everPassed").asBoolean());

        var r4 = rowOf(f, asg, idle.id());
        assertEquals("NOT_SUBMITTED", r4.get("status").asText());
        assertTrue(r4.path("forced").isNull());
    }

    @Test
    void reminderSkipsStudentsWithAnySubmittedAttempt() throws Exception {
        var f = fixture("LOCKED");
        var done = member(f);
        var pending = member(f);
        long asg = assign(f, Instant.now().plus(2, ChronoUnit.HOURS));
        submitAttempt(f, startAssigned(f, asg, done), done, false, false, false, false);
        reminders.sendDue();
        assertEquals(0, count("select count(*) from notifications where user_id = ? and type = 'ASSIGNMENT_DUE'", done.id()));
        assertEquals(1, count("select count(*) from notifications where user_id = ? and type = 'ASSIGNMENT_DUE'", pending.id()));
    }

    @Test
    void gradingAnAssignedForcedListeningAttemptIsRejected() throws Exception {
        var f = fixture("LOCKED");
        var s = member(f);
        long asg = assign(f, Instant.now().plus(48, ChronoUnit.HOURS));
        long a1 = startAssigned(f, asg, s);
        submitAttempt(f, a1, s, true, true, true, true);
        call(MockMvcRequestBuilders.put(base(f) + "/" + asg + "/attempts/" + a1 + "/grade"), f.teacher(), Map.of("score", 3, "comment", "Tốt"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("grade_not_supported"));
        assertEquals(0, count("select count(*) from grade_overrides where attempt_id = ?", a1));
    }

    @Test
    void teacherOfAnotherClassIsBlocked() throws Exception {
        var f = fixture("LOCKED");
        var other = fixture("LOCKED");
        var s = member(f);
        long asg = assign(f, Instant.now().plus(48, ChronoUnit.HOURS));
        long a1 = startAssigned(f, asg, s);
        submitAttempt(f, a1, s, true, true, true, true);
        call(MockMvcRequestBuilders.get(base(f) + "/" + asg + "/progress"), other.teacher(), null).andExpect(status().is4xxClientError());
        call(MockMvcRequestBuilders.put(base(f) + "/" + asg + "/attempts/" + a1 + "/grade"), other.teacher(), Map.of("score", 1))
                .andExpect(status().is4xxClientError());
        // học viên lớp khác không bắt đầu được bài giao này
        call(MockMvcRequestBuilders.post(base(f) + "/" + asg + "/start"), member(other), null).andExpect(status().is4xxClientError());
    }

    @Test
    void ordinaryAssignmentStillAllowsOnlyOneSubmission() throws Exception {
        var f = fixture("FREE");
        var s = member(f);
        long asg = assign(f, Instant.now().plus(48, ChronoUnit.HOURS));
        long a1 = startAssigned(f, asg, s);
        submitAttempt(f, a1, s, true, true, true, true);
        call(MockMvcRequestBuilders.post(base(f) + "/" + asg + "/start"), s, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("assignment_submitted"));
        var row = rowOf(f, asg, s.id());
        assertTrue(row.path("forced").isNull());
        assertEquals(4, row.get("maxScore").asInt(), "bài thường vẫn có điểm trong bảng tiến độ");
    }
}
