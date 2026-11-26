package com.pps.smartenglish.classroom;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.ai.AiTestSupport;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Tester D-153: bài giao đã hủy, phía học viên. Người nhận xem lại kết quả và bài đã nộp (chỉ đọc), không bắt đầu hay làm tiếp;
 * người không nhận, đã rút khỏi lớp, lớp khác vẫn 404.
 */
class AssignmentCancelledTesterTest extends AiTestSupport {

    /** Khóa học tuần tự: bankB ở Unit B chưa mở, chỉ vào được nhờ bài giao. */
    private record Fx(long course, long classId, long bankA, long bankB, long qB, long aiBank, long qW, long qS, Account teacher) {}

    private Fx fixture() throws Exception {
        long c = insert("courses", Map.of("slug", "cx-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED",
                "delivery_mode", "CLASS_BASED", "sequential", true));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED", "sort_order", 0));
        long ta = insert("topics", Map.of("level_id", lv, "title", "Unit A", "is_free_trial", false, "status", "PUBLISHED", "sort_order", 0));
        long tb = insert("topics", Map.of("level_id", lv, "title", "Unit B", "is_free_trial", false, "status", "PUBLISHED", "sort_order", 1));
        long bankA = insert("question_banks", Map.of("topic_id", ta, "title", "Bộ A", "is_trial", false, "status", "PUBLISHED", "mode", "PRACTICE"));
        insert("questions", Map.of("bank_id", bankA, "type", "CONNECTIVE_MCQ", "prompt", "Câu A"));
        long bankB = insert("question_banks", Map.of("topic_id", tb, "title", "Bộ B", "is_trial", false, "status", "PUBLISHED", "mode", "PRACTICE"));
        long qB = insert("questions", Map.of("bank_id", bankB, "type", "CONNECTIVE_MCQ", "prompt", "Câu B"));
        long aiBank = insert("question_banks", Map.of("topic_id", tb, "title", "Bộ AI", "is_trial", false, "status", "PUBLISHED", "mode", "PRACTICE"));
        long qW = writingQuestion(aiBank);
        long qS = question(aiBank, "SPEAKING", 2, "Describe a trip.", "{\"part\":1,\"prompt\":\"Describe a trip.\"}", "{}");
        long cls = insert("classes", Map.of("course_id", c, "name", "Lớp", "starts_on", LocalDate.now().minusDays(10),
                "ends_on", LocalDate.now().plusDays(60), "mode", "ONLINE", "capacity", 20));
        var tu = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = 'TEACHER'", tu.id());
        var teacher = login(tu.email(), PASSWORD);
        jdbc.update("insert into class_staff (class_id, user_id, role, assigned_from) values (?, ?, 'TEACHER', ?)", cls, teacher.id(), LocalDate.now().minusDays(10));
        return new Fx(c, cls, bankA, bankB, qB, aiBank, qW, qS, teacher);
    }

    private Account member(Fx f) throws Exception {
        var s = student(3);
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', TIMESTAMP '2020-01-01 00:00:00', TIMESTAMP '2099-01-01 00:00:00')", s.id(), f.course());
        jdbc.update("insert into class_members (class_id, course_id, user_id, status, live_key, joined_at) values (?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP)",
                f.classId(), f.course(), s.id(), f.course() + ":" + s.id());
        return s;
    }

    private String base(Fx f) {
        return "/api/classes/" + f.classId() + "/assignments";
    }

    private long assign(Fx f, String key, long id, List<Long> students) throws Exception {
        var m = new HashMap<String, Object>();
        m.put(key, id);
        m.put("dueAt", Instant.now().plus(48, ChronoUnit.HOURS).toString());
        if (students != null) m.put("studentIds", students);
        return idOf(postJson(base(f), f.teacher(), m).andExpect(status().isCreated()), "id");
    }

