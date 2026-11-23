package com.pps.smartenglish.classroom;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.classroom.service.SessionReminderService;
import com.pps.smartenglish.support.ApiTestBase;
import java.time.Instant;
import com.pps.smartenglish.classroom.service.ClassService;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** FR-SES-01..04, NFR-SEC-05: buổi học nhóm, kèm 1-1, đổi/hủy, nhắc lịch và điểm danh. */
class SessionApiTest extends ApiTestBase {

    private static final String PASSWORD = "mat-khau-1";
    private static final String URL = "https://meet.google.com/abc-defg-hij";

    @Autowired SessionReminderService reminders;

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

    /** Lớp chạy từ 30 ngày trước đến 30 ngày sau, để buổi quá khứ và tương lai đều hợp lệ. */
    private long newClass(long courseId) {
        return insert("classes", Map.of("course_id", courseId, "name", "Lớp " + UUID.randomUUID().toString().substring(0, 4),
                "starts_on", LocalDate.now(ClassService.VN).minusDays(30), "ends_on", LocalDate.now(ClassService.VN).plusDays(30), "mode", "ONLINE", "capacity", 20));
    }

    private long course() {
        return insert("courses", Map.of("slug", "ses-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED", "delivery_mode", "CLASS_BASED"));
    }

    private void staffOf(long classId, Account a, String role) {
        jdbc.update("insert into class_staff (class_id, user_id, role, assigned_from) values (?, ?, ?, ?)", classId, a.id(), role,
                LocalDate.now(ClassService.VN).minusDays(30));
    }

    private Account memberOf(long classId, long courseId) throws Exception {
        var s = register(newEmail(), PASSWORD);
        jdbc.update("insert into class_members (class_id, course_id, user_id, status, live_key, joined_at) values (?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP)",
                classId, courseId, s.id(), courseId + ":" + s.id());
        return s;
    }

    private Map<String, Object> group(Instant start, String mode, String link) {
        var m = new LinkedHashMap<String, Object>();
        m.put("startsAt", start.toString());
        m.put("endsAt", start.plus(90, ChronoUnit.MINUTES).toString());
        m.put("mode", mode);
        if (link != null) m.put("meetingUrl", link);
        return m;
    }

    private long createSession(Account teacher, long classId, Instant start) throws Exception {
        String body = call(HttpMethod.POST, "/api/classes/" + classId + "/sessions", teacher.accessToken(), group(start, "ONLINE", URL))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get(0).get("id").asLong();
    }

    private Instant ago(int hours) {
        return Instant.now().minus(hours, ChronoUnit.HOURS);
    }

    private Instant in(int minutes) {
        return Instant.now().plus(minutes, ChronoUnit.MINUTES);
    }

    @Test
    void groupSessionRulesAndWeeklyRepeat() throws Exception {
        long course = course();
        long cls = newClass(course);
        var teacher = withRole("TEACHER");
        staffOf(cls, teacher, "TEACHER");
        String url = "/api/classes/" + cls + "/sessions";

        var repeat = group(in(24 * 60), "ONLINE", URL);
        repeat.put("repeatUntil", LocalDate.now(ClassService.VN).plusDays(1 + 14).toString());
        call(HttpMethod.POST, url, teacher.accessToken(), repeat).andExpect(status().isCreated()).andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].kind").value("GROUP")).andExpect(jsonPath("$[0].meetingUrl").value(URL));

