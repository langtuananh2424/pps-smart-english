package com.pps.smartenglish.classroom;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.ai.AiTestSupport;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Tester đợt khu giảng dạy (#61, #62): nhánh SQL Viết/Nói của recipientCounts, người nhận rời lớp, quyền gọi /assignable
 * (trợ giảng hết hạn, giáo vụ, kế toán, học viên đã xong lớp), khớp luật với POST /assignments, số câu SQL theo số bài.
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class AssignableTesterTest extends AiTestSupport {

    @Autowired EntityManagerFactory emf;

    private record Fx(long course, long level, long topic, long bank, long qW, long qS, long classId, Account teacher) {}

    private Account withRole(String role) throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = ?", u.id(), role);
        return login(u.email(), PASSWORD);
    }

    private Fx fixture(String courseStatus) throws Exception {
        long c = insert("courses", Map.of("slug", "asbt-" + UUID.randomUUID(), "title", "Khóa", "status", courseStatus,
                "delivery_mode", "CLASS_BASED", "sequential", true));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED", "sort_order", 0));
        long t = insert("topics", Map.of("level_id", lv, "title", "Unit A", "is_free_trial", false, "status", "PUBLISHED", "sort_order", 0));
        long b = insert("question_banks", Map.of("topic_id", t, "title", "Bộ", "is_trial", false, "status", "PUBLISHED", "mode", "PRACTICE"));
        long qW = writingQuestion(b);
        long qS = question(b, "SPEAKING", 2, "Describe a trip.", "{\"part\":1,\"prompt\":\"Describe a trip.\"}", "{}");
        long cls = insert("classes", Map.of("course_id", c, "name", "Lớp " + UUID.randomUUID().toString().substring(0, 4),
                "starts_on", LocalDate.now().minusDays(10), "ends_on", LocalDate.now().plusDays(60), "mode", "ONLINE", "capacity", 20));
        var teacher = withRole("TEACHER");
        jdbc.update("insert into class_staff (class_id, user_id, role, assigned_from) values (?, ?, 'TEACHER', ?)", cls, teacher.id(), LocalDate.now().minusDays(10));
        return new Fx(c, lv, t, b, qW, qS, cls, teacher);
    }

    private Fx fixture() throws Exception {
        return fixture("PUBLISHED");
    }

    private Account member(Fx f) throws Exception {
        var s = student(3);
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', TIMESTAMP '2020-01-01 00:00:00', TIMESTAMP '2099-01-01 00:00:00')", s.id(), f.course());
        jdbc.update("insert into class_members (class_id, course_id, user_id, status, live_key, joined_at) values (?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP)",
                f.classId(), f.course(), s.id(), f.course() + ":" + s.id());
        return s;
    }

    private long aiAssignment(Fx f, long questionId, String type) {
        return insert("assignments", Map.of("class_id", f.classId(), "bank_id", f.bank(), "question_id", questionId, "question_type", type,
                "due_at", Timestamp.from(Instant.now().plus(2, ChronoUnit.DAYS)), "status", "ACTIVE", "created_by", f.teacher().id()));
    }

    private long bankAssignment(Fx f, Instant due) {
        return insert("assignments", Map.of("class_id", f.classId(), "bank_id", f.bank(), "due_at", Timestamp.from(due),
                "status", "ACTIVE", "created_by", f.teacher().id()));
    }

    /** Phiên viết đã ở trạng thái status; jobStatus null: chưa có job chấm. */
    private void writing(long asg, Fx f, Account s, String status, String jobStatus) {
        jdbc.update("insert into writing_sessions (user_id, question_id, turns, status, assignment_id, submitted_at) values (?, ?, '[]' format json, ?, ?, ?)",
                s.id(), f.qW(), status, asg, Timestamp.from(Instant.now()));
        if (jobStatus != null) {
            long ws = jdbc.queryForObject("select max(id) from writing_sessions where assignment_id = ? and user_id = ?", Long.class, asg, s.id());
            jdbc.update("insert into ai_jobs (user_id, kind, status, output, session_id) values (?, 'WRITING_GRADE', ?, '{\"scorePercent\":70}' format json, ?)",
                    s.id(), jobStatus, ws);
            jdbc.update("update writing_sessions set grade_job_id = (select max(id) from ai_jobs where session_id = ?) where id = ?", ws, ws);
        }
    }

    private void speaking(long asg, Fx f, Account s, String status) {
        jdbc.update("insert into speaking_submissions (user_id, question_id, status, object_key, max_seconds, max_bytes, upload_expires_at, result, assignment_id, submitted_at) "
                + "values (?, ?, ?, ?, 60, 1000000, ?, '{\"overall\":5.5}' format json, ?, ?)", s.id(), f.qS(), status, "k-" + UUID.randomUUID(),
                Timestamp.from(Instant.now()), asg, Timestamp.from(Instant.now()));
    }

    private JsonNode staffList(Fx f) throws Exception {
        String body = getJson("/api/classes/" + f.classId() + "/assignments", f.teacher()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private JsonNode row(JsonNode list, long id) {
        for (JsonNode n : list) {
            if (n.get("id").asLong() == id) return n;
        }
        throw new AssertionError("không có bài " + id);
    }

    /** Số người nhận trong màn tiến độ và số dòng đã nộp (SUBMITTED, LATE): nguồn mà giáo viên đối chiếu với recipients/submitted. */
    private int[] progress(Fx f, long asg) throws Exception {
        String body = getJson("/api/classes/" + f.classId() + "/assignments/" + asg + "/progress", f.teacher()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode n = json.readTree(body);
        JsonNode rows = n.isArray() ? n : n.path("rows");
        int done = 0;
        for (JsonNode r : rows) {
            String st = r.path("status").asText();
            if (st.equals("SUBMITTED") || st.equals("LATE")) done++;
        }
        return new int[] {rows.size(), done};
    }

    @Test
    void writingSubmittedCountsOnlySubmittedSessionsWithoutFailedJobOncePerStudent() throws Exception {
        var f = fixture();
        var done = member(f);
        var failed = member(f);
        var draft = member(f);
        var twice = member(f);
        var pending = member(f);
        long asg = aiAssignment(f, f.qW(), "WRITING");
        writing(asg, f, done, "SUBMITTED", "DONE");
        writing(asg, f, failed, "SUBMITTED", "FAILED");
        writing(asg, f, draft, "ACTIVE", null);
        writing(asg, f, twice, "SUBMITTED", "DONE");
        writing(asg, f, twice, "SUBMITTED", "PENDING");
        writing(asg, f, pending, "SUBMITTED", null);
        var r = row(staffList(f), asg);
        assertEquals(5, r.get("recipients").asInt());
        assertEquals(3, r.get("submitted").asInt()); // done, twice (một lần), pending
        int[] p = progress(f, asg);
        assertEquals(p[0], r.get("recipients").asInt(), "recipients phải khớp số dòng màn tiến độ");
        assertEquals(p[1], r.get("submitted").asInt(), "submitted phải khớp số dòng đã nộp ở màn tiến độ");
    }

    @Test
    void speakingSubmittedCountsGradingAndGradedOnly() throws Exception {
        var f = fixture();
        var graded = member(f);
        var grading = member(f);
        var unclear = member(f);
        var retried = member(f);
        var waiting = member(f);
        long asg = aiAssignment(f, f.qS(), "SPEAKING");
        speaking(asg, f, graded, "GRADED");
        speaking(asg, f, grading, "GRADING");
        speaking(asg, f, unclear, "UNCLEAR");
        speaking(asg, f, retried, "FAILED");
        speaking(asg, f, retried, "GRADED");
        speaking(asg, f, waiting, "AWAITING_UPLOAD");
        var r = row(staffList(f), asg);
        assertEquals(5, r.get("recipients").asInt());
        assertEquals(3, r.get("submitted").asInt());
        int[] p = progress(f, asg);
        assertEquals(p[0], r.get("recipients").asInt());
        assertEquals(p[1], r.get("submitted").asInt());
    }

    @Test
    void submissionOfAnotherAssignmentIsNotCounted() throws Exception {
        var f = fixture();
        var s = member(f);
        long a1 = aiAssignment(f, f.qW(), "WRITING");
        long a2 = insert("assignments", Map.of("class_id", f.classId(), "bank_id", f.bank(), "question_id", f.qW(), "question_type", "WRITING",
                "due_at", Timestamp.from(Instant.now().plus(3, ChronoUnit.DAYS)), "status", "ACTIVE", "created_by", f.teacher().id()));
        writing(a1, f, s, "SUBMITTED", "DONE");
        var list = staffList(f);
        assertEquals(1, row(list, a1).get("submitted").asInt());
        assertEquals(0, row(list, a2).get("submitted").asInt());
    }

    @Test
    void targetedStudentWhoLeftClassCountsTheSameInListAndProgress() throws Exception {
        var f = fixture();
        var stay = member(f);
        var left = member(f);
        long asg = idOf(postJson("/api/classes/" + f.classId() + "/assignments", f.teacher(),
                Map.of("bankId", f.bank(), "dueAt", Instant.now().plus(2, ChronoUnit.DAYS).toString(), "studentIds", List.of(stay.id(), left.id())))
                .andExpect(status().isCreated()), "id");
        jdbc.update("update class_members set status = 'WITHDRAWN' where class_id = ? and user_id = ?", f.classId(), left.id());
        var r = row(staffList(f), asg);
        int[] p = progress(f, asg);
        assertEquals(p[0], r.get("recipients").asInt(), "danh sách báo " + r.get("recipients") + " người nhận, màn tiến độ có " + p[0] + " dòng");
    }

    @Test
    void cancelledAssignmentStillHasCountsForStaff() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = bankAssignment(f, Instant.now().plus(1, ChronoUnit.DAYS));
        insert("student_exercise_attempts", Map.of("user_id", s.id(), "bank_id", f.bank(), "assignment_id", asg,
                "submitted_at", Timestamp.from(Instant.now()), "score", 1, "max_score", 1));
        postJson("/api/classes/" + f.classId() + "/assignments/" + asg + "/cancel", f.teacher(), Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        var r = row(staffList(f), asg);
        assertEquals(1, r.get("recipients").asInt());
        assertEquals(1, r.get("submitted").asInt());
    }

    @Test
    void studentNeverSeesCountsOnAnyAssignmentEndpoint() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = bankAssignment(f, Instant.now().plus(1, ChronoUnit.DAYS));
        getJson("/api/me/assignments", s).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].recipients").doesNotExist()).andExpect(jsonPath("$[0].submitted").doesNotExist());
        getJson("/api/me/assignments/" + asg, s).andExpect(status().isOk())
                .andExpect(jsonPath("$.recipients").doesNotExist()).andExpect(jsonPath("$.submitted").doesNotExist());
    }

    @Test
    void assignableAccessMatrix() throws Exception {
        var f = fixture();
        String url = "/api/classes/" + f.classId() + "/assignable";
        var expired = withRole("ASSISTANT");
        jdbc.update("insert into class_staff (class_id, user_id, role, assigned_from, assigned_to) values (?, ?, 'ASSISTANT', ?, ?)",
                f.classId(), expired.id(), LocalDate.now().minusDays(10), LocalDate.now().minusDays(1));
        var future = withRole("ASSISTANT");
        jdbc.update("insert into class_staff (class_id, user_id, role, assigned_from) values (?, ?, 'ASSISTANT', ?)",
                f.classId(), future.id(), LocalDate.now().plusDays(1));
        var revoked = withRole("ASSISTANT");
        jdbc.update("insert into class_staff (class_id, user_id, role, assigned_from, revoked_at) values (?, ?, 'ASSISTANT', ?, CURRENT_TIMESTAMP)",
                f.classId(), revoked.id(), LocalDate.now().minusDays(10));
        var live = withRole("ASSISTANT");
        jdbc.update("insert into class_staff (class_id, user_id, role, assigned_from) values (?, ?, 'ASSISTANT', ?)",
                f.classId(), live.id(), LocalDate.now().minusDays(1));
        var completed = member(f);
        jdbc.update("update class_members set status = 'COMPLETED' where class_id = ? and user_id = ?", f.classId(), completed.id());
        for (var who : List.of(expired, future, revoked, completed, withRole("ACCOUNTANT"), withRole("TEACHER"))) {
            getJson(url, who).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("class_not_found"));
        }
        getJson(url, live).andExpect(status().isOk());
        getJson(url, withRole("TRAINING_MANAGER")).andExpect(status().isOk());
        mvc.perform(MockMvcRequestBuilders.get(url)).andExpect(status().isUnauthorized());
        getJson("/api/classes/abc/assignable", f.teacher()).andExpect(status().isNotFound());
        getJson("/api/classes/99999999/assignable", f.teacher()).andExpect(status().isNotFound());
    }

    /** Mọi mục cây trả về phải giao được (cùng luật POST /assignments): khóa nháp thì POST từ chối, nên cây cũng phải rỗng. */
    @Test
    void draftCourseContentIsNotOfferedSinceCreateRejectsIt() throws Exception {
        var f = fixture("DRAFT");
        postJson("/api/classes/" + f.classId() + "/assignments", f.teacher(),
                Map.of("bankId", f.bank(), "dueAt", Instant.now().plus(2, ChronoUnit.DAYS).toString())).andExpect(status().isBadRequest());
        getJson("/api/classes/" + f.classId() + "/assignable", f.teacher()).andExpect(status().isOk())
                .andExpect(jsonPath("$.topics[*].banks[*]", hasSize(0)))
                .andExpect(jsonPath("$.topics[*].aiQuestions[*]", hasSize(0)));
    }

    @Test
    void bankOfDraftLessonIsHidden() throws Exception {
        var f = fixture();
        long lesson = insert("lessons", Map.of("topic_id", f.topic(), "type", "CONNECTIVE_VIDEO", "title", "Bài nháp", "status", "DRAFT"));
        long b = insert("question_banks", Map.of("topic_id", f.topic(), "lesson_id", lesson, "title", "Bộ bài nháp", "is_trial", false, "status", "PUBLISHED", "mode", "PRACTICE"));
        writingQuestion(b);
        getJson("/api/classes/" + f.classId() + "/assignable", f.teacher()).andExpect(status().isOk())
                .andExpect(jsonPath("$.topics[0].banks", hasSize(1)))
                .andExpect(jsonPath("$.topics[0].aiQuestions", hasSize(2)));
    }

    @Test
    void longPromptIsCutTo120CharsAndHtmlIsReturnedAsData() throws Exception {
        var f = fixture();
        String longPrompt = "<img src=x onerror=alert(1)>" + "Đây là đề bài rất dài ".repeat(20);
        question(f.bank(), "WRITING", 3, longPrompt, "{\"task\":\"x\",\"requiredWords\":100}", "{}");
        String body = getJson("/api/classes/" + f.classId() + "/assignable", f.teacher()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode qs = json.readTree(body).at("/topics/0/aiQuestions");
        JsonNode last = qs.get(qs.size() - 1);
        assertEquals(120, last.get("title").asText().length());
        assertTrue(last.get("title").asText().startsWith("<img"));
    }

    /** P0 checklist: số câu SQL của danh sách bài (nhân sự, có recipients/submitted) không tăng theo số bài. */
    @Test
    void staffListQueryCountDoesNotGrowWithAssignments() throws Exception {
        var f = fixture();
        for (int i = 0; i < 3; i++) member(f);
        bankAssignment(f, Instant.now().plus(1, ChronoUnit.DAYS));
        aiAssignment(f, f.qW(), "WRITING");
        var stats = emf.unwrap(SessionFactory.class).getStatistics();
        staffList(f);
        stats.clear();
        staffList(f);
        long few = stats.getPrepareStatementCount();
        for (int i = 0; i < 20; i++) {
            bankAssignment(f, Instant.now().plus(2 + i, ChronoUnit.DAYS));
            aiAssignment(f, i % 2 == 0 ? f.qW() : f.qS(), i % 2 == 0 ? "WRITING" : "SPEAKING");
        }
        stats.clear();
        assertEquals(42, staffList(f).size());
        assertEquals(few, stats.getPrepareStatementCount());
    }

    /** P0 checklist: GET /unlocks một truy vấn tên cho mọi dòng; POST mở cho nhiều học viên không thêm một câu SQL tên mỗi học viên. */
    @Test
    void unlockGrantorNamesAreLoadedOnce() throws Exception {
        var f = fixture();
        long t2 = insert("topics", Map.of("level_id", f.level(), "title", "Unit B", "is_free_trial", false, "status", "PUBLISHED", "sort_order", 1));
        var stats = emf.unwrap(SessionFactory.class).getStatistics();
        String url = "/api/classes/" + f.classId() + "/unlocks";
        var one = member(f);
        postJson(url, f.teacher(), Map.of("topicId", f.topic(), "studentIds", List.of(one.id()))).andExpect(status().isCreated());
        stats.clear();
        postJson(url, f.teacher(), Map.of("topicId", t2, "studentIds", List.of(one.id()))).andExpect(status().isCreated());
        long single = stats.getPrepareStatementCount();
        List<Long> many = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) many.add(member(f).id());
        long t3 = insert("topics", Map.of("level_id", f.level(), "title", "Unit C", "is_free_trial", false, "status", "PUBLISHED", "sort_order", 2));
        stats.clear();
        postJson(url, f.teacher(), Map.of("topicId", t3, "studentIds", many)).andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(10)));
        long ten = stats.getPrepareStatementCount();
        // mỗi học viên 1 INSERT; phần tên người mở không được thêm câu nào theo số học viên
        self(single, ten);
    }

    private static void self(long single, long ten) {
        long perStudent = (ten - single) / 9;
        assertTrue(perStudent <= 1, "mỗi học viên thêm " + perStudent + " câu SQL (single=" + single + ", ten=" + ten + ")");
    }
}
