package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** #69, D-142, D-143: chế độ Nghe ép của bài giao chốt lúc giao; điểm ẩn theo result_mode của từng lượt, không theo chế độ hiện tại của bộ. */
class ForcedAssignmentFlagTest extends ListeningTestSupport {

    private static final String CMS = "/api/admin/cms";

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

    private ResultActions call(MockHttpServletRequestBuilder req, Account a, Object body) throws Exception {
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return send(req, a.accessToken());
    }

    private JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private long assign(Fx f) throws Exception {
        return body(call(MockMvcRequestBuilders.post(base(f)), f.teacher(),
                Map.of("bankId", f.w().bank(), "dueAt", Instant.now().plus(48, ChronoUnit.HOURS).toString()))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private ResultActions start(Fx f, long asg, Account s) throws Exception {
        return call(MockMvcRequestBuilders.post(base(f) + "/" + asg + "/start"), s, null);
    }

    private long submitAssigned(Fx f, long asg, Account s, boolean... correct) throws Exception {
        long id = body(start(f, asg, s).andExpect(status().isOk())).get("id").asLong();
        postJson("/api/attempts/" + id + "/submit", s, Map.of("answers", answers(f.w(), correct))).andExpect(status().isOk());
        return id;
    }

    private JsonNode progressRow(Fx f, long asg, long userId) throws Exception {
        for (JsonNode r : body(call(MockMvcRequestBuilders.get(base(f) + "/" + asg + "/progress"), f.teacher(), null).andExpect(status().isOk()))) {
            if (r.get("userId").asLong() == userId) {
                return r;
            }
        }
        throw new AssertionError("không thấy học viên " + userId);
    }

    private void playbackMode(Account admin, long bank, String mode) throws Exception {
        call(MockMvcRequestBuilders.patch(CMS + "/banks/" + bank), admin, Map.of("playbackMode", mode)).andExpect(status().isOk());
    }

    @Test
    void teacherProgressHidesScoreOfCountOnlyAttemptAfterBankLeavesLockedMode() throws Exception {
        var f = fixture("LOCKED");
        var admin = withRole("ADMIN");
        var s = member(f);
        long asg = assign(f);
        submitAssigned(f, asg, s, true, true, true, false);
        playbackMode(admin, f.w().bank(), "FREE");

        var row = progressRow(f, asg, s.id());
        assertTrue(row.path("aiScore").isNull() && row.path("maxScore").isNull() && row.path("finalScore").isNull(), row.toString());
        assertEquals(1, row.at("/forced/attemptCount").asInt(), "bài giao đã chốt Nghe ép nên vẫn có tiến độ Nghe ép");
    }

    @Test
    void gradingCountOnlyAttemptIsStillRejectedAfterBankLeavesLockedMode() throws Exception {
        var f = fixture("LOCKED");
        var admin = withRole("ADMIN");
        var s = member(f);
        long asg = assign(f);
        long attempt = submitAssigned(f, asg, s, true, true, true, true);
        playbackMode(admin, f.w().bank(), "FREE");
        call(MockMvcRequestBuilders.put(base(f) + "/" + asg + "/attempts/" + attempt + "/grade"), f.teacher(), Map.of("score", 3))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("grade_not_supported"));
    }

    @Test
    void ordinaryAssignmentStaysSingleSubmissionAfterBankSwitchedToLocked() throws Exception {
        var f = fixture("FREE");
        var admin = withRole("ADMIN");
        var s = member(f);
        long asg = assign(f);
        long first = submitAssigned(f, asg, s, true, true, true, true);
        playbackMode(admin, f.w().bank(), "LOCKED");
        start(f, asg, s).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("assignment_submitted"));
        assertEquals("FULL", jdbc.queryForObject("select result_mode from student_exercise_attempts where id = ?", String.class, first));
        var row = progressRow(f, asg, s.id());
        assertEquals(4, row.get("maxScore").asInt(), "bài thường vẫn có điểm");
        assertTrue(row.path("forced").isNull());
    }

    @Test
    void forcedAssignmentStaysForcedAfterBankSwitchedToFree() throws Exception {
        var f = fixture("LOCKED");
        var admin = withRole("ADMIN");
        var s = member(f);
        long asg = assign(f);
        assertEquals(1, count("select count(*) from forced_listening_rules where assignment_id = ?", asg), "luật được chụp lúc giao");
        submitAssigned(f, asg, s, false, false, false, false);
        playbackMode(admin, f.w().bank(), "FREE");

        // vẫn làm lại không giới hạn, lượt mới vẫn COUNT_ONLY theo luật đã chụp, không có điểm
        long second = body(start(f, asg, s).andExpect(status().isOk())).get("id").asLong();
        assertEquals("COUNT_ONLY", jdbc.queryForObject("select result_mode from student_exercise_attempts where id = ?", String.class, second));
        var res = body(postJson("/api/attempts/" + second + "/submit", s, Map.of("answers", answers(f.w(), true, true, true, true)))
                .andExpect(status().isOk()));
        assertTrue(res.path("score").isMissingNode() && res.path("results").isMissingNode(), res.toString());
        assertEquals(2, res.at("/forced/attemptNo").asInt());
        assertEquals(2, progressRow(f, asg, s.id()).at("/forced/attemptCount").asInt());
    }

    @Test
    void chapterTestScoreOfCountOnlyAttemptIsHiddenOnClassProgress() throws Exception {
        var f = fixture("LOCKED");
        jdbc.update("update topics set chapter_test_bank_id = ? where id = ?", f.w().bank(), f.w().topic());
        var s = member(f);
        submit(s, f.w(), true, true, true, false);

        var board = body(call(MockMvcRequestBuilders.get("/api/classes/" + f.classId() + "/progress"), f.teacher(), null)
                .andExpect(status().isOk()));
        int seen = 0;
        for (JsonNode st : board.get("students")) {
            if (st.get("userId").asLong() == s.id()) {
                for (JsonNode c : st.get("chapterTests")) {
                    seen++;
                    assertTrue(c.path("score").isNull() && c.path("maxScore").isNull() && c.path("percent").isNull(), c.toString());
                }
            }
        }
        assertEquals(1, seen);
    }
}
