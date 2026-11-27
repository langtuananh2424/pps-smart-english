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

/** D-162 (tester): lượt tự học mở TRƯỚC khi giao Thi; bài giao Thi theo mode của bộ. */
class D162TesterTest extends ListeningTestSupport {

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


    private long selfStart(Fx f, Account s) throws Exception {
        return json.readTree(postJson("/api/banks/" + f.w().bank() + "/attempts", s, Map.of()).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    /** Lượt Luyện tập tự học đang mở, giáo viên giao bộ ở chế độ Thi: không được còn gợi ý/kiểm câu (D-162 "không thấy gợi ý"). */
    @Test
    void openSelfAttemptBeforeExamAssignmentLosesHints() throws Exception {
        var f = fixture("PRACTICE");
        var s = member(f);
        long self = selfStart(f, s);
        getJson("/api/attempts/" + self + "/hints", s).andExpect(status().isOk()); // trước khi giao: có gợi ý
        assign(f, "EXAM");
        var bank = json.readTree(getJson("/api/banks/" + f.w().bank(), s).andReturn().getResponse().getContentAsString());
        assertEquals(0, bank.at("/questions/0/hints").size(), "GET bộ vẫn lộ gợi ý cho người nhận bài Thi");
        getJson("/api/attempts/" + self + "/hints", s).andExpect(status().isForbidden());
    }

    /** Bài giao không đặt mode trên bộ EXAM: chế độ hiệu lực là Thi, nên cũng chặn tự học. */
    @Test
    void examBankAssignmentWithoutModeBlocksSelfStudy() throws Exception {
        var f = fixture("EXAM");
        var s = member(f);
        assign(f, null);
        postJson("/api/banks/" + f.w().bank() + "/attempts", s, Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("bank_assigned_as_exam"));
    }
}
