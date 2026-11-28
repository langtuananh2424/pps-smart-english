package com.pps.smartenglish.classroom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.ai.AiTestSupport;
import com.pps.smartenglish.ai.speaking.FakeSpeakingEngineClient;
import com.pps.smartenglish.support.FakeStorageConfig.FakeStorage;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Tester PR #87: D-169 với bài giao bộ câu đã hủy; D-170 giá trị biên điểm Nói. */
class D168CancelledSetAndBandTesterTest extends AiTestSupport {

    @Autowired FakeStorage storage;
    @Autowired FakeSpeakingEngineClient engine;

    private record Fx(long course, long cls, long bank, long mcq, long right, long speaking, Account teacher) {}

    @BeforeEach
    void reset() {
        engine.reset();
        storage.objects.clear();
    }

    private ResultActions call(HttpMethod method, String url, Account who, Object body) throws Exception {
        var req = MockMvcRequestBuilders.request(method, url);
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return send(req, who.accessToken());
    }

    private ResultActions putRaw(String url, Account who, String body) throws Exception {
        return send(MockMvcRequestBuilders.put(url).contentType(MediaType.APPLICATION_JSON).content(body), who.accessToken());
    }

    private Fx fixture() throws Exception {
        long c = insert("courses", Map.of("slug", "d169-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED",
                "delivery_mode", "CLASS_BASED", "sequential", false));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED", "sort_order", 0));
        long t = insert("topics", Map.of("level_id", lv, "title", "Unit A", "is_free_trial", false, "status", "PUBLISHED", "sort_order", 0));
        long bank = insert("question_banks", Map.of("topic_id", t, "title", "Bộ", "is_trial", false, "status", "PUBLISHED", "mode", "PRACTICE", "pass_pct", 50));
        long mcq = question(bank, "CONNECTIVE_MCQ", 2, "Chọn từ nối", "{}", "{}");
        long right = insert("question_choices", Map.of("question_id", mcq, "label", "A", "content", "although", "is_correct", true));
        insert("question_choices", Map.of("question_id", mcq, "label", "B", "content", "so", "is_correct", false));
        long bank2 = insert("question_banks", Map.of("topic_id", t, "title", "Bộ nói", "is_trial", false, "status", "PUBLISHED", "mode", "PRACTICE"));
        long sp = question(bank2, "SPEAKING", 2, "Describe a trip.", "{\"part\":1,\"prompt\":\"Describe a trip.\"}", "{}");
        long cls = insert("classes", Map.of("course_id", c, "name", "Lớp " + UUID.randomUUID().toString().substring(0, 4),
                "starts_on", LocalDate.now().minusDays(10), "ends_on", LocalDate.now().plusDays(60), "mode", "ONLINE", "capacity", 20));
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = 'TEACHER'", u.id());
        jdbc.update("insert into class_staff (class_id, user_id, role, assigned_from) values (?, ?, 'TEACHER', ?)", cls, u.id(), LocalDate.now().minusDays(10));
        return new Fx(c, cls, bank, mcq, right, sp, login(u.email(), PASSWORD));
    }