        call(HttpMethod.POST, url, teacher.accessToken(), group(in(60), "OFFLINE", URL)).andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].meetingUrl").value(nullValue()));
        call(HttpMethod.POST, url, teacher.accessToken(), group(in(60), "ONLINE", null))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.meetingUrl").exists());
        call(HttpMethod.POST, url, teacher.accessToken(), group(in(60), "ONLINE", "javascript:alert(1)"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.meetingUrl").exists());
        var reversed = group(in(60), "ONLINE", URL);
        reversed.put("endsAt", in(30).toString());
        call(HttpMethod.POST, url, teacher.accessToken(), reversed).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.endsAt").exists());
        call(HttpMethod.POST, url, teacher.accessToken(), group(in(60 * 24 * 90), "ONLINE", URL))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.startsAt").exists());
        var tooLong = group(in(60), "ONLINE", URL);
        tooLong.put("repeatUntil", LocalDate.now(ClassService.VN).plusDays(90).toString());
        call(HttpMethod.POST, url, teacher.accessToken(), tooLong).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.repeatUntil").exists());
        var stranger = group(in(60), "ONLINE", URL);
        stranger.put("taughtBy", withRole("TEACHER").id());
        call(HttpMethod.POST, url, teacher.accessToken(), stranger).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.taughtBy").exists());
    }

    @Test
    void otherClassStaffAndStudentsCannotTouchTheSessions() throws Exception {
        long course = course();
        long a = newClass(course);
        long b = newClass(course);
        var ta = withRole("TEACHER");
        var tb = withRole("TEACHER");
        staffOf(a, ta, "TEACHER");
        staffOf(b, tb, "TEACHER");
        var student = memberOf(a, course);
        long sid = createSession(ta, a, ago(2));
        String base = "/api/classes/" + a + "/sessions";

        call(HttpMethod.GET, base, tb.accessToken(), null).andExpect(status().isNotFound());
        call(HttpMethod.POST, base, tb.accessToken(), group(in(60), "ONLINE", URL)).andExpect(status().isNotFound());
        call(HttpMethod.PATCH, base + "/" + sid, tb.accessToken(), Map.of("mode", "OFFLINE")).andExpect(status().isNotFound());
        call(HttpMethod.POST, base + "/" + sid + "/cancel", tb.accessToken(), null).andExpect(status().isNotFound());
        call(HttpMethod.PUT, base + "/" + sid + "/attendance", tb.accessToken(), Map.of("marks", List.of(Map.of("userId", student.id(), "status", "PRESENT"))))
                .andExpect(status().isNotFound());
        call(HttpMethod.GET, base + "/" + sid + "/attendance", tb.accessToken(), null).andExpect(status().isNotFound());
        // buổi của lớp A không truy cập được qua đường dẫn lớp B
        call(HttpMethod.GET, "/api/classes/" + b + "/sessions/" + sid + "/attendance", tb.accessToken(), null).andExpect(status().isNotFound());

        // học viên không tự điểm danh, không tạo/sửa buổi, nhưng xem được lịch lớp mình
        call(HttpMethod.PUT, base + "/" + sid + "/attendance", student.accessToken(), Map.of("marks", List.of(Map.of("userId", student.id(), "status", "PRESENT"))))
                .andExpect(status().isNotFound());
        call(HttpMethod.POST, base, student.accessToken(), group(in(60), "ONLINE", URL)).andExpect(status().isNotFound());
        call(HttpMethod.GET, base, student.accessToken(), null).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));

        // cấu hình và buổi kèm chỉ giáo vụ
        call(HttpMethod.GET, "/api/admin/classes/settings", ta.accessToken(), null).andExpect(status().isForbidden());
        call(HttpMethod.POST, "/api/admin/classes/" + a + "/tutoring", ta.accessToken(), Map.of()).andExpect(status().isForbidden());
    }

    @Test
    void tutoringSessionIsPrivateToItsStudent() throws Exception {
        long course = course();
        long cls = newClass(course);
        var mgr = withRole("TRAINING_MANAGER");
        var assistant = withRole("ASSISTANT");
        staffOf(cls, assistant, "ASSISTANT");
        var s1 = memberOf(cls, course);
        var s2 = memberOf(cls, course);
        var outsider = register(newEmail(), PASSWORD);

        var body = new LinkedHashMap<String, Object>(group(in(120), "ONLINE", URL));
        body.put("studentId", s1.id());
        body.put("assistantId", assistant.id());
        String url = "/api/admin/classes/" + cls + "/tutoring";
        call(HttpMethod.POST, url, mgr.accessToken(), body).andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("TUTORING")).andExpect(jsonPath("$.taughtBy").value(assistant.id()));

        body.put("studentId", outsider.id());
        call(HttpMethod.POST, url, mgr.accessToken(), body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.studentId").exists());
        body.put("studentId", s1.id());
        body.put("assistantId", mgr.id());
        call(HttpMethod.POST, url, mgr.accessToken(), body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.assistantId").exists());

        String list = "/api/classes/" + cls + "/sessions";
        call(HttpMethod.GET, list, s1.accessToken(), null).andExpect(jsonPath("$", hasSize(1)));
        call(HttpMethod.GET, list, s2.accessToken(), null).andExpect(jsonPath("$", hasSize(0)));
        call(HttpMethod.GET, list, assistant.accessToken(), null).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void attendanceRulesHistoryAndLocks() throws Exception {
        long course = course();
        long cls = newClass(course);
        var teacher = withRole("TEACHER");
        staffOf(cls, teacher, "TEACHER");
        var s1 = memberOf(cls, course);
        var s2 = memberOf(cls, course);
        var outsider = register(newEmail(), PASSWORD);
        long past = createSession(teacher, cls, ago(3));
        long future = createSession(teacher, cls, in(300));
        String base = "/api/classes/" + cls + "/sessions/";

        call(HttpMethod.PUT, base + future + "/attendance", teacher.accessToken(), Map.of("marks", List.of(Map.of("userId", s1.id(), "status", "PRESENT"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("session_not_started"));
        call(HttpMethod.PUT, base + past + "/attendance", teacher.accessToken(), Map.of("marks", List.of(Map.of("userId", outsider.id(), "status", "PRESENT"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.marks").exists());
        call(HttpMethod.PUT, base + past + "/attendance", teacher.accessToken(), Map.of("marks", List.of(Map.of("userId", s1.id(), "status", "NOPE"))))
                .andExpect(status().isBadRequest());

        call(HttpMethod.GET, base + past + "/attendance", teacher.accessToken(), null).andExpect(jsonPath("$[0].status").value(nullValue()));
        call(HttpMethod.PUT, base + past + "/attendance", teacher.accessToken(),
                Map.of("marks", List.of(Map.of("userId", s1.id(), "status", "PRESENT"), Map.of("userId", s2.id(), "status", "ABSENT"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
        call(HttpMethod.GET, base + past + "/attendance/history", teacher.accessToken(), null).andExpect(jsonPath("$", hasSize(0)));

        // sửa: ghi giá trị cũ, người sửa; nhập lại cùng giá trị không ghi thêm
        call(HttpMethod.PUT, base + past + "/attendance", teacher.accessToken(),
                Map.of("marks", List.of(Map.of("userId", s2.id(), "status", "EXCUSED"), Map.of("userId", s1.id(), "status", "PRESENT")))).andExpect(status().isOk());
        call(HttpMethod.GET, base + past + "/attendance/history", teacher.accessToken(), null).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].oldStatus").value("ABSENT")).andExpect(jsonPath("$[0].newStatus").value("EXCUSED"))
                .andExpect(jsonPath("$[0].changedBy").value(teacher.id()));

        // học viên thấy điểm danh của chính mình; buổi đã điểm danh khóa đổi và hủy
        call(HttpMethod.GET, "/api/classes/" + cls + "/sessions", s2.accessToken(), null).andExpect(jsonPath("$[0].myStatus").value("EXCUSED"));
        call(HttpMethod.PATCH, base + past, teacher.accessToken(), Map.of("mode", "OFFLINE"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("session_attended"));
        call(HttpMethod.POST, base + past + "/cancel", teacher.accessToken(), null).andExpect(status().isConflict());
    }

    @Test
    void changeAndCancelNotifyStudentsAndLockCancelledSession() throws Exception {
        long course = course();
        long cls = newClass(course);
        var teacher = withRole("TEACHER");
        staffOf(cls, teacher, "TEACHER");
        var s = memberOf(cls, course);
        long sid = createSession(teacher, cls, in(600));
        String url = "/api/classes/" + cls + "/sessions/" + sid;

        call(HttpMethod.PATCH, url, teacher.accessToken(), Map.of("startsAt", in(700).toString(), "endsAt", in(790).toString())).andExpect(status().isOk());
        assertEquals(1, count("select count(*) from notifications where user_id = ? and type = 'SESSION_CHANGED'", s.id()));
        call(HttpMethod.PATCH, url, teacher.accessToken(), Map.of("mode", "OFFLINE")).andExpect(status().isOk())
                .andExpect(jsonPath("$.meetingUrl").value(nullValue()));
        call(HttpMethod.PATCH, url, teacher.accessToken(), Map.of("mode", "ONLINE"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.meetingUrl").exists());

        call(HttpMethod.POST, url + "/cancel", teacher.accessToken(), null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertEquals(3, count("select count(*) from notifications where user_id = ? and type = 'SESSION_CHANGED'", s.id()));
        call(HttpMethod.PATCH, url, teacher.accessToken(), Map.of("mode", "OFFLINE")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("session_cancelled"));
        call(HttpMethod.PUT, url + "/attendance", teacher.accessToken(), Map.of("marks", List.of(Map.of("userId", s.id(), "status", "PRESENT"))))
                .andExpect(status().isConflict());
    }

    @Test
    void reminderGoesOutOnceAndAgainAfterReschedule() throws Exception {
        var admin = withRole("TRAINING_MANAGER");
        call(HttpMethod.PUT, "/api/admin/classes/settings", admin.accessToken(), Map.of("reminderMinutes", 2)).andExpect(status().isBadRequest());
        call(HttpMethod.PUT, "/api/admin/classes/settings", admin.accessToken(), Map.of("reminderMinutes", 5000)).andExpect(status().isOk());
        call(HttpMethod.GET, "/api/admin/classes/settings", admin.accessToken(), null).andExpect(jsonPath("$.reminderMinutes").value(5000));

        long course = course();
        long cls = newClass(course);
        var teacher = withRole("TEACHER");
        staffOf(cls, teacher, "TEACHER");
        var s = memberOf(cls, course);
        long soon = createSession(teacher, cls, in(60));
        reminders.sendDue(); // có thể gồm buổi của test khác; chỉ kiểm phần của học viên này
        assertEquals(1, count("select count(*) from notifications where user_id = ? and type = 'SESSION_REMINDER'", s.id()));
        reminders.sendDue();
        assertEquals(1, count("select count(*) from notifications where user_id = ? and type = 'SESSION_REMINDER'", s.id()));

        call(HttpMethod.PATCH, "/api/classes/" + cls + "/sessions/" + soon, teacher.accessToken(),
                Map.of("startsAt", in(120).toString(), "endsAt", in(210).toString())).andExpect(status().isOk());
        reminders.sendDue();
        assertEquals(2, count("select count(*) from notifications where user_id = ? and type = 'SESSION_REMINDER'", s.id()));
        call(HttpMethod.PUT, "/api/admin/classes/settings", admin.accessToken(), Map.of("reminderMinutes", 60)).andExpect(status().isOk());
    }
}
