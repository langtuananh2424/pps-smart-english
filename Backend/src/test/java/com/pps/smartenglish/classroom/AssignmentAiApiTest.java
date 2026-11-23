package com.pps.smartenglish.classroom;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.ai.provider.AiCallException;
import com.pps.smartenglish.ai.AiTestSupport;
import com.pps.smartenglish.ai.speaking.FakeSpeakingEngineClient;
import com.pps.smartenglish.classroom.service.AssignmentReminderService;
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
 * FR-ASG-01..04, D-121, D-130, NFR-SEC-05: giao riêng câu Viết/Nói chấm AI. Làm trong ngữ cảnh bài giao không trừ lượt,
 * vẫn giữ giới hạn tần suất; ngoài bài giao luồng và trừ lượt giữ nguyên.
 */
class AssignmentAiApiTest extends AiTestSupport {

    private static final String ESSAY = "I agree with online learning because it is flexible. However, students need discipline.";

    @Autowired FakeStorage storage;
    @Autowired FakeSpeakingEngineClient engine;
    @Autowired AssignmentReminderService reminders;

    /** Khóa có lớp học tuần tự: unit A mở (qA viết, sA nói), unit B bị khóa (qB viết, sB nói). */
    private record Fx(long course, long classId, long qA, long qB, long sB, Account teacher) {}

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

    private long bank(long topic) {
        return insert("question_banks", Map.of("topic_id", topic, "title", "Bộ " + UUID.randomUUID().toString().substring(0, 4),
                "is_trial", false, "status", "PUBLISHED", "mode", "PRACTICE"));
    }

    private long newClass(long course) {
        return insert("classes", Map.of("course_id", course, "name", "Lớp " + UUID.randomUUID().toString().substring(0, 4),
                "starts_on", LocalDate.now().minusDays(10), "ends_on", LocalDate.now().plusDays(60), "mode", "ONLINE", "capacity", 20));
    }

    private Account teacherOf(long classId) throws Exception {
        var t = withRole("TEACHER");
        jdbc.update("insert into class_staff (class_id, user_id, role, assigned_from) values (?, ?, 'TEACHER', ?)", classId, t.id(), LocalDate.now().minusDays(10));
        return t;
    }

    private Fx fixture() throws Exception {
        long c = insert("courses", Map.of("slug", "asgai-" + UUID.randomUUID(), "title", "Khóa Viết Nói", "status", "PUBLISHED",
                "delivery_mode", "CLASS_BASED", "sequential", true));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED", "sort_order", 0));
        long ta = insert("topics", Map.of("level_id", lv, "title", "Unit A", "is_free_trial", false, "status", "PUBLISHED", "sort_order", 0));
        long tb = insert("topics", Map.of("level_id", lv, "title", "Unit B", "is_free_trial", false, "status", "PUBLISHED", "sort_order", 1));
        long qA = writingQuestion(bank(ta));
        long bankB = bank(tb);
        long qB = writingQuestion(bankB);
        long sB = question(bankB, "SPEAKING", 2, "Describe a trip.", "{\"part\":1,\"prompt\":\"Describe a trip.\"}", "{}");
        long cls = newClass(c);
        return new Fx(c, cls, qA, qB, sB, teacherOf(cls));
    }

