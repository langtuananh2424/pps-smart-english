package com.pps.smartenglish.classroom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.ai.AiTestSupport;
import com.pps.smartenglish.ai.speaking.FakeSpeakingEngineClient;
import com.pps.smartenglish.support.FakeStorageConfig.FakeStorage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * D-168, FR-ASG-03: điểm giáo viên sửa cho bài giao câu Viết/Nói là điểm cuối ở phía học viên (/mine) và giáo viên (progress);
 * điểm Cam chấm giữ ở aiScore; Cam chấm xong sau khi sửa không ghi đè; người ngoài lớp, học viên và điểm vượt thang bị chặn.
 */
class D168TeacherAiScoreTest extends AiTestSupport {

    private static final String ESSAY = "I agree with online learning because it is flexible. However, students need discipline.";

    @Autowired FakeStorage storage;
    @Autowired FakeSpeakingEngineClient engine;

    private record Fx(long course, long cls, long writing, long speaking, Account teacher) {}

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

    private Account withRole(String role) throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = ?", u.id(), role);
        return login(u.email(), PASSWORD);
    }

    private Account staffOf(long cls) throws Exception {
        var t = withRole("TEACHER");
        jdbc.update("insert into class_staff (class_id, user_id, role, assigned_from) values (?, ?, 'TEACHER', ?)", cls, t.id(), LocalDate.now().minusDays(10));
        return t;
    }

    private long newClass(long course) {
        return insert("classes", Map.of("course_id", course, "name", "Lớp " + UUID.randomUUID().toString().substring(0, 4),
                "starts_on", LocalDate.now().minusDays(10), "ends_on", LocalDate.now().plusDays(60), "mode", "ONLINE", "capacity", 20));
    }

    private Fx fixture() throws Exception {
        long c = insert("courses", Map.of("slug", "d168-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED",
                "delivery_mode", "CLASS_BASED", "sequential", false));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED", "sort_order", 0));
        long t = insert("topics", Map.of("level_id", lv, "title", "Unit A", "is_free_trial", false, "status", "PUBLISHED", "sort_order", 0));
        long bank = insert("question_banks", Map.of("topic_id", t, "title", "Bộ", "is_trial", false, "status", "PUBLISHED", "mode", "PRACTICE"));
        long w = writingQuestion(bank);
        long sp = question(bank, "SPEAKING", 2, "Describe a trip.", "{\"part\":1,\"prompt\":\"Describe a trip.\"}", "{}");
        long cls = newClass(c);
        return new Fx(c, cls, w, sp, staffOf(cls));
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

    private long assign(Fx f, long questionId) throws Exception {
        var m = Map.of("questionId", questionId, "dueAt", Instant.now().plus(48, ChronoUnit.HOURS).toString());
        return idOf(call(HttpMethod.POST, base(f), f.teacher(), m).andExpect(status().isCreated()), "id");
    }

    /** Học viên nộp bài viết, chưa cho Cam chấm. */
    private long submitWriting(Fx f, Account s, long asg) throws Exception {
        guard.resetRateLimit(s.id());
        var body = new HashMap<String, Object>();
        body.put("questionId", f.writing());
        body.put("assignmentId", asg);
        long session = idOf(postJson("/api/writing/sessions", s, body).andExpect(status().isCreated()), "sessionId");
        guard.resetRateLimit(s.id());
        postJson("/api/writing/sessions/" + session + "/submit", s, Map.of("finalText", ESSAY), UUID.randomUUID().toString()).andExpect(status().isAccepted());
        return session;
    }

    /** Học viên nộp bài nói, chưa cho Cam chấm. */
    private long submitSpeaking(Fx f, Account s, long asg) throws Exception {
        guard.resetRateLimit(s.id());
        var body = new HashMap<String, Object>();
        body.put("questionId", f.speaking());
        body.put("assignmentId", asg);
        long id = idOf(postJson("/api/speaking/submissions", s, body).andExpect(status().isCreated()), "submissionId");
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

    private String progressFinal(Fx f, long asg, Account who) throws Exception {
        String res = call(HttpMethod.GET, base(f) + "/" + asg + "/progress", f.teacher(), null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (var n : json.readTree(res)) {
            if (n.get("userId").asLong() == who.id()) {
                return n.path("finalScore").asText();
            }
        }
        return null;
    }

    /** Sửa điểm lúc Cam còn chờ: Cam chấm xong, điểm giáo viên vẫn là điểm cuối, aiScore là điểm Cam. */
    @Test
    void writingTeacherScoreWinsOverLateCamResult() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = assign(f, f.writing());
        long session = submitWriting(f, s, asg);
        String grade = base(f) + "/" + asg + "/attempts/" + session + "/grade";

        call(HttpMethod.PUT, grade, f.teacher(), Map.of("score", 85)).andExpect(status().isCreated());
        call(HttpMethod.GET, base(f) + "/" + asg + "/mine", s, null).andExpect(jsonPath("$.finalScore").value(85.0));
        runner.runDueNow();
        call(HttpMethod.GET, base(f) + "/" + asg + "/mine", s, null).andExpect(jsonPath("$.aiScore").value(70))
                .andExpect(jsonPath("$.finalScore").value(85.0));
        assertEquals("85.0", progressFinal(f, asg, s));
    }

    @Test
    void speakingTeacherScoreWinsOverLateCamResult() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = assign(f, f.speaking());
        long id = submitSpeaking(f, s, asg);
        String grade = base(f) + "/" + asg + "/attempts/" + id + "/grade";

        call(HttpMethod.PUT, grade, f.teacher(), Map.of("score", 7.5)).andExpect(status().isCreated());
        runner.runDueNow();
        call(HttpMethod.GET, base(f) + "/" + asg + "/mine", s, null).andExpect(jsonPath("$.aiScore").isNumber())
                .andExpect(jsonPath("$.finalScore").value(7.5));
        assertEquals("7.5", progressFinal(f, asg, s));
        // sửa lần hai: bản mới nhất là điểm cuối, lịch sử giữ cả hai
        call(HttpMethod.PUT, grade, f.teacher(), Map.of("score", 8)).andExpect(status().isCreated());
        call(HttpMethod.GET, base(f) + "/" + asg + "/mine", s, null).andExpect(jsonPath("$.finalScore").value(8.0));
        call(HttpMethod.GET, grade.replace("/grade", "/grades"), f.teacher(), null).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void outsidersStudentsAndOverMaxAreBlocked() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = assign(f, f.writing());
        long session = submitWriting(f, s, asg);
        String grade = base(f) + "/" + asg + "/attempts/" + session + "/grade";

        call(HttpMethod.PUT, grade, s, Map.of("score", 100)).andExpect(status().isNotFound());
        call(HttpMethod.PUT, grade, withRole("TEACHER"), Map.of("score", 100)).andExpect(status().isNotFound());
        call(HttpMethod.PUT, grade, f.teacher(), Map.of("score", 101)).andExpect(status().isBadRequest());
        assertEquals(0, count("select count(*) from grade_overrides where writing_session_id = ?", session));
    }
}