    private Account member(Fx f) throws Exception {
        var s = student(3);
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', TIMESTAMP '2020-01-01 00:00:00', TIMESTAMP '2099-01-01 00:00:00')",
                s.id(), f.course());
        jdbc.update("insert into class_members (class_id, course_id, user_id, status, live_key, joined_at) values (?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP)",
                f.cls(), f.course(), s.id(), f.course() + ":" + s.id());
        return s;
    }

    private String base(Fx f) {
        return "/api/classes/" + f.cls() + "/assignments";
    }

    private String gradeUrl(Fx f, long asg, long sub) {
        return base(f) + "/" + asg + "/attempts/" + sub + "/grade";
    }

    private long assign(Fx f, Map<String, Object> target) throws Exception {
        var body = new HashMap<String, Object>(target);
        body.put("dueAt", Instant.now().plus(48, ChronoUnit.HOURS).toString());
        return idOf(call(HttpMethod.POST, base(f), f.teacher(), body).andExpect(status().isCreated()), "id");
    }

    private long submitSet(Fx f, Account s, long asg) throws Exception {
        long attempt = idOf(call(HttpMethod.POST, base(f) + "/" + asg + "/start", s, null).andExpect(status().isOk()), "id");
        guard.resetRateLimit(s.id());
        postJson("/api/attempts/" + attempt + "/submit", s, Map.of("answers", List.of(
                Map.of("questionId", f.mcq(), "answer", Map.of("choiceId", f.right()))))).andExpect(status().isOk());
        return attempt;
    }

    private long submitSpeaking(Fx f, Account s, long asg) throws Exception {
        guard.resetRateLimit(s.id());
        long id = idOf(postJson("/api/speaking/submissions", s, Map.of("questionId", f.speaking(), "assignmentId", asg))
                .andExpect(status().isCreated()), "submissionId");
        storage.put(jdbc.queryForObject("select object_key from speaking_submissions where id = ?", String.class, id), wav(20));
        guard.resetRateLimit(s.id());
        postJson("/api/speaking/submissions/" + id + "/complete", s, Map.of()).andExpect(status().isAccepted());
        return id;
    }

    private static byte[] wav(double seconds) {
        int rate = 32000;
        int data = (int) (seconds * rate);
        var b = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + data).put("WAVE".getBytes()).put("fmt ".getBytes()).putInt(16).putShort((short) 1)
                .putShort((short) 1).putInt(rate / 2).putInt(rate).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes()).putInt(data);
        return b.array();
    }

    private BigDecimal score(long attempt) {
        return jdbc.queryForObject("select score from student_exercise_attempts where id = ?", BigDecimal.class, attempt);
    }

    /** D-169: bài giao bộ câu đã hủy: PUT điểm và PUT chỉ nhận xét đều 409, không ghi grade_overrides, điểm lượt giữ nguyên; lịch sử và kết quả vẫn đọc được. */
    @Test
    void cancelledSetAssignmentLocksGradingButStaysReadable() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = assign(f, Map.of("bankId", f.bank()));
        long attempt = submitSet(f, s, asg);
        call(HttpMethod.PUT, gradeUrl(f, asg, attempt), f.teacher(), Map.of("score", 1, "comment", "Khá")).andExpect(status().isCreated());
        postJson(base(f) + "/" + asg + "/cancel", f.teacher(), Map.of()).andExpect(status().isOk());

        call(HttpMethod.PUT, gradeUrl(f, asg, attempt), f.teacher(), Map.of("score", 2)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("assignment_cancelled"));
        call(HttpMethod.PUT, gradeUrl(f, asg, attempt), f.teacher(), Map.of("comment", "Sửa muộn")).andExpect(status().isConflict());
        call(HttpMethod.PUT, gradeUrl(f, asg, attempt), f.teacher(), Map.of("score", 0, "comment", "x")).andExpect(status().isConflict());

        assertEquals(1, count("select count(*) from grade_overrides where attempt_id = ?", attempt));
        assertEquals(0, new BigDecimal("1").compareTo(score(attempt)));
        call(HttpMethod.GET, base(f) + "/" + asg + "/attempts/" + attempt + "/grades", f.teacher(), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].comment").value("Khá"));
        getJson("/api/attempts/" + attempt, s).andExpect(status().isOk()).andExpect(jsonPath("$.score").value(1));
    }

    /** D-170 + @Digits: Nói nhận 0, 9, 8.50, 4.5; từ chối 9.5, -0.5, 8.25, 0.1, 9.001 bằng 400 và không ghi dòng sửa. */
    @Test
    void speakingBandBoundaries() throws Exception {
        var f = fixture();
        var s = member(f);
        long sp = assign(f, Map.of("questionId", f.speaking()));
        long sub = submitSpeaking(f, s, sp);
        String url = gradeUrl(f, sp, sub);

        for (String bad : new String[] {"9.5", "-0.5", "8.25", "0.1", "9.001", "10", "1e1"}) {
            assertEquals(400, putRaw(url, f.teacher(), "{\"score\":" + bad + "}").andReturn().getResponse().getStatus(), "score " + bad);
        }
        assertEquals(0, count("select count(*) from grade_overrides where speaking_submission_id = ?", sub));
        for (String ok : new String[] {"0", "9", "8.50", "4.5", "9.0"}) {
            assertEquals(201, putRaw(url, f.teacher(), "{\"score\":" + ok + "}").andReturn().getResponse().getStatus(), "score " + ok);
        }
        assertEquals(5, count("select count(*) from grade_overrides where speaking_submission_id = ?", sub));
        call(HttpMethod.GET, base(f) + "/" + sp + "/mine", s, null).andExpect(jsonPath("$.finalScore").value(9.0));
    }
}