    private Account member(Fx f) throws Exception {
        var s = student(3);
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', TIMESTAMP '2020-01-01 00:00:00', TIMESTAMP '2099-01-01 00:00:00')",
                s.id(), f.course());
        jdbc.update("insert into class_members (class_id, course_id, user_id, status, live_key, joined_at) values (?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP)",
                f.classId(), f.course(), s.id(), f.course() + ":" + s.id());
        return s;
    }

    /** Lượt còn lại gộp: pool trừ phần đã dùng của ví định kỳ (khóa mặc định 20 lượt/tuần). Một lần chấm trừ đúng 1 ở một trong hai nguồn. */
    private int wallet(Account u) {
        return balance(u) - count("select coalesce(sum(used), 0) from ai_period_usage where user_id = ?", u.id());
    }

    private void exhaust(Fx f, Account s) {
        jdbc.update("update courses set ai_period_quota = 0 where id = ?", f.course());
        jdbc.update("update user_ai_quota set balance = 0 where user_id = ?", s.id());
        jdbc.update("delete from ai_period_usage where user_id = ?", s.id());
    }

    private String base(Fx f) {
        return "/api/classes/" + f.classId() + "/assignments";
    }

    private Instant inHours(int h) {
        return Instant.now().plus(h, ChronoUnit.HOURS);
    }

    private ResultActions assign(Fx f, Account teacher, Long questionId, Instant due, List<Long> students) throws Exception {
        var m = new HashMap<String, Object>();
        m.put("questionId", questionId);
        m.put("dueAt", due.toString());
        if (students != null) m.put("studentIds", students);
        return call(HttpMethod.POST, base(f), teacher.accessToken(), m);
    }

    private long assignOk(Fx f, long questionId, Instant due, List<Long> students) throws Exception {
        return idOf(assign(f, f.teacher(), questionId, due, students).andExpect(status().isCreated()), "id");
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

    private long writeAssigned(Account s, long q, long asg) throws Exception {
        long session = idOf(startWriting(s, q, asg).andExpect(status().isCreated()), "sessionId");
        submitWriting(s, session).andExpect(status().isAccepted());
        return session;
    }

    private String progressOf(Fx f, long asg, Account who, String field) throws Exception {
        String res = call(HttpMethod.GET, base(f) + "/" + asg + "/progress", f.teacher().accessToken(), null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (var n : json.readTree(res)) {
            if (n.get("userId").asLong() == who.id()) {
                return n.path(field).isNull() ? null : n.get(field).asText();
            }
        }
        return null;
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

    /** Xin quyền tải lên cho bài nói rồi tải file lên kho giả; trả id lượt nói. */
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
    void teacherAssignsWritingQuestionAndValidates() throws Exception {
        var f = fixture();
        var s = member(f);
        Instant due = inHours(48);
        long asg = idOf(assign(f, f.teacher(), f.qB(), due, null).andExpect(status().isCreated()).andExpect(jsonPath("$.questionId").value(f.qB()))
                .andExpect(jsonPath("$.questionType").value("WRITING")).andExpect(jsonPath("$.wholeClass").value(true)), "id");
        assign(f, f.teacher(), f.qB(), due, null).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("assignment_duplicate"));
        assign(f, f.teacher(), f.sB(), due, null).andExpect(status().isCreated()).andExpect(jsonPath("$.questionType").value("SPEAKING"));
        assign(f, f.teacher(), f.qB(), inHours(72), null).andExpect(status().isCreated()); // hạn khác thì không trùng

        assign(f, f.teacher(), f.qB(), Instant.now().minusSeconds(60), null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.dueAt").exists());
        assign(f, f.teacher(), 999999L, due, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.questionId").exists());
        long mcq = question(jdbc.queryForObject("select bank_id from questions where id = ?", Long.class, f.qA()), "CONNECTIVE_MCQ", 5, "Câu", "{}", "{}");
        assign(f, f.teacher(), mcq, due, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.questionId").exists());
        assign(f, f.teacher(), f.qB(), due, List.of(999999L)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.studentIds").exists());
        // thiếu cả hai, hoặc có cả hai
        call(HttpMethod.POST, base(f), f.teacher().accessToken(), Map.of("dueAt", due.toString())).andExpect(status().isBadRequest());
        call(HttpMethod.POST, base(f), f.teacher().accessToken(), Map.of("dueAt", due.toString(), "questionId", f.qB(),
                "bankId", jdbc.queryForObject("select bank_id from questions where id = ?", Long.class, f.qB()))).andExpect(status().isBadRequest());
        // câu của khóa khác
        var other = fixture();
        assign(f, f.teacher(), other.qA(), due, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.questionId").exists());

        call(HttpMethod.GET, "/api/me/assignments", s.accessToken(), null).andExpect(jsonPath("$[?(@.id == " + asg + ")].questionType").value("WRITING"));
        // làm qua lượt làm bài thường không được: câu Viết chạy phiên riêng
        call(HttpMethod.POST, base(f) + "/" + asg + "/start", s.accessToken(), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("assignment_not_attemptable"));
    }

    @Test
    void assignedWritingDoesNotChargeAndOpensLockedUnit() throws Exception {
        var f = fixture();
        var s = member(f);
        // chưa giao: unit B đang khóa
        startWriting(s, f.qB(), null).andExpect(status().isForbidden());
        long asg = assignOk(f, f.qB(), inHours(48), List.of(s.id()));

        long session = writeAssigned(s, f.qB(), asg);
        assertEquals(3, wallet(s), "bài giao không trừ ví");
        assertEquals(0, count("select count(*) from ai_quota_ledger where user_id = ?", s.id()));
        assertEquals(1, count("select count(*) from ai_jobs where session_id = ? and kind = 'WRITING_GRADE' and charged = false", session));
        assertEquals(asg, jdbc.queryForObject("select assignment_id from writing_sessions where id = ?", Long.class, session));

        // chưa chấm xong thì đã tính là nộp, chưa có điểm AI
        assertEquals("SUBMITTED", progressOf(f, asg, s, "status"));
        assertEquals(null, progressOf(f, asg, s, "aiScore"));
        assertEquals(1, runner.runDueNow());
        call(HttpMethod.GET, base(f) + "/" + asg + "/mine", s.accessToken(), null).andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.attemptId").value(session)).andExpect(jsonPath("$.aiScore").value(70)).andExpect(jsonPath("$.maxScore").value(100))
                .andExpect(jsonPath("$.finalScore").value(70));
        assertEquals(3, wallet(s));

        // mỗi bài giao nộp một lần
        startWriting(s, f.qB(), asg).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("assignment_submitted"));
        postJson("/api/writing/sessions/" + session + "/submit", s, Map.of("finalText", ESSAY), UUID.randomUUID().toString())
                .andExpect(status().isConflict());
        assertEquals(3, wallet(s));
    }

    @Test
    void outsideAssignmentStillChargesAndAnswerStepsAreFree() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = assignOk(f, f.qA(), inHours(48), null);

        long practice = idOf(startWriting(s, f.qA(), null).andExpect(status().isCreated()), "sessionId");
        submitWriting(s, practice).andExpect(status().isAccepted());
        assertEquals(2, wallet(s), "luyện tập ngoài bài giao vẫn trừ lượt");

        // hết lượt: luyện tập bị chặn, bài giao vẫn làm được
        exhaust(f, s);
        startWriting(s, f.qA(), null).andExpect(status().isPaymentRequired());
        long session = idOf(startWriting(s, f.qA(), asg).andExpect(status().isCreated()), "sessionId");
        guard.resetRateLimit(s.id());
        postJson("/api/writing/sessions/" + session + "/answer", s, Map.of("text", "Because it is flexible.")).andExpect(status().isAccepted());
        // làm tiếp: mở lại bài giao trả về đúng phiên đang dở
        assertEquals(session, idOf(startWriting(s, f.qA(), asg).andExpect(status().isCreated()), "sessionId"));
        runner.runDueNow();
        submitWriting(s, session).andExpect(status().isAccepted());
        assertEquals(0, wallet(s));
    }

    @Test
    void lateSubmissionIsGradedAndMarkedLate() throws Exception {
        var f = fixture();
        var onTime = member(f);
        var late = member(f);
        var missing = member(f);
        long asg = assignOk(f, f.qA(), inHours(48), null);
        writeAssigned(onTime, f.qA(), asg);
        long lateSession = idOf(startWriting(late, f.qA(), asg).andExpect(status().isCreated()), "sessionId");
        jdbc.update("update assignments set due_at = TIMESTAMPADD(HOUR, -1, CURRENT_TIMESTAMP) where id = ?", asg);
        submitWriting(late, lateSession).andExpect(status().isAccepted()); // quá hạn vẫn nộp được (D-121)
        assertEquals(1, count("select count(*) from ai_jobs where session_id = ?", lateSession));
        jdbc.update("update writing_sessions set submitted_at = TIMESTAMPADD(HOUR, -2, CURRENT_TIMESTAMP) where assignment_id = ? and user_id = ?", asg, onTime.id());
        runner.runDueNow();

        assertEquals("SUBMITTED", progressOf(f, asg, onTime, "status"));
        assertEquals("LATE", progressOf(f, asg, late, "status"));
        assertEquals("OVERDUE", progressOf(f, asg, missing, "status"));
        assertEquals("70", progressOf(f, asg, late, "aiScore"), "nộp trễ vẫn chấm");
        call(HttpMethod.GET, base(f) + "/" + asg + "/mine", late.accessToken(), null).andExpect(jsonPath("$.status").value("LATE"));
        call(HttpMethod.GET, "/api/me/assignments", missing.accessToken(), null).andExpect(jsonPath("$[0].myStatus").value("OVERDUE"));
        call(HttpMethod.GET, "/api/me/assignments", late.accessToken(), null).andExpect(jsonPath("$[0].myStatus").value("LATE"));
    }

    @Test
    void nonRecipientsAndWrongQuestionAreBlocked() throws Exception {
        var f = fixture();
        var chosen = member(f);
        var left = member(f);
        long asg = assignOk(f, f.qA(), inHours(48), List.of(chosen.id()));

        startWriting(left, f.qA(), asg).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("assignment_not_found"));
        startWriting(chosen, f.qB(), asg).andExpect(status().isNotFound()); // bài giao câu khác
        startWriting(chosen, f.qA(), 999999L).andExpect(status().isNotFound());
        // học viên lớp khác (cùng khóa) cũng không dùng được assignmentId của lớp này
        var otherClass = newClass(f.course());
        var stranger = student(3);
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', TIMESTAMP '2020-01-01 00:00:00', TIMESTAMP '2099-01-01 00:00:00')", stranger.id(), f.course());
        jdbc.update("insert into class_members (class_id, course_id, user_id, status, live_key, joined_at) values (?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP)",
                otherClass, f.course(), stranger.id(), f.course() + ":" + stranger.id());
        startWriting(stranger, f.qA(), asg).andExpect(status().isNotFound());
        // không có lượt nào bị trừ hay phiên nào được mở
        assertEquals(3, wallet(left));
        assertEquals(0, count("select count(*) from writing_sessions where assignment_id = ?", asg));

        // hủy bài giao thì người nhận cũng bị chặn; phiên đang dở không nộp được
        long session = idOf(startWriting(chosen, f.qA(), asg).andExpect(status().isCreated()), "sessionId");
        call(HttpMethod.POST, base(f) + "/" + asg + "/cancel", f.teacher().accessToken(), null).andExpect(status().isOk());
        submitWriting(chosen, session).andExpect(status().isNotFound());
        startWriting(chosen, f.qA(), asg).andExpect(status().isNotFound());
    }

    @Test
    void otherClassTeacherCannotAssignViewOrGrade() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = assignOk(f, f.qA(), inHours(48), null);
        long session = writeAssigned(s, f.qA(), asg);
        runner.runDueNow();

        long otherClass = newClass(f.course());
        var outsider = teacherOf(otherClass);
        String url = base(f) + "/" + asg;
        assign(f, outsider, f.qA(), inHours(10), null).andExpect(status().isNotFound());
        call(HttpMethod.GET, url + "/progress", outsider.accessToken(), null).andExpect(status().isNotFound());
        call(HttpMethod.PUT, url + "/attempts/" + session + "/grade", outsider.accessToken(), Map.of("score", 50)).andExpect(status().isNotFound());
        call(HttpMethod.GET, url + "/attempts/" + session + "/grades", outsider.accessToken(), null).andExpect(status().isNotFound());
        call(HttpMethod.PUT, "/api/classes/" + otherClass + "/assignments/" + asg + "/attempts/" + session + "/grade", outsider.accessToken(),
                Map.of("score", 50)).andExpect(status().isNotFound());
        call(HttpMethod.POST, url + "/cancel", outsider.accessToken(), null).andExpect(status().isNotFound());
        // học viên không chấm, không xem tiến độ
        call(HttpMethod.PUT, url + "/attempts/" + session + "/grade", s.accessToken(), Map.of("score", 50)).andExpect(status().isNotFound());
        call(HttpMethod.GET, url + "/progress", s.accessToken(), null).andExpect(status().isNotFound());
    }

    @Test
    void teacherGradeOnWritingIsAppendOnlyAndStudentIsNotified() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = assignOk(f, f.qA(), inHours(48), null);
        long session = writeAssigned(s, f.qA(), asg);
        runner.runDueNow();
        var t = f.teacher().accessToken();
        String url = base(f) + "/" + asg + "/attempts/" + session;

        call(HttpMethod.PUT, url + "/grade", t, Map.of()).andExpect(status().isBadRequest());
        call(HttpMethod.PUT, url + "/grade", t, Map.of("score", 101)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.score").exists());
        call(HttpMethod.PUT, url + "/grade", t, Map.of("score", 85, "comment", "Bố cục tốt")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.attemptId").value(session));
        call(HttpMethod.PUT, url + "/grade", t, Map.of("comment", "Chú ý liên từ")).andExpect(status().isCreated());
        call(HttpMethod.GET, url + "/grades", t, null).andExpect(jsonPath("$", hasSize(2)));
        call(HttpMethod.PUT, base(f) + "/" + asg + "/attempts/999999/grade", t, Map.of("score", 1)).andExpect(status().isNotFound());
        assertEquals(2, count("select count(*) from notifications where user_id = ? and type = 'ASSIGNMENT_GRADED'", s.id()));

        call(HttpMethod.GET, base(f) + "/" + asg + "/mine", s.accessToken(), null).andExpect(jsonPath("$.aiScore").value(70))
                .andExpect(jsonPath("$.finalScore").value(85.0)).andExpect(jsonPath("$.teacherComment").value("Chú ý liên từ"));
        assertEquals("85.0", progressOf(f, asg, s, "finalScore"));
    }

    @Test
    void writingGradingFailureChargesNothingAndAllowsResubmit() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = assignOk(f, f.qA(), inHours(48), null);
        fake.failNext(1, new AiCallException(false, "Mô hình từ chối trả lời", null, null));
        long session = writeAssigned(s, f.qA(), asg);
        assertEquals(1, runner.runDueNow());
        assertEquals(1, count("select count(*) from ai_jobs where session_id = ? and status = 'FAILED'", session));
        assertEquals(3, wallet(s), "job bài giao lỗi không hoàn thêm lượt");
        assertEquals(0, count("select count(*) from ai_quota_ledger where user_id = ?", s.id()));
        assertEquals("NOT_SUBMITTED", progressOf(f, asg, s, "status"));

        writeAssigned(s, f.qA(), asg); // chấm lỗi thì nộp lại được
        runner.runDueNow();
        assertEquals("SUBMITTED", progressOf(f, asg, s, "status"));
        assertEquals(3, wallet(s));
    }

    @Test
    void writingRateLimitStillAppliesToAssignedWork() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = assignOk(f, f.qA(), inHours(48), null);
        long session = idOf(startWriting(s, f.qA(), asg).andExpect(status().isCreated()), "sessionId"); // lần chạm 1
        for (int i = 0; i < 4; i++) { // lần chạm 2..5; câu thứ tư đủ câu đào sâu nên phiên sang READY
            postJson("/api/writing/sessions/" + session + "/answer", s, Map.of("text", "Câu " + i)).andExpect(status().is2xxSuccessful());
            runner.runDueNow();
        }
        postJson("/api/writing/sessions/" + session + "/draft", s, Map.of()).andExpect(status().isTooManyRequests());
    }

    @Test
    void assignedSpeakingDoesNotChargeAndTracksProgress() throws Exception {
        var f = fixture();
        var s = member(f);
        var other = member(f);
        // chưa giao thì unit B khóa; có giao thì làm được, kể cả khi ví cạn
        guard.resetRateLimit(s.id());
        postJson("/api/speaking/submissions", s, Map.of("questionId", f.sB())).andExpect(status().isForbidden());
        long asg = idOf(assign(f, f.teacher(), f.sB(), inHours(48), null).andExpect(status().isCreated()), "id");
        exhaust(f, s);
        long id = speakingPermit(s, f.sB(), asg);
        assertEquals(asg, jdbc.queryForObject("select assignment_id from speaking_submissions where id = ?", Long.class, id));
        completeSpeaking(s, id).andExpect(status().isAccepted());
        assertEquals(1, count("select count(*) from ai_jobs where kind = 'SPEAKING_GRADE' and charged = false and user_id = ?", s.id()));
        assertEquals("SUBMITTED", progressOf(f, asg, s, "status"));
        assertEquals("NOT_SUBMITTED", progressOf(f, asg, other, "status"));
        assertEquals(1, runner.runDueNow());
        assertEquals("GRADED", jdbc.queryForObject("select status from speaking_submissions where id = ?", String.class, id));
        assertEquals(0, wallet(s));
        assertEquals(0, count("select count(*) from ai_quota_ledger where user_id = ?", s.id()));
        call(HttpMethod.GET, base(f) + "/" + asg + "/mine", s.accessToken(), null).andExpect(jsonPath("$.attemptId").value(id))
                .andExpect(jsonPath("$.maxScore").value(9)).andExpect(jsonPath("$.aiScore").isNumber());

        // đã nộp thì chặn lượt mới và chặn xác nhận lượt dang dở
        guard.resetRateLimit(s.id());
        postJson("/api/speaking/submissions", s, Map.of("questionId", f.sB(), "assignmentId", asg)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("assignment_submitted"));

        // giáo viên chấm lại điểm nói, trong thang 9
        String grade = base(f) + "/" + asg + "/attempts/" + id + "/grade";
        call(HttpMethod.PUT, grade, f.teacher().accessToken(), Map.of("score", 10)).andExpect(status().isBadRequest());
        call(HttpMethod.PUT, grade, f.teacher().accessToken(), Map.of("score", 7.5, "comment", "Phát âm rõ")).andExpect(status().isCreated());
        call(HttpMethod.GET, base(f) + "/" + asg + "/mine", s.accessToken(), null).andExpect(jsonPath("$.finalScore").value(7.5));
        // người không được giao, lớp khác
        postJson("/api/speaking/submissions", other, Map.of("questionId", f.sB(), "assignmentId", 999999L)).andExpect(status().isNotFound());
    }

    @Test
    void unclearAssignedSpeakingRefundsNothingAndAllowsRetry() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = assignOk(f, f.sB(), inHours(48), null);
        engine.setTranscript(FakeSpeakingEngineClient.spoken(3, 0.1, 0.9)); // dưới 5 từ: không rõ
        long id = speakingPermit(s, f.sB(), asg);
        completeSpeaking(s, id).andExpect(status().isAccepted());
        assertEquals(1, runner.runDueNow());
        assertEquals("UNCLEAR", jdbc.queryForObject("select status from speaking_submissions where id = ?", String.class, id));
        assertEquals(3, wallet(s), "bài giao không trừ nên cũng không có lượt để hoàn");
        assertEquals(0, count("select count(*) from ai_quota_ledger where user_id = ?", s.id()));
        assertEquals("NOT_SUBMITTED", progressOf(f, asg, s, "status"));

        engine.reset();
        long again = speakingPermit(s, f.sB(), asg); // thu lại được
        completeSpeaking(s, again).andExpect(status().isAccepted());
        runner.runDueNow();
        assertEquals("SUBMITTED", progressOf(f, asg, s, "status"));
        assertEquals(3, wallet(s));
    }

    @Test
    void speakingRateLimitStillAppliesToAssignedWork() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = assignOk(f, f.sB(), inHours(48), null);
        guard.resetRateLimit(s.id());
        for (int i = 0; i < 5; i++) {
            postJson("/api/speaking/submissions", s, Map.of("questionId", f.sB(), "assignmentId", asg)).andExpect(status().isCreated());
        }
        postJson("/api/speaking/submissions", s, Map.of("questionId", f.sB(), "assignmentId", asg)).andExpect(status().isTooManyRequests());
    }

    @Test
    void dueReminderSkipsThoseWhoSubmittedAiWork() throws Exception {
        var f = fixture();
        var done = member(f);
        var pending = member(f);
        long asg = assignOk(f, f.qA(), Instant.now().plus(2, ChronoUnit.HOURS), null);
        writeAssigned(done, f.qA(), asg);
        reminders.sendDue();
        assertEquals(1, count("select count(*) from notifications where user_id = ? and type = 'ASSIGNMENT_DUE'", pending.id()));
        assertEquals(0, count("select count(*) from notifications where user_id = ? and type = 'ASSIGNMENT_DUE'", done.id()));
    }

    @Test
    void questionAssignmentOpensOnlyThatQuestionInAssignmentContext() throws Exception {
        var f = fixture();
        var s = member(f);
        long bankB = jdbc.queryForObject("select bank_id from questions where id = ?", Long.class, f.qB());
        long asg = assignOk(f, f.qB(), inHours(48), List.of(s.id()));

        // không mở bộ chứa câu, không mở câu khác trong bộ, không làm câu đó ngoài ngữ cảnh bài giao
        call(HttpMethod.GET, "/api/banks/" + bankB, s.accessToken(), null).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("unit_locked"));
        call(HttpMethod.POST, "/api/banks/" + bankB + "/attempts", s.accessToken(), null).andExpect(status().isForbidden());
        startWriting(s, f.qB(), null).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("unit_locked"));
        guard.resetRateLimit(s.id());
        postJson("/api/speaking/submissions", s, Map.of("questionId", f.sB())).andExpect(status().isForbidden());
        guard.resetRateLimit(s.id());
        postJson("/api/speaking/submissions", s, Map.of("questionId", f.sB(), "assignmentId", asg)).andExpect(status().isNotFound());
        getJson("/api/speaking/questions/" + f.sB(), s).andExpect(status().isForbidden());
        getJson("/api/speaking/questions/" + f.sB() + "?assignmentId=" + asg, s).andExpect(status().isNotFound());

        // đúng câu, kèm assignmentId thì làm được
        long session = idOf(startWriting(s, f.qB(), asg).andExpect(status().isCreated()), "sessionId");
        submitWriting(s, session).andExpect(status().isAccepted());
        getJson("/api/writing/sessions/" + session, s).andExpect(status().isOk());
        // nộp xong vẫn không mở bộ
        call(HttpMethod.GET, "/api/banks/" + bankB, s.accessToken(), null).andExpect(status().isForbidden());

        // bài giao Nói: xem đề được qua assignmentId
        long asgS = assignOk(f, f.sB(), inHours(48), List.of(s.id()));
        getJson("/api/speaking/questions/" + f.sB() + "?assignmentId=" + asgS, s).andExpect(status().isOk());
        getJson("/api/speaking/questions/" + f.sB(), s).andExpect(status().isForbidden());
    }

    @Test
    void bankAssignmentsAreUnchangedByQuestionAssignments() throws Exception {
        var f = fixture();
        var s = member(f);
        long bankA = jdbc.queryForObject("select bank_id from questions where id = ?", Long.class, f.qA());
        String due = inHours(48).toString();
        long asg = idOf(call(HttpMethod.POST, base(f), f.teacher().accessToken(), Map.of("bankId", bankA, "dueAt", due))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.questionId").doesNotExist()), "id");
        // giao trùng cả bộ cùng hạn vẫn bị chặn
        call(HttpMethod.POST, base(f), f.teacher().accessToken(), Map.of("bankId", bankA, "dueAt", due)).andExpect(status().isConflict());
        call(HttpMethod.GET, base(f), s.accessToken(), null).andExpect(jsonPath("$", hasSize(1)));
        call(HttpMethod.POST, base(f) + "/" + asg + "/start", s.accessToken(), null).andExpect(status().isOk());
    }
}
