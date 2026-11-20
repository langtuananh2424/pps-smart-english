package com.pps.smartenglish.classroom;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.support.ApiTestBase;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** FR-CLS-05..07, D-122, D-124, NFR-SEC-05: bảng tiến độ lớp, mở sớm unit, báo cáo số buổi dạy. */
class ProgressApiTest extends ApiTestBase {

    private static final String PASSWORD = "mat-khau-1";

    /** Khóa học tuần tự 3 unit; unit A có bài giảng, bài tập, kiểm tra chương; unit B, C có bài giảng. */
    private record Fx(long course, long classId, long topicA, long topicB, long topicC, long lessonA, long lessonB, long lessonC, long practiceA,
                      long testA, Account teacher) {}

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
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

    private long lesson(long topic) {
        var l = new HashMap<String, Object>(Map.of("topic_id", topic, "type", "CONNECTIVE_VIDEO", "title", "Bài " + topic, "status", "PUBLISHED"));
        l.put("video_source", "YOUTUBE");
        l.put("video_ref", "abc");
        l.put("duration_sec", 60);
        return insert("lessons", l);
    }

    private long bank(long topic, String mode) {
        long b = insert("question_banks", Map.of("topic_id", topic, "title", mode + " " + topic, "is_trial", false, "status", "PUBLISHED", "mode", mode));
        insert("questions", Map.of("bank_id", b, "type", "CONNECTIVE_MCQ", "prompt", "Câu hỏi"));
        return b;
    }

    private Fx fixture(boolean sequential) throws Exception {
        long c = insert("courses", Map.of("slug", "prg-" + UUID.randomUUID(), "title", "Khóa tiến độ", "status", "PUBLISHED",
                "delivery_mode", "CLASS_BASED", "sequential", sequential));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED", "sort_order", 0));
        long ta = insert("topics", Map.of("level_id", lv, "title", "Unit A", "is_free_trial", false, "status", "PUBLISHED", "sort_order", 0));
        long tb = insert("topics", Map.of("level_id", lv, "title", "Unit B", "is_free_trial", false, "status", "PUBLISHED", "sort_order", 1));
        long tc = insert("topics", Map.of("level_id", lv, "title", "Unit C", "is_free_trial", false, "status", "PUBLISHED", "sort_order", 2));
        long practice = bank(ta, "PRACTICE");
        long test = bank(ta, "EXAM");
        jdbc.update("update topics set chapter_test_bank_id = ? where id = ?", test, ta);
        long cls = insert("classes", Map.of("course_id", c, "name", "Lớp " + UUID.randomUUID().toString().substring(0, 4),
                "starts_on", LocalDate.now().minusDays(10), "ends_on", LocalDate.now().plusDays(60), "mode", "ONLINE", "capacity", 20));
        var teacher = withRole("TEACHER");
        jdbc.update("insert into class_staff (class_id, user_id, role, assigned_from) values (?, ?, 'TEACHER', ?)", cls, teacher.id(), LocalDate.now().minusDays(10));
        return new Fx(c, cls, ta, tb, tc, lesson(ta), lesson(tb), lesson(tc), practice, test, teacher);
    }

