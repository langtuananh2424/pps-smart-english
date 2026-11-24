package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Tester vòng 2 (#69 sau khi chốt Nghe ép tại lúc giao, V35): các chỗ còn đọc chế độ HIỆN TẠI của bộ cho lượt đã có,
 * bài giao Viết/Nói không bị gắn cờ, câu archived và script.
 */
class ForcedListeningRound2Test extends ListeningTestSupport {

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

    private ResultActions call(MockHttpServletRequestBuilder req, Account a, Object body) throws Exception {
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return send(req, a.accessToken());
    }

    private JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private String due() {
        return Instant.now().plus(48, ChronoUnit.HOURS).toString();
    }

    private void playbackMode(Account admin, long bank, String mode) throws Exception {
        call(MockMvcRequestBuilders.patch(CMS + "/banks/" + bank), admin, Map.of("playbackMode", mode)).andExpect(status().isOk());
    }

    /** D-135/D-136: lượt Nghe ép (COUNT_ONLY, luật đã chụp) vẫn bị giới hạn số lần nghe dù bộ đã đổi về FREE. */
    @Test
    void forcedAssignmentAttemptKeepsPlayLimitAfterBankSwitchedToFree() throws Exception {
        var f = fixture("LOCKED");
        var admin = withRole("ADMIN");
        var s = member(f);
        long asg = body(call(MockMvcRequestBuilders.post("/api/classes/" + f.classId() + "/assignments"), f.teacher(),
                Map.of("bankId", f.w().bank(), "dueAt", due(), "forcedListening", Map.of("playLimit", 1))).andExpect(status().isCreated()))
                .get("id").asLong();
        long attempt = body(call(MockMvcRequestBuilders.post("/api/classes/" + f.classId() + "/assignments/" + asg + "/start"), s, null)
                .andExpect(status().isOk())).get("id").asLong();
        playbackMode(admin, f.w().bank(), "FREE");

        play(s, attempt, f.w().g1()).andExpect(status().isOk()).andExpect(jsonPath("$.playLimit").value(1));
        playDone(s, attempt, f.w().g1());
        play(s, attempt, f.w().g1()).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("play_limit_reached"));
    }

    /** Lượt tự học bắt đầu ở LOCKED (COUNT_ONLY) không được nghe tự do khi bộ đổi về FREE giữa chừng. */
    @Test
    void selfStudyCountOnlyAttemptKeepsPlayLimitWhenBankSwitchedMidAttempt() throws Exception {
        var w = world("LOCKED", null);
        var admin = withRole("ADMIN");
        var u = student(0);
        put(CMS + "/banks/" + w.bank() + "/forced-listening", admin, Map.of("playLimit", 1)).andExpect(status().isOk());
        long attempt = startAttempt(u, w);
        playbackMode(admin, w.bank(), "FREE");
        play(u, attempt, w.g1()).andExpect(status().isOk());
        playDone(u, attempt, w.g1());
        play(u, attempt, w.g1()).andExpect(status().isForbidden());
    }

    /** Lượt thường (FULL) bắt đầu ở FREE; bộ đổi sang LOCKED giữa chừng thì áp như Thi, không áp quy tắc Nghe ép. */
    @Test
    void fullAttemptOnBankSwitchedToLockedIsLimitedLikeExam() throws Exception {
        var w = world("FREE", null);
        var admin = withRole("ADMIN");
        var u = student(0);
        long attempt = startAttempt(u, w);
        playbackMode(admin, w.bank(), "LOCKED");
        play(u, attempt, w.g1()).andExpect(status().isOk()).andExpect(jsonPath("$.playbackMode").value("EXAM"))
                .andExpect(jsonPath("$.playLimit").isNumber());
        // chế độ Thi mặc định nghe 1 lần (app.listening.exam-play-limit): lần thứ hai hết lượt, không phải play_in_progress
        play(u, attempt, w.g1()).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("play_limit_reached"));
    }

    @Test
    void writingAssignmentOnLockedBankIsNotForced() throws Exception {
        var f = fixture("LOCKED");
        member(f);
        long writing = writingQuestion(f.w().bank());
        long asg = body(call(MockMvcRequestBuilders.post("/api/classes/" + f.classId() + "/assignments"), f.teacher(),
                Map.of("questionId", writing, "dueAt", due())).andExpect(status().isCreated())).get("id").asLong();
        assertEquals(Boolean.FALSE, jdbc.queryForObject("select forced_listening from assignments where id = ?", Boolean.class, asg));
        assertEquals(0, count("select count(*) from forced_listening_rules where assignment_id = ?", asg));
        // giao cả bộ trên cùng bộ thì có cờ và có luật chụp
        long whole = body(call(MockMvcRequestBuilders.post("/api/classes/" + f.classId() + "/assignments"), f.teacher(),
                Map.of("bankId", f.w().bank(), "dueAt", due())).andExpect(status().isCreated())).get("id").asLong();
        assertEquals(Boolean.TRUE, jdbc.queryForObject("select forced_listening from assignments where id = ?", Boolean.class, whole));
        assertEquals(1, count("select count(*) from forced_listening_rules where assignment_id = ?", whole));
    }

    @Test
    void archivedQuestionScriptIsNotFoundEvenOnAnOldNthAttempt() throws Exception {
        var w = world("LOCKED", null);
        var admin = withRole("ADMIN");
        var u = student(0);
        put(CMS + "/banks/" + w.bank() + "/forced-listening", admin, Map.of("hintEveryN", 1, "wrongThreshold", 1)).andExpect(status().isOk());
        var first = submitWithId(u, w, false, true, true, true);
        getJson("/api/attempts/" + first.id() + "/scripts/" + w.q()[0], u).andExpect(status().isOk());
        jdbc.update("update questions set archived_at = CURRENT_TIMESTAMP where id = ?", w.q()[0]);
        getJson("/api/attempts/" + first.id() + "/scripts/" + w.q()[0], u).andExpect(status().isNotFound());
        // xem lại lượt cũ: danh sách câu sai nhiều dựng lại không còn câu đã ẩn, không có prompt null
        var hard = get("/api/attempts/" + first.id(), u, 200).at("/forced/hardQuestions");
        List<Long> ids = new ArrayList<>();
        hard.forEach(h -> {
            ids.add(h.get("questionId").asLong());
            assertTrue(h.hasNonNull("prompt"), h.toString());
        });
        assertTrue(!ids.contains(w.q()[0]), hard.toString());
    }
}
