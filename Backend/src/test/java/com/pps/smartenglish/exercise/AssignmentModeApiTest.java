package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** D-155, FR-ASG-01, FR-EX-05: giáo viên đặt chế độ Luyện tập/Thi cho bài giao; lượt theo bài giao, không thì theo bộ. */
class AssignmentModeApiTest extends ListeningTestSupport {

    private record Fx(World w, long classId, Account teacher) {}

    private Fx fixture(String bankMode) throws Exception {
        var w = world("FREE", null);
        jdbc.update("update courses set delivery_mode = 'CLASS_BASED' where id = ?", w.course());
        jdbc.update("update question_banks set mode = ? where id = ?", bankMode, w.bank());
        insert("question_hints", Map.of("question_id", w.q()[0], "content", "Gợi ý", "sort_order", 0));
        long cls = classOf(w);
        return new Fx(w, cls, staff(cls));
    }

    private long classOf(World w) {
        return insert("classes", Map.of("course_id", w.course(), "name", "Lớp", "starts_on", LocalDate.now().minusDays(1),
                "ends_on", LocalDate.now().plusDays(60), "mode", "ONLINE", "capacity", 20));
    }

    private Account staff(long cls) throws Exception {
        var teacher = withRole("TEACHER");
        jdbc.update("insert into class_staff (class_id, user_id, role, assigned_from) values (?, ?, 'TEACHER', ?)", cls, teacher.id(),
                LocalDate.now().minusDays(2));
        return teacher;
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

    private ResultActions create(Fx f, String mode) throws Exception {
        var body = new HashMap<String, Object>(Map.of("bankId", f.w().bank(), "dueAt", Instant.now().plus(48, ChronoUnit.HOURS).toString()));
        if (mode != null) body.put("mode", mode);
        return call(MockMvcRequestBuilders.post(base(f)), f.teacher(), body);
    }

    private long assign(Fx f, String mode) throws Exception {
        return json.readTree(create(f, mode).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode start(Fx f, long asg, Account s) throws Exception {
        return json.readTree(call(MockMvcRequestBuilders.post(base(f) + "/" + asg + "/start"), s, null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private ResultActions patch(Fx f, long asg, Account who, Map<String, Object> body) throws Exception {
        return call(MockMvcRequestBuilders.patch(base(f) + "/" + asg), who, body);
    }

    @Test
    void examAssignmentOnPracticeBankGivesExamAttemptWithoutHints() throws Exception {
        var f = fixture("PRACTICE");
        var s = member(f);
        long asg = assign(f, "EXAM");
        call(MockMvcRequestBuilders.get("/api/me/assignments/" + asg), s, null).andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("EXAM"));
        // chưa bắt đầu lượt: GET bộ cũng không lộ gợi ý cho người nhận bài Thi
        var bank = json.readTree(getJson("/api/banks/" + f.w().bank(), s).andReturn().getResponse().getContentAsString());
        assertEquals(0, bank.at("/questions/0/hints").size());
        var attempt = start(f, asg, s);
        assertEquals("EXAM", attempt.get("mode").asText());
        getJson("/api/attempts/" + attempt.get("id").asLong() + "/hints", s).andExpect(status().isForbidden());
    }

    @Test
    void assignmentWithoutModeFollowsBank() throws Exception {
        var f = fixture("PRACTICE");
        var s = member(f);
        long asg = assign(f, null);
        call(MockMvcRequestBuilders.get("/api/me/assignments"), s, null).andExpect(jsonPath("$[0].mode").value("PRACTICE"));
        var attempt = start(f, asg, s);
        assertEquals("PRACTICE", attempt.get("mode").asText());
        getJson("/api/attempts/" + attempt.get("id").asLong() + "/hints", s).andExpect(status().isOk());
    }

    @Test
    void invalidModesRejected() throws Exception {
        var f = fixture("PRACTICE");
        create(f, "X").andExpect(status().isBadRequest());
        var exam = fixture("EXAM");
        create(exam, "PRACTICE").andExpect(status().isBadRequest()); // bộ thi không hạ xuống Luyện tập
        long q = f.w().q()[0];
        jdbc.update("update questions set type = 'WRITING' where id = ?", q);
        call(MockMvcRequestBuilders.post(base(f)), f.teacher(), Map.of("questionId", q, "mode", "EXAM",
                "dueAt", Instant.now().plus(48, ChronoUnit.HOURS).toString())).andExpect(status().isBadRequest());
    }

    @Test
    void otherClassTeacherCannotChangeMode() throws Exception {
        var f = fixture("PRACTICE");
        long asg = assign(f, null);
        var stranger = staff(classOf(f.w()));
        int code = patch(f, asg, stranger, Map.of("mode", "EXAM")).andReturn().getResponse().getStatus();
        org.junit.jupiter.api.Assertions.assertTrue(code == 403 || code == 404, "trả " + code);
        patch(f, asg, f.teacher(), Map.of("mode", "EXAM")).andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("EXAM"));
    }

    private ResultActions selfStudy(Fx f, Account s) throws Exception {
        return postJson("/api/banks/" + f.w().bank() + "/attempts", s, Map.of());
    }

    /** D-162: bài giao Thi còn hiệu lực và chưa nộp thì chặn tự học; nộp hoặc hủy thì mở lại; bài giao Luyện tập không chặn. */
    @Test
    void examAssignmentBlocksSelfStudyUntilSubmittedOrCancelled() throws Exception {
        var f = fixture("PRACTICE");
        var s = member(f);
        long asg = assign(f, "EXAM");
        selfStudy(f, s).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("bank_assigned_as_exam"));
        long attempt = start(f, asg, s).get("id").asLong();
        postJson("/api/attempts/" + attempt + "/submit", s, Map.of("answers", java.util.List.of())).andExpect(status().isOk());
        selfStudy(f, s).andExpect(status().isCreated());

        var s2 = member(f);
        selfStudy(f, s2).andExpect(status().isConflict());
        call(MockMvcRequestBuilders.post(base(f) + "/" + asg + "/cancel"), f.teacher(), null).andExpect(status().isOk());
        selfStudy(f, s2).andExpect(status().isCreated());
    }

    @Test
    void practiceAssignmentDoesNotBlockSelfStudy() throws Exception {
        var f = fixture("PRACTICE");
        var s = member(f);
        assign(f, "PRACTICE");
        selfStudy(f, s).andExpect(status().isCreated());
    }

    @Test
    void modeLockedOnceAttemptStarted() throws Exception {
        var f = fixture("PRACTICE");
        var s = member(f);
        long asg = assign(f, "EXAM");
        patch(f, asg, f.teacher(), java.util.Collections.singletonMap("mode", null)).andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("PRACTICE"));
        start(f, asg, s);
        patch(f, asg, f.teacher(), Map.of("mode", "EXAM")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("assignment_mode_locked"));
    }
}