    private void cancel(Fx f, long asg) throws Exception {
        postJson(base(f) + "/" + asg + "/cancel", f.teacher(), Map.of()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    private long start(Fx f, long asg, Account s) throws Exception {
        return idOf(postJson(base(f) + "/" + asg + "/start", s, Map.of()).andExpect(status().isOk()), "id");
    }

    private Map<String, Object> aiBody(long q, long asg) {
        var body = new HashMap<String, Object>();
        body.put("questionId", q);
        body.put("assignmentId", asg);
        return body;
    }

    @Test
    void cancelledSubmittedBankAssignmentStaysReadable() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = assign(f, "bankId", f.bankB(), List.of(s.id()));
        long attempt = start(f, asg, s);
        postJson("/api/attempts/" + attempt + "/submit", s, Map.of("answers", List.of())).andExpect(status().isOk());
        cancel(f, asg);

        getJson("/api/me/assignments/" + asg, s).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED")).andExpect(jsonPath("$.myStatus").value("SUBMITTED"));
        getJson(base(f) + "/" + asg + "/mine", s).andExpect(status().isOk()).andExpect(jsonPath("$.attemptId").value(attempt));
        postJson(base(f) + "/" + asg + "/start", s, Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("assignment_cancelled"));
        // "Xem bài đã nộp" mở /bai-tap/{bankId}/ket-qua/{attemptId}: màn đó cần cả lượt và bộ câu hỏi
        getJson("/api/attempts/" + attempt, s).andExpect(status().isOk());
        getJson("/api/banks/" + f.bankB(), s).andExpect(status().isOk());
    }

    @Test
    void openAttemptOfCancelledAssignmentCannotBeContinued() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = assign(f, "bankId", f.bankB(), null);
        long attempt = start(f, asg, s);
        cancel(f, asg);

        getJson("/api/me/assignments/" + asg, s).andExpect(status().isOk()).andExpect(jsonPath("$.myStatus").value("NOT_SUBMITTED"));
        int draft = send(MockMvcRequestBuilders.put("/api/attempts/" + attempt + "/draft").contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":[]}"), s.accessToken()).andReturn().getResponse().getStatus();
        int submit = postJson("/api/attempts/" + attempt + "/submit", s, Map.of("answers", List.of())).andReturn().getResponse().getStatus();
        assertEquals(0, count("select count(*) from student_exercise_attempts where id = ? and submitted_at is not null", attempt),
                "D-153: bài đã hủy không làm tiếp được, nhưng lượt dở vẫn nộp được (draft=" + draft + ", submit=" + submit + ")");
        assertTrue(draft >= 400, "lưu nháp lượt của bài đã hủy phải bị chặn: " + draft);
    }

    @Test
    void cancelledAssignmentHiddenFromNonRecipientWithdrawnAndOtherClass() throws Exception {
        var f = fixture();
        var s = member(f);
        var classmate = member(f);
        long asg = assign(f, "bankId", f.bankB(), List.of(s.id()));
        long attempt = start(f, asg, s);
        postJson("/api/attempts/" + attempt + "/submit", s, Map.of("answers", List.of())).andExpect(status().isOk());
        cancel(f, asg);

        var other = fixture();
        var outsider = member(other);
        for (var who : List.of(classmate, outsider, other.teacher())) {
            getJson("/api/me/assignments/" + asg, who).andExpect(status().isNotFound());
            getJson(base(f) + "/" + asg + "/mine", who).andExpect(status().isNotFound());
            postJson(base(f) + "/" + asg + "/start", who, Map.of()).andExpect(status().isNotFound());
        }
        // đúng id nhưng đường dẫn lớp của người dò: vẫn 404
        getJson(base(other) + "/" + asg + "/mine", outsider).andExpect(status().isNotFound());
        getJson(base(other) + "/" + asg + "/mine", s).andExpect(status().isNotFound());
        // danh sách của người nhận không có bài hủy
        getJson("/api/me/assignments", s).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));

        jdbc.update("update class_members set status = 'WITHDRAWN', live_key = null where user_id = ? and class_id = ?", s.id(), f.classId());
        getJson("/api/me/assignments/" + asg, s).andExpect(status().isNotFound());
        getJson(base(f) + "/" + asg + "/mine", s).andExpect(status().isNotFound());
        postJson(base(f) + "/" + asg + "/start", s, Map.of()).andExpect(status().isNotFound());
    }

    @Test
    void cancelledSpeakingAssignmentResultStaysViewable() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = assign(f, "questionId", f.qS(), List.of(s.id()));
        guard.resetRateLimit(s.id());
        long sub = idOf(postJson("/api/speaking/submissions", s, aiBody(f.qS(), asg)).andExpect(status().isCreated()), "submissionId");
        jdbc.update("update speaking_submissions set status = 'GRADED' where id = ?", sub);
        cancel(f, asg);

        getJson("/api/me/speaking?questionId=" + f.qS() + "&limit=1&assignmentId=" + asg, s).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(sub));
        getJson("/api/speaking/submissions/" + sub, s).andExpect(status().isOk());
        // SpeakingPage gọi đề kèm assignmentId trước khi hiện kết quả: 404 ở đây làm "Xem bài đã nộp" thành màn lỗi
        getJson("/api/speaking/questions/" + f.qS() + "?assignmentId=" + asg, s).andExpect(status().isOk());
    }

    @Test
    void cancelledSpeakingAssignmentRejectsNewSubmission() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = assign(f, "questionId", f.qS(), List.of(s.id()));
        cancel(f, asg);
        guard.resetRateLimit(s.id());
        int code = postJson("/api/speaking/submissions", s, aiBody(f.qS(), asg)).andReturn().getResponse().getStatus();
        assertTrue(code == 404 || code == 409, "tạo lượt nói cho bài đã hủy: " + code);
        assertEquals(0, count("select count(*) from speaking_submissions where user_id = ?", s.id()));
    }

    @Test
    void cancelledWritingAssignmentIsReadOnly() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = assign(f, "questionId", f.qW(), List.of(s.id()));
        guard.resetRateLimit(s.id());
        long session = idOf(postJson("/api/writing/sessions", s, aiBody(f.qW(), asg)).andExpect(status().isCreated()), "sessionId");
        cancel(f, asg);

        getJson("/api/me/writing?questionId=" + f.qW() + "&limit=1&assignmentId=" + asg, s).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sessionId").value(session));
        getJson("/api/writing/sessions/" + session, s).andExpect(status().isOk());
        guard.resetRateLimit(s.id());
        int answer = postJson("/api/writing/sessions/" + session + "/answer", s, Map.of("text", "I think so.")).andReturn().getResponse().getStatus();
        assertTrue(answer == 404 || answer == 409, "phiên của bài đã hủy vẫn nhận câu trả lời: " + answer);
        guard.resetRateLimit(s.id());
        int created = postJson("/api/writing/sessions", s, aiBody(f.qW(), asg)).andReturn().getResponse().getStatus();
        assertTrue(created == 404 || created == 409, "tạo phiên mới cho bài đã hủy: " + created);
        assertEquals(1, count("select count(*) from writing_sessions where user_id = ?", s.id()));
    }

    @Test
    void checkOnAttemptOfCancelledAssignmentIsRejected() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = assign(f, "bankId", f.bankB(), List.of(s.id()));
        long attempt = start(f, asg, s);
        cancel(f, asg);
        postJson("/api/attempts/" + attempt + "/questions/" + f.qB() + "/check", s, Map.of("answer", Map.of("choiceId", 1)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("assignment_cancelled"));
        assertEquals(0, count("select count(*) from attempt_question_checks where attempt_id = ?", attempt));
    }

    /** Bài giao một câu Viết/Nói không mở cả bộ, kể cả sau khi hủy: GET bank của Unit khóa vẫn 403. */
    @Test
    void cancelledSingleQuestionAssignmentDoesNotOpenBank() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = assign(f, "questionId", f.qW(), List.of(s.id()));
        getJson("/api/banks/" + f.aiBank(), s).andExpect(status().isForbidden());
        cancel(f, asg);
        getJson("/api/banks/" + f.aiBank(), s).andExpect(status().isForbidden());
    }

    /** Đề trả cho người nhận bài đã hủy không có đáp án (giống lúc làm bài). */
    @Test
    void readableBankOfCancelledAssignmentHasNoAnswerKey() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = assign(f, "bankId", f.bankB(), List.of(s.id()));
        cancel(f, asg);
        String body = getJson("/api/banks/" + f.bankB(), s).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(!body.contains("answerKey") && !body.contains("answer_key") && !body.contains("isCorrect") && !body.contains("explanation"), body);
    }

    /**
     * D-147 + D-153: lượt dở của bài đã hủy không nộp được nhưng vẫn là "lượt mở" của bộ, nên chặn mọi lượt mới trên bộ đó
     * (tự học khi unit đã mở, hoặc bài giao mới cùng bộ) bằng attempt_in_progress trỏ tới lượt không làm tiếp được.
     */
    @Test
    void openAttemptOfCancelledAssignmentDoesNotBlockNewAssignmentOfSameBank() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = assign(f, "bankId", f.bankB(), List.of(s.id()));
        start(f, asg, s);
        cancel(f, asg);
        long again = assign(f, "bankId", f.bankB(), List.of(s.id()));
        postJson(base(f) + "/" + again + "/start", s, Map.of()).andExpect(status().isOk()); // hiện: 409 attempt_in_progress
    }

    @Test
    void openAttemptOfCancelledAssignmentDoesNotBlockSelfStudy() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = assign(f, "bankId", f.bankA(), List.of(s.id())); // Unit A đã mở: tự học được
        start(f, asg, s);
        cancel(f, asg);
        int code = postJson("/api/banks/" + f.bankA() + "/attempts", s, Map.of()).andReturn().getResponse().getStatus();
        assertTrue(code == 200 || code == 201, "tự học bộ đã mở bị chặn bởi lượt dở của bài đã hủy: " + code);
    }

    @Test
    void openListSkipsAttemptOfCancelledAssignment() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = assign(f, "bankId", f.bankA(), List.of(s.id()));
        start(f, asg, s);
        getJson("/api/me/attempts?bankId=" + f.bankA() + "&open=true", s).andExpect(jsonPath("$.data", hasSize(1)));
        cancel(f, asg);
        getJson("/api/me/attempts?bankId=" + f.bankA() + "&open=true", s).andExpect(jsonPath("$.data", hasSize(0)));
    }
}
