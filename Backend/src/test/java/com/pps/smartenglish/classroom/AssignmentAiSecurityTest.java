package com.pps.smartenglish.classroom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.ai.AiTestSupport;
import com.pps.smartenglish.ai.provider.AiCallException;
import com.pps.smartenglish.ai.speaking.FakeSpeakingEngineClient;
import com.pps.smartenglish.support.FakeStorageConfig.FakeStorage;
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

/**
 * Tester đợt #61: phân quyền theo bản ghi và lạm dụng assignmentId cho bài giao câu Viết/Nói (FR-ASG-01..04, D-130, NFR-SEC-05),
 * điểm ghép với hồ sơ năng lực (#68).
 */
class AssignmentAiSecurityTest extends AiTestSupport {

    private static final String ESSAY = "I agree with online learning because it is flexible. However, students need discipline.";

    @Autowired FakeStorage storage;
    @Autowired FakeSpeakingEngineClient engine;

    private record Fx(long course, long classId, long qA, long sA, long topicA, Account teacher) {}

    @BeforeEach
    void reset() {
        engine.reset();
        storage.objects.clear();
    }

    private Account withRole(String role) throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = ?", u.id(), role);
        return login(u.email(), PASSWORD);
    }

    private ResultActions call(HttpMethod method, String url, String token, Object body) throws Exception {
        var req = MockMvcRequestBuilders.request(method, url);
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return send(req, token);
    }

    private long bank(long topic, String mode) {
        return insert("question_banks", Map.of("topic_id", topic, "title", "Bộ " + UUID.randomUUID().toString().substring(0, 4),
                "is_trial", false, "status", "PUBLISHED", "mode", mode));
    }

    private Fx fixture() throws Exception {
        long c = insert("courses", Map.of("slug", "asgsec-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED",
                "delivery_mode", "CLASS_BASED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED", "sort_order", 0));
        long ta = insert("topics", Map.of("level_id", lv, "title", "Unit A", "is_free_trial", false, "status", "PUBLISHED", "sort_order", 0));
        long bankA = bank(ta, "PRACTICE");
        long qA = writingQuestion(bankA);
        long sA = question(bankA, "SPEAKING", 2, "Describe a trip.", "{\"part\":1,\"prompt\":\"Describe a trip.\"}", "{}");
        long cls = insert("classes", Map.of("course_id", c, "name", "Lớp " + UUID.randomUUID().toString().substring(0, 4),
                "starts_on", LocalDate.now().minusDays(10), "ends_on", LocalDate.now().plusDays(60), "mode", "ONLINE", "capacity", 20));
        var t = withRole("TEACHER");
        jdbc.update("insert into class_staff (class_id, user_id, role, assigned_from) values (?, ?, 'TEACHER', ?)", cls, t.id(), LocalDate.now().minusDays(10));
        return new Fx(c, cls, qA, sA, ta, t);
    }

    private Account member(Fx f) throws Exception {
        var s = student(3);
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', TIMESTAMP '2020-01-01 00:00:00', TIMESTAMP '2099-01-01 00:00:00')",
                s.id(), f.course());
        jdbc.update("insert into class_members (class_id, course_id, user_id, status, live_key, joined_at) values (?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP)",
                f.classId(), f.course(), s.id(), f.course() + ":" + s.id());
        return s;
    }

    private String base(Fx f) {
        return "/api/classes/" + f.classId() + "/assignments";
    }

    private long assignOk(Fx f, long questionId, int dueHours) throws Exception {
        return idOf(call(HttpMethod.POST, base(f), f.teacher().accessToken(), Map.of("questionId", questionId,
                "dueAt", Instant.now().plus(dueHours, ChronoUnit.HOURS).toString())).andExpect(status().isCreated()), "id");
    }

    private ResultActions startWriting(Account s, long q, Long asg) throws Exception {
        guard.resetRateLimit(s.id());
        var body = new HashMap<String, Object>();
        body.put("questionId", q);
        body.put("assignmentId", asg);
        return postJson("/api/writing/sessions", s, body);
    }

    private ResultActions submitWriting(Account s, long session) throws Exception {
        guard.resetRateLimit(s.id());
        return postJson("/api/writing/sessions/" + session + "/submit", s, Map.of("finalText", ESSAY), UUID.randomUUID().toString());
    }

    private long writeAssigned(Account s, long q, Long asg) throws Exception {
        long session = idOf(startWriting(s, q, asg).andExpect(status().isCreated()), "sessionId");
        submitWriting(s, session).andExpect(status().isAccepted());
        return session;
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

    private long speakingPermit(Account s, long q, Long asg) throws Exception {
        guard.resetRateLimit(s.id());
        var body = new HashMap<String, Object>();
        body.put("questionId", q);
        body.put("assignmentId", asg);
        long id = idOf(postJson("/api/speaking/submissions", s, body).andExpect(status().isCreated()), "submissionId");
        storage.put(jdbc.queryForObject("select object_key from speaking_submissions where id = ?", String.class, id), wav(20));
        return id;
    }

    private ResultActions completeSpeaking(Account s, long id) throws Exception {
        guard.resetRateLimit(s.id());
        return postJson("/api/speaking/submissions/" + id + "/complete", s, Map.of());
    }

    @Test
    void teacherCannotGradeSubmissionOfAnotherAssignmentOrPracticeSession() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg1 = assignOk(f, f.qA(), 48);
        long asg2 = assignOk(f, f.qA(), 72);
        writeAssigned(s, f.qA(), asg1);
        long s2 = writeAssigned(s, f.qA(), asg2);
        long practice = writeAssigned(s, f.qA(), null);
        runner.runDueNow();
        String t = f.teacher().accessToken();

        // id phiên của bài giao khác, hay phiên luyện tập, không chấm được dưới bài giao 1
        call(HttpMethod.PUT, base(f) + "/" + asg1 + "/attempts/" + s2 + "/grade", t, Map.of("score", 50)).andExpect(status().isNotFound());
        call(HttpMethod.PUT, base(f) + "/" + asg1 + "/attempts/" + practice + "/grade", t, Map.of("score", 50)).andExpect(status().isNotFound());
        call(HttpMethod.GET, base(f) + "/" + asg1 + "/attempts/" + practice + "/grades", t, null).andExpect(status().isNotFound());
        assertEquals(0, count("select count(*) from grade_overrides where writing_session_id in (?, ?)", s2, practice));
    }

    @Test
    void writingAndSpeakingGradeStayWithinScale() throws Exception {
        var f = fixture();
        var s = member(f);
        long wa = assignOk(f, f.qA(), 48);
        long session = writeAssigned(s, f.qA(), wa);
        long sa = assignOk(f, f.sA(), 48);
        long sp = speakingPermit(s, f.sA(), sa);
        completeSpeaking(s, sp).andExpect(status().isAccepted());
        runner.runDueNow();
        String t = f.teacher().accessToken();
        String w = base(f) + "/" + wa + "/attempts/" + session + "/grade";
        String k = base(f) + "/" + sa + "/attempts/" + sp + "/grade";

        call(HttpMethod.PUT, w, t, Map.of("score", 100)).andExpect(status().isCreated());
        call(HttpMethod.PUT, w, t, Map.of("score", 100.5)).andExpect(status().isBadRequest());
        call(HttpMethod.PUT, w, t, Map.of("score", -1)).andExpect(status().isBadRequest());
        call(HttpMethod.PUT, k, t, Map.of("score", 9)).andExpect(status().isCreated());
        call(HttpMethod.PUT, k, t, Map.of("score", 9.5)).andExpect(status().isBadRequest());
        call(HttpMethod.PUT, k, t, Map.of("score", -0.5)).andExpect(status().isBadRequest());
        // id lượt nói dưới bài giao Viết (và ngược lại) không lẫn bảng
        call(HttpMethod.PUT, base(f) + "/" + wa + "/attempts/" + sp + "/grade", t, Map.of("score", 1)).andExpect(status().isNotFound());
        call(HttpMethod.PUT, base(f) + "/" + sa + "/attempts/" + session + "/grade", t, Map.of("score", 1)).andExpect(status().isNotFound());
    }

    @Test
    void failedWritingJobIsNotGradable() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = assignOk(f, f.qA(), 48);
        fake.failNext(1, new AiCallException(false, "Mô hình từ chối trả lời", null, null));
        long session = writeAssigned(s, f.qA(), asg);
        runner.runDueNow();
        call(HttpMethod.PUT, base(f) + "/" + asg + "/attempts/" + session + "/grade", f.teacher().accessToken(), Map.of("score", 50))
                .andExpect(status().isNotFound());
    }

    @Test
    void examBankQuestionCannotBeAssigned() throws Exception {
        var f = fixture();
        long exam = writingQuestion(bank(f.topicA(), "EXAM"));
        call(HttpMethod.POST, base(f), f.teacher().accessToken(), Map.of("questionId", exam,
                "dueAt", Instant.now().plus(48, ChronoUnit.HOURS).toString())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.questionId").exists());
    }

    @Test
    void clientCannotOverrideQuestionType() throws Exception {
        var f = fixture();
        call(HttpMethod.POST, base(f), f.teacher().accessToken(), Map.of("questionId", f.qA(), "questionType", "SPEAKING",
                "dueAt", Instant.now().plus(48, ChronoUnit.HOURS).toString())).andExpect(status().isCreated())
                .andExpect(jsonPath("$.questionType").value("WRITING"));
    }

    @Test
    void studentCannotCreateAssignment() throws Exception {
        var f = fixture();
        var s = member(f);
        call(HttpMethod.POST, base(f), s.accessToken(), Map.of("questionId", f.qA(),
                "dueAt", Instant.now().plus(48, ChronoUnit.HOURS).toString())).andExpect(status().isNotFound());
        assertEquals(0, count("select count(*) from assignments where class_id = ?", f.classId()));
    }

    @Test
    void practiceOnSameQuestionDoesNotCountAsAssignmentSubmission() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = assignOk(f, f.qA(), 48);
        writeAssigned(s, f.qA(), null);
        runner.runDueNow();
        call(HttpMethod.GET, base(f) + "/" + asg + "/mine", s.accessToken(), null).andExpect(jsonPath("$.status").value("NOT_SUBMITTED"))
                .andExpect(jsonPath("$.attemptId").doesNotExist());
    }

    @Test
    void otherStudentCannotReadAssignedWritingOrSpeaking() throws Exception {
        var f = fixture();
        var owner = member(f);
        var peer = member(f);
        long wa = assignOk(f, f.qA(), 48);
        long session = writeAssigned(owner, f.qA(), wa);
        long sa = assignOk(f, f.sA(), 48);
        long sp = speakingPermit(owner, f.sA(), sa);
        completeSpeaking(owner, sp).andExpect(status().isAccepted());
        runner.runDueNow();

        getJson("/api/writing/sessions/" + session, peer).andExpect(status().isNotFound());
        submitWriting(peer, session).andExpect(status().isNotFound());
        getJson("/api/speaking/submissions/" + sp, peer).andExpect(status().isNotFound());
        completeSpeaking(peer, sp).andExpect(status().isNotFound());
        // bài của bạn cùng lớp không lọt vào kết quả của mình
        call(HttpMethod.GET, base(f) + "/" + wa + "/mine", peer.accessToken(), null).andExpect(jsonPath("$.attemptId").doesNotExist());
    }

    @Test
    void cancelledSpeakingAssignmentBlocksCompleteAndPrompt() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = assignOk(f, f.sA(), 48);
        long id = speakingPermit(s, f.sA(), asg);
        call(HttpMethod.POST, base(f) + "/" + asg + "/cancel", f.teacher().accessToken(), null).andExpect(status().isOk());

        completeSpeaking(s, id).andExpect(status().isNotFound());
        getJson("/api/speaking/questions/" + f.sA() + "?assignmentId=" + asg, s).andExpect(status().isNotFound());
        assertEquals(0, count("select count(*) from ai_jobs where kind = 'SPEAKING_GRADE' and user_id = ?", s.id()));
    }

    @Test
    void secondSpeakingPermitCannotBeCompletedAfterSubmission() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = assignOk(f, f.sA(), 48);
        long first = speakingPermit(s, f.sA(), asg);
        long second = speakingPermit(s, f.sA(), asg); // xin trước khi nộp lượt đầu
        completeSpeaking(s, first).andExpect(status().isAccepted());
        completeSpeaking(s, second).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("assignment_submitted"));
        assertEquals(1, count("select count(*) from ai_jobs where kind = 'SPEAKING_GRADE' and user_id = ?", s.id()));
    }

    @Test
    void speakingPermitOfWritingAssignmentIsRejected() throws Exception {
        var f = fixture();
        var s = member(f);
        long writingAsg = assignOk(f, f.qA(), 48);
        guard.resetRateLimit(s.id());
        postJson("/api/speaking/submissions", s, Map.of("questionId", f.sA(), "assignmentId", writingAsg)).andExpect(status().isNotFound());
        getJson("/api/speaking/questions/" + f.sA() + "?assignmentId=abc", s).andExpect(status().isNotFound());
    }

    @Test
    void secondOpenWritingSessionCannotSubmitAgain() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = assignOk(f, f.qA(), 48);
        long first = idOf(startWriting(s, f.qA(), asg).andExpect(status().isCreated()), "sessionId");
        // mô phỏng hai lần mở đồng thời: có hai phiên ACTIVE của cùng bài giao
        jdbc.update("insert into writing_sessions (user_id, question_id, step, turns, status, assignment_id) "
                + "select user_id, question_id, step, turns, 'ACTIVE', assignment_id from writing_sessions where id = ?", first);
        long second = jdbc.queryForObject("select max(id) from writing_sessions where assignment_id = ?", Long.class, asg);
        submitWriting(s, first).andExpect(status().isAccepted());
        submitWriting(s, second).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("assignment_submitted"));
        assertEquals(1, count("select count(*) from ai_jobs where kind = 'WRITING_GRADE' and user_id = ?", s.id()));
    }

    @Test
    void withdrawnMemberCannotContinueAssignedWork() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = assignOk(f, f.qA(), 48);
        long session = idOf(startWriting(s, f.qA(), asg).andExpect(status().isCreated()), "sessionId");
        jdbc.update("update class_members set status = 'WITHDRAWN' where user_id = ? and class_id = ?", s.id(), f.classId());
        submitWriting(s, session).andExpect(status().isNotFound());
        assertEquals(0, count("select count(*) from ai_jobs where kind = 'WRITING_GRADE' and user_id = ?", s.id()));
    }

    @Test
    void assignedAiWorkKeepsLearningProfilesWorking() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = assignOk(f, f.qA(), 48);
        writeAssigned(s, f.qA(), asg);
        runner.runDueNow();
        getJson("/api/me/learning-profile", s).andExpect(status().isOk());
        call(HttpMethod.GET, "/api/classes/" + f.classId() + "/students/" + s.id() + "/learning-profile", f.teacher().accessToken(), null)
                .andExpect(status().isOk());
    }
}