    private Account student(Fx f) throws Exception {
        var s = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', TIMESTAMP '2020-01-01 00:00:00', TIMESTAMP '2099-01-01 00:00:00')",
                s.id(), f.course());
        jdbc.update("insert into class_members (class_id, course_id, user_id, status, live_key, joined_at) values (?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP)",
                f.classId(), f.course(), s.id(), f.course() + ":" + s.id());
        return s;
    }

    private void completeLesson(Account s, long lessonId) {
        jdbc.update("insert into lesson_progress (user_id, lesson_id, watched_sec, completed_at) values (?, ?, 60, CURRENT_TIMESTAMP)", s.id(), lessonId);
    }

    private void submit(Account s, long bankId, double score, int max) {
        jdbc.update("insert into student_exercise_attempts (user_id, bank_id, submitted_at, score, max_score) values (?, ?, CURRENT_TIMESTAMP, ?, ?)", s.id(), bankId, score, max);
    }

    private JsonNode row(JsonNode board, Account s) {
        for (JsonNode n : board.get("students")) {
            if (n.get("userId").asLong() == s.id()) return n;
        }
        throw new AssertionError("Không thấy học viên " + s.id());
    }

    @Test
    void boardShowsProgressAndFlagsLaggingStudents() throws Exception {
        var f = fixture(true);
        var onTrack = student(f);
        var lagging = student(f);
        var ahead = student(f);
        // onTrack xong unit A (bài giảng + bài tập + kiểm tra chương 3/4); ahead xong A và B; lagging chưa làm gì
        for (var s : List.of(onTrack, ahead)) {
            completeLesson(s, f.lessonA());
            submit(s, f.practiceA(), 1, 1);
            submit(s, f.testA(), 3, 4);
        }
        completeLesson(ahead, f.lessonB());
        // lagging: vắng 3 buổi, một bài giao quá hạn chưa nộp
        long session = insert("class_sessions", Map.of("class_id", f.classId(), "kind", "GROUP", "mode", "ONLINE", "starts_at", java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(7200)),
                "ends_at", java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(3600)), "taught_by", f.teacher().id(), "created_by", f.teacher().id()));
        for (int i = 0; i < 3; i++) {
            long s2 = insert("class_sessions", Map.of("class_id", f.classId(), "kind", "GROUP", "mode", "ONLINE", "starts_at", java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(7200 + i)),
                    "ends_at", java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(3600)), "taught_by", f.teacher().id(), "created_by", f.teacher().id()));
            jdbc.update("insert into attendance_marks (session_id, user_id, status, marked_by, marked_at) values (?, ?, 'ABSENT', ?, CURRENT_TIMESTAMP)", s2, lagging.id(), f.teacher().id());
            jdbc.update("insert into attendance_marks (session_id, user_id, status, marked_by, marked_at) values (?, ?, 'PRESENT', ?, CURRENT_TIMESTAMP)", s2, onTrack.id(), f.teacher().id());
        }
        jdbc.update("insert into attendance_marks (session_id, user_id, status, marked_by, marked_at) values (?, ?, 'EXCUSED', ?, CURRENT_TIMESTAMP)", session, onTrack.id(), f.teacher().id());
        long asg = insert("assignments", Map.of("class_id", f.classId(), "bank_id", f.practiceA(), "due_at", java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(3600)),
                "created_by", f.teacher().id()));
        jdbc.update("insert into student_exercise_attempts (user_id, bank_id, assignment_id, submitted_at, score, max_score) values (?, ?, ?, TIMESTAMPADD(HOUR, -2, CURRENT_TIMESTAMP), 1, 1)",
                onTrack.id(), f.practiceA(), asg);

        String body = call(HttpMethod.GET, "/api/classes/" + f.classId() + "/progress", f.teacher().accessToken(), null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode board = json.readTree(body);
        assertEquals(2, board.get("medianUnitIndex").asInt()); // trung vị: unit B (1-based 2)

        var a = row(board, onTrack);
        assertEquals(33, a.get("lessonPercent").asInt()); // 1/3 bài giảng
        assertEquals(2, a.get("unitIndex").asInt());
        assertEquals(75, a.get("chapterTests").get(0).get("percent").asInt());
        assertEquals(1, a.get("assignments").get("submitted").asInt());
        assertEquals(100, a.get("avgPercent").asInt());
        assertEquals(100, a.get("attendance").get("ratePercent").asInt());
        assertEquals(1, a.get("attendance").get("excused").asInt());
        assertEquals(false, a.get("lagging").asBoolean());

        var l = row(board, lagging);
        assertEquals(1, l.get("unitIndex").asInt());
        assertEquals(1, l.get("assignments").get("overdue").asInt());
        assertEquals(0, l.get("attendance").get("ratePercent").asInt());
        var reasons = new java.util.HashSet<String>();
        l.get("lagReasons").forEach(n -> reasons.add(n.asText()));
        assertEquals(java.util.Set.of("OVERDUE_ASSIGNMENT", "ABSENCES", "BEHIND_UNIT"), reasons);
        assertEquals(3, row(board, ahead).get("unitIndex").asInt());

        // giáo vụ nâng ngưỡng vắng lên 5 thì bỏ cờ ABSENCES
        var mgr = withRole("TRAINING_MANAGER");
        call(HttpMethod.PUT, "/api/admin/classes/courses/" + f.course() + "/lag-thresholds", mgr.accessToken(),
                Map.of("overdueAssignments", 1, "absences", 5, "unitsBehind", 1, "minAvgPercent", 50)).andExpect(status().isOk());
        call(HttpMethod.GET, "/api/classes/" + f.classId() + "/progress", mgr.accessToken(), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.thresholds.absences").value(5))
                .andExpect(jsonPath("$.students[?(@.userId == " + lagging.id() + ")].lagReasons[*]", org.hamcrest.Matchers.not(hasItem("ABSENCES"))));
        call(HttpMethod.PUT, "/api/admin/classes/courses/" + f.course() + "/lag-thresholds", f.teacher().accessToken(),
                Map.of("overdueAssignments", 1, "absences", 5, "unitsBehind", 1, "minAvgPercent", 50)).andExpect(status().isForbidden());
        call(HttpMethod.PUT, "/api/admin/classes/courses/" + f.course() + "/lag-thresholds", mgr.accessToken(),
                Map.of("overdueAssignments", 0, "absences", 5, "unitsBehind", 1, "minAvgPercent", 50)).andExpect(status().isBadRequest());
    }

    @Test
    void onlyStaffOfTheSameClassSeeTheBoard() throws Exception {
        var a = fixture(true);
        var b = fixture(true);
        var s = student(a);
        call(HttpMethod.GET, "/api/classes/" + a.classId() + "/progress", b.teacher().accessToken(), null).andExpect(status().isNotFound());
        call(HttpMethod.GET, "/api/classes/" + a.classId() + "/progress", s.accessToken(), null).andExpect(status().isNotFound());
        call(HttpMethod.GET, "/api/classes/" + a.classId() + "/unlocks", b.teacher().accessToken(), null).andExpect(status().isNotFound());
        call(HttpMethod.POST, "/api/classes/" + a.classId() + "/unlocks", b.teacher().accessToken(), Map.of("topicId", a.topicB())).andExpect(status().isNotFound());
        call(HttpMethod.GET, "/api/classes/" + a.classId() + "/progress", a.teacher().accessToken(), null).andExpect(status().isOk()).andExpect(jsonPath("$.students", hasSize(1)));
    }

    @Test
    void earlyUnlockOpensTheUnitForClassOrStudentAndCanBeRevoked() throws Exception {
        var f = fixture(true);
        var s1 = student(f);
        var s2 = student(f);
        var t = f.teacher().accessToken();
        String url = "/api/classes/" + f.classId() + "/unlocks";
        call(HttpMethod.GET, "/api/lessons/" + f.lessonB(), s1.accessToken(), null).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("unit_locked"));

        // chỉ s1
        String created = call(HttpMethod.POST, url, t, Map.of("topicId", f.topicB(), "studentIds", List.of(s1.id()))).andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].grantedBy").value(f.teacher().id())).andReturn().getResponse().getContentAsString();
        long unlockId = json.readTree(created).get(0).get("id").asLong();
        call(HttpMethod.GET, "/api/lessons/" + f.lessonB(), s1.accessToken(), null).andExpect(status().isOk());
        call(HttpMethod.GET, "/api/lessons/" + f.lessonB(), s2.accessToken(), null).andExpect(status().isForbidden());
        call(HttpMethod.GET, "/api/lessons/" + f.lessonC(), s1.accessToken(), null).andExpect(status().isForbidden()); // unit C vẫn khóa

        call(HttpMethod.POST, url, t, Map.of("topicId", f.topicB(), "studentIds", List.of(s1.id()))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("unlock_duplicate"));

        // cả lớp
        call(HttpMethod.POST, url, t, Map.of("topicId", f.topicB())).andExpect(status().isCreated());
        call(HttpMethod.GET, "/api/lessons/" + f.lessonB(), s2.accessToken(), null).andExpect(status().isOk());
        call(HttpMethod.GET, url, t, null).andExpect(jsonPath("$", hasSize(2)));

        // thu hồi cả hai: khóa lại
        call(HttpMethod.DELETE, url + "/" + unlockId, t, null).andExpect(status().isOk());
        for (JsonNode n : json.readTree(call(HttpMethod.GET, url, t, null).andReturn().getResponse().getContentAsString())) {
            call(HttpMethod.DELETE, url + "/" + n.get("id").asLong(), t, null).andExpect(status().isOk());
        }
        call(HttpMethod.GET, "/api/lessons/" + f.lessonB(), s1.accessToken(), null).andExpect(status().isForbidden());
    }

    @Test
    void earlyUnlockValidation() throws Exception {
        var seq = fixture(true);
        var s = student(seq);
        var other = fixture(true);
        var t = seq.teacher().accessToken();
        String url = "/api/classes/" + seq.classId() + "/unlocks";
        call(HttpMethod.POST, url, t, Map.of("topicId", other.topicB())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.topicId").exists());
        call(HttpMethod.POST, url, t, Map.of("topicId", seq.topicB(), "studentIds", List.of(999999L))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.studentIds").exists());
        call(HttpMethod.POST, url, t, Map.of("topicId", seq.topicB(), "studentIds", List.of(s.id()))).andExpect(status().isCreated());

        var free = fixture(false); // khóa tắt học tuần tự: mở hết, không cần mở sớm
        call(HttpMethod.POST, "/api/classes/" + free.classId() + "/unlocks", free.teacher().accessToken(), Map.of("topicId", free.topicB()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("not_sequential"));
    }

    @Test
    void teachingReportCountsOnlyMarkedSessionsAndTracksEditsSinceLastExport() throws Exception {
        var f = fixture(true);
        var assistant = withRole("ASSISTANT");
        jdbc.update("insert into class_staff (class_id, user_id, role, assigned_from) values (?, ?, 'ASSISTANT', ?)", f.classId(), assistant.id(), LocalDate.now().minusDays(10));
        var s = student(f);
        var now = java.time.Instant.now();
        java.util.function.BiFunction<Long, String, Long> session = (taught, kind) -> {
            var cols = new HashMap<String, Object>(Map.of("class_id", f.classId(), "kind", kind, "mode", "ONLINE", "starts_at", java.sql.Timestamp.from(now.minusSeconds(10800)),
                    "ends_at", java.sql.Timestamp.from(now.minusSeconds("GROUP".equals(kind) ? 5400 : 7200)), "taught_by", taught, "created_by", taught));
            if ("TUTORING".equals(kind)) cols.put("student_id", s.id());
            return insert("class_sessions", cols);
        };
        long group = session.apply(f.teacher().id(), "GROUP"); // 90 phút
        long tutoring = session.apply(assistant.id(), "TUTORING"); // 60 phút
        long unmarked = session.apply(f.teacher().id(), "GROUP");
        long cancelled = session.apply(f.teacher().id(), "GROUP");
        for (long id : new long[] {group, tutoring, cancelled}) {
            jdbc.update("insert into attendance_marks (session_id, user_id, status, marked_by, marked_at) values (?, ?, 'PRESENT', ?, CURRENT_TIMESTAMP)", id, s.id(), f.teacher().id());
        }
        jdbc.update("update class_sessions set status = 'CANCELLED' where id = ?", cancelled);
        String range = "?from=" + LocalDate.now().minusDays(2) + "&to=" + LocalDate.now().plusDays(1);

        var accountant = withRole("ACCOUNTANT");
        String body = call(HttpMethod.GET, "/api/admin/reports/teaching" + range, accountant.accessToken(), null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode teacherRow = null;
        JsonNode assistantRow = null;
        for (JsonNode n : json.readTree(body).get("teachers")) {
            if (n.get("userId").asLong() == f.teacher().id()) teacherRow = n;
            if (n.get("userId").asLong() == assistant.id()) assistantRow = n;
        }
        assertEquals(1, teacherRow.get("groupSessions").asInt()); // không đếm buổi chưa điểm danh, buổi hủy
        assertEquals(1.5, teacherRow.get("groupHours").asDouble());
        assertEquals(0, teacherRow.get("tutoringSessions").asInt());
        assertEquals(1, assistantRow.get("tutoringSessions").asInt());
        assertEquals(1.0, assistantRow.get("tutoringHours").asDouble());
        assertTrue(unmarked > 0);

        // quyền
        call(HttpMethod.GET, "/api/admin/reports/teaching" + range, f.teacher().accessToken(), null).andExpect(status().isForbidden());
        call(HttpMethod.GET, "/api/admin/reports/teaching" + range, s.accessToken(), null).andExpect(status().isForbidden());
        call(HttpMethod.GET, "/api/admin/reports/teaching?from=2026-02-01&to=2026-01-01", accountant.accessToken(), null).andExpect(status().isBadRequest());
        call(HttpMethod.GET, "/api/admin/reports/teaching", accountant.accessToken(), null).andExpect(status().isBadRequest());

        // sửa điểm danh: hiện trong báo cáo cho tới khi được xuất
        jdbc.update("insert into attendance_changes (session_id, user_id, old_status, new_status, changed_by, changed_at) values (?, ?, 'ABSENT', 'PRESENT', ?, TIMESTAMPADD(MINUTE, -1, CURRENT_TIMESTAMP))",
                group, s.id(), f.teacher().id());
        call(HttpMethod.GET, "/api/admin/reports/teaching" + range, accountant.accessToken(), null)
                .andExpect(jsonPath("$.editedSessions[*].sessionId", hasItem((int) group)));
        call(HttpMethod.POST, "/api/admin/reports/teaching/export" + range, accountant.accessToken(), null).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv")).andExpect(content().string(containsString("Số buổi nhóm")))
                .andExpect(content().string(containsString(String.valueOf(group))));
        call(HttpMethod.GET, "/api/admin/reports/teaching" + range, accountant.accessToken(), null)
                .andExpect(jsonPath("$.editedSessions").isEmpty()).andExpect(jsonPath("$.lastExportAt").isNotEmpty());
        // sửa thêm sau lần xuất: hiện lại
        jdbc.update("insert into attendance_changes (session_id, user_id, old_status, new_status, changed_by, changed_at) values (?, ?, 'PRESENT', 'LATE', ?, TIMESTAMPADD(SECOND, 5, CURRENT_TIMESTAMP))",
                group, s.id(), f.teacher().id());
        call(HttpMethod.GET, "/api/admin/reports/teaching" + range, accountant.accessToken(), null)
                .andExpect(jsonPath("$.editedSessions", hasSize(1))).andExpect(jsonPath("$.editedSessions[0].sessionId").value((int) group))
                .andExpect(jsonPath("$.editedSessions[0].edits").value(1));
    }
}
