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
 * D-168 (tester): IDOR theo lớp, bài và loại bài nộp; trợ giảng hết phân công; giá trị điểm biên; nhận xét không đổi điểm;
 * hồ sơ năng lực không đổi theo điểm sửa.
 */
class D168TeacherAiScoreEdgeTest extends AiTestSupport {

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

    private Account staffOf(long cls, String role, LocalDate from, LocalDate to) throws Exception {
        var t = withRole("TEACHER");
        jdbc.update("insert into class_staff (class_id, user_id, role, assigned_from, assigned_to) values (?, ?, ?, ?, ?)", cls, t.id(), role, from, to);
        return t;
    }

    private String gradeUrl(Fx f, long asg, long sub) {
        return base(f) + "/" + asg + "/attempts/" + sub + "/grade";
    }

    private ResultActions putRaw(String url, Account who, String body) throws Exception {
        return send(MockMvcRequestBuilders.put(url).contentType(MediaType.APPLICATION_JSON).content(body), who.accessToken());
    }

    private long overridesOf(long session) {
        return count("select count(*) from grade_overrides where writing_session_id = ?", session);
    }

    /** Giáo viên lớp B không chấm, không xem lịch sử, không mở bài nộp của lớp A, dù qua đường dẫn lớp mình hay lớp A. */
    @Test
    void teacherOfAnotherClassCannotTouchSubmission() throws Exception {
        var a = fixture();
        var s = member(a);
        long asgA = assign(a, a.writing());
        long session = submitWriting(a, s, asgA);
        var b = fixture();
        long asgB = assign(b, b.writing());

        call(HttpMethod.PUT, gradeUrl(b, asgB, session), b.teacher(), Map.of("score", 90)).andExpect(status().isNotFound());
        call(HttpMethod.GET, base(b) + "/" + asgB + "/attempts/" + session + "/grades", b.teacher(), null).andExpect(status().isNotFound());
        call(HttpMethod.GET, base(b) + "/" + asgB + "/submissions/" + session, b.teacher(), null).andExpect(status().isNotFound());
        // bài của lớp A gắn vào đường dẫn lớp B
        call(HttpMethod.PUT, base(b) + "/" + asgA + "/attempts/" + session + "/grade", b.teacher(), Map.of("score", 90)).andExpect(status().isNotFound());
        call(HttpMethod.PUT, gradeUrl(a, asgA, session), b.teacher(), Map.of("score", 90)).andExpect(status().isNotFound());
        assertEquals(0, overridesOf(session));
    }

    /** Cùng lớp nhưng bài nộp thuộc bài giao khác, hoặc id phiên viết đưa vào bài giao Nói: 404, không ghi dòng sửa. */
    @Test
    void submissionOfOtherAssignmentOrOtherTypeIsNotFound() throws Exception {
        var f = fixture();
        var s = member(f);
        long asgW1 = assign(f, f.writing());
        long asgW2 = assign(f, f.writing());
        long asgSp = assign(f, f.speaking());
        long session = submitWriting(f, s, asgW1);

        call(HttpMethod.PUT, gradeUrl(f, asgW2, session), f.teacher(), Map.of("score", 50)).andExpect(status().isNotFound());
        call(HttpMethod.PUT, gradeUrl(f, asgSp, session), f.teacher(), Map.of("score", 5)).andExpect(status().isNotFound());
        assertEquals(0, overridesOf(session));
        assertEquals(0, count("select count(*) from grade_overrides where speaking_submission_id = ?", session));
    }

    /** Trợ giảng hết hạn phân công, bị thu hồi hoặc chưa tới ngày phân công: 404. */
    @Test
    void assistantOutsideAssignmentWindowIsBlocked() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = assign(f, f.writing());
        long session = submitWriting(f, s, asg);
        var expired = staffOf(f.cls(), "ASSISTANT", LocalDate.now().minusDays(10), LocalDate.now().minusDays(1));
        var future = staffOf(f.cls(), "ASSISTANT", LocalDate.now().plusDays(1), null);
        var revoked = staffOf(f.cls(), "ASSISTANT", LocalDate.now().minusDays(10), null);
        jdbc.update("update class_staff set revoked_at = CURRENT_TIMESTAMP where user_id = ?", revoked.id());
        var active = staffOf(f.cls(), "ASSISTANT", LocalDate.now().minusDays(1), LocalDate.now());

        for (var who : new Account[] {expired, future, revoked}) {
            call(HttpMethod.PUT, gradeUrl(f, asg, session), who, Map.of("score", 40)).andExpect(status().isNotFound());
        }
        assertEquals(0, overridesOf(session));
        call(HttpMethod.PUT, gradeUrl(f, asg, session), active, Map.of("score", 40)).andExpect(status().isCreated());
        call(HttpMethod.GET, base(f) + "/" + asg + "/mine", s, null).andExpect(jsonPath("$.finalScore").value(40.0));
    }

    /** Điểm âm, NaN, Infinity, chuỗi, số rất lớn, vượt band 9 của bài Nói: 400, không phải 500, không ghi dòng sửa. */
    @Test
    void invalidScoresAreRejectedWithBadRequest() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = assign(f, f.writing());
        long session = submitWriting(f, s, asg);
        String url = gradeUrl(f, asg, session);

        for (String body : new String[] {"{\"score\":-1}", "{\"score\":-0.01}", "{\"score\":\"NaN\"}", "{\"score\":NaN}",
                "{\"score\":\"Infinity\"}", "{\"score\":\"abc\"}", "{\"score\":1e400}", "{\"score\":100.001}"}) {
            int st = putRaw(url, f.teacher(), body).andReturn().getResponse().getStatus();
            assertEquals(400, st, "body " + body);
        }
        assertEquals(0, overridesOf(session));

        long sp = assign(f, f.speaking());
        long sub = submitSpeaking(f, s, sp);
        call(HttpMethod.PUT, gradeUrl(f, sp, sub), f.teacher(), Map.of("score", 9.5)).andExpect(status().isBadRequest());
        call(HttpMethod.PUT, gradeUrl(f, sp, sub), f.teacher(), Map.of("score", 9)).andExpect(status().isCreated());
    }

    /** Điểm biên 0 và đúng điểm tối đa được nhận. */
    @Test
    void boundaryScoresAreAccepted() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = assign(f, f.writing());
        long session = submitWriting(f, s, asg);
        call(HttpMethod.PUT, gradeUrl(f, asg, session), f.teacher(), Map.of("score", 0)).andExpect(status().isCreated());
        call(HttpMethod.GET, base(f) + "/" + asg + "/mine", s, null).andExpect(jsonPath("$.finalScore").value(0.0));
        call(HttpMethod.PUT, gradeUrl(f, asg, session), f.teacher(), Map.of("score", 100)).andExpect(status().isCreated());
        call(HttpMethod.GET, base(f) + "/" + asg + "/mine", s, null).andExpect(jsonPath("$.finalScore").value(100.0));
    }

    /** Điểm quá 2 chữ số thập phân bị từ chối (cột numeric(8,2)); 85.12 vẫn nhận và đọc lại khớp. */
    @Test
    void scoreBeyondTwoDecimalsRejectedAndTwoDecimalsConsistent() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = assign(f, f.writing());
        long session = submitWriting(f, s, asg);
        String url = gradeUrl(f, asg, session);

        assertEquals(400, putRaw(url, f.teacher(), "{\"score\":85.123456}").andReturn().getResponse().getStatus());
        var res = putRaw(url, f.teacher(), "{\"score\":85.12}").andReturn().getResponse();
        assertEquals(201, res.getStatus());
        assertEquals(new java.math.BigDecimal("85.12"), json.readTree(res.getContentAsString()).get("score").decimalValue());
        call(HttpMethod.GET, base(f) + "/" + asg + "/mine", s, null).andExpect(jsonPath("$.finalScore").value(85.12));
    }

    /** Chỉ sửa nhận xét: điểm cuối vẫn là điểm Cam. */
    @Test
    void commentOnlyKeepsCamScore() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = assign(f, f.writing());
        long session = submitWriting(f, s, asg);
        runner.runDueNow();
        call(HttpMethod.PUT, gradeUrl(f, asg, session), f.teacher(), Map.of("comment", "Tốt")).andExpect(status().isCreated());
        call(HttpMethod.GET, base(f) + "/" + asg + "/mine", s, null).andExpect(jsonPath("$.aiScore").value(70))
                .andExpect(jsonPath("$.finalScore").value(70)).andExpect(jsonPath("$.teacherComment").value("Tốt"));
    }

    /** D-166/D-168: hồ sơ năng lực tính theo câu, không đổi khi giáo viên sửa điểm bài Viết/Nói. */
    @Test
    void learningProfileUnchangedByTeacherScore() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = assign(f, f.writing());
        long session = submitWriting(f, s, asg);
        long sp = assign(f, f.speaking());
        long sub = submitSpeaking(f, s, sp);
        runner.runDueNow();
        String before = call(HttpMethod.GET, "/api/me/learning-profile", s, null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        call(HttpMethod.PUT, gradeUrl(f, asg, session), f.teacher(), Map.of("score", 12)).andExpect(status().isCreated());
        call(HttpMethod.PUT, gradeUrl(f, sp, sub), f.teacher(), Map.of("score", 1)).andExpect(status().isCreated());
        String after = call(HttpMethod.GET, "/api/me/learning-profile", s, null).andReturn().getResponse().getContentAsString();
        assertEquals(json.readTree(before), json.readTree(after));
    }

    /** D-169: bài đã hủy khóa chấm (Viết và Nói): 409, không ghi dòng sửa; đọc lịch sử vẫn được. */
    @Test
    void cancelledAssignmentLocksGrading() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = assign(f, f.writing());
        long session = submitWriting(f, s, asg);
        long sp = assign(f, f.speaking());
        long sub = submitSpeaking(f, s, sp);
        call(HttpMethod.PUT, gradeUrl(f, asg, session), f.teacher(), Map.of("score", 60)).andExpect(status().isCreated());
        for (long a : new long[] {asg, sp}) {
            postJson(base(f) + "/" + a + "/cancel", f.teacher(), Map.of()).andExpect(status().isOk());
        }
        call(HttpMethod.PUT, gradeUrl(f, asg, session), f.teacher(), Map.of("score", 90)).andExpect(status().isConflict());
        call(HttpMethod.PUT, gradeUrl(f, asg, session), f.teacher(), Map.of("comment", "Muộn")).andExpect(status().isConflict());
        call(HttpMethod.PUT, gradeUrl(f, sp, sub), f.teacher(), Map.of("score", 7)).andExpect(status().isConflict());
        assertEquals(1, overridesOf(session));
        assertEquals(0, count("select count(*) from grade_overrides where speaking_submission_id = ?", sub));
        call(HttpMethod.GET, base(f) + "/" + asg + "/attempts/" + session + "/grades", f.teacher(), null).andExpect(status().isOk());
    }

    /** D-170: điểm Nói là bội số của 0,5; Viết giữ thang cũ. */
    @Test
    void speakingScoreStepIsHalfBand() throws Exception {
        var f = fixture();
        var s = member(f);
        long sp = assign(f, f.speaking());
        long sub = submitSpeaking(f, s, sp);
        call(HttpMethod.PUT, gradeUrl(f, sp, sub), f.teacher(), Map.of("score", 7.3)).andExpect(status().isBadRequest());
        assertEquals(0, count("select count(*) from grade_overrides where speaking_submission_id = ?", sub));
        call(HttpMethod.PUT, gradeUrl(f, sp, sub), f.teacher(), Map.of("score", 7.5)).andExpect(status().isCreated());
        long asg = assign(f, f.writing());
        long session = submitWriting(f, s, asg);
        call(HttpMethod.PUT, gradeUrl(f, asg, session), f.teacher(), Map.of("score", 85.12)).andExpect(status().isCreated());
    }
}
