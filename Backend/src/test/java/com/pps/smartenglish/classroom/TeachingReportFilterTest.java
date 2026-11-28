package com.pps.smartenglish.classroom;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.support.ApiTestBase;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** FR-CLS-05, FR-CLS-07, D-176: lọc báo cáo số buổi dạy theo vai trò và lớp, giới hạn 366 ngày, danh sách lớp của giáo viên. */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class TeachingReportFilterTest extends ApiTestBase {

    private static final String PASSWORD = "mat-khau-1";

    // Ngày cố định riêng cho từng test có xuất báo cáo: bảng teaching_report_exports dùng chung giữa các test, mốc xuất của test khác không được phủ tới.
    private static final LocalDate DAY_FILTER = LocalDate.of(2021, 5, 10);
    private static final LocalDate DAY_MARK = LocalDate.of(2021, 6, 10);
    private static final LocalDate DAY_ROLE = LocalDate.of(2021, 7, 10);

    @Autowired EntityManagerFactory emf;

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private Account withRole(String role) throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = ?", u.id(), role);
        return login(u.email(), PASSWORD);
    }

    private ResultActions get(String url, String token) throws Exception {
        return send(MockMvcRequestBuilders.request(HttpMethod.GET, url), token);
    }

    private long course(int lessons) {
        long c = insert("courses", Map.of("slug", "trf-" + UUID.randomUUID(), "title", "Khóa lọc", "status", "PUBLISHED", "delivery_mode", "CLASS_BASED", "sequential", false));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED", "sort_order", 0));
        long t = insert("topics", Map.of("level_id", lv, "title", "Unit", "is_free_trial", false, "status", "PUBLISHED", "sort_order", 0));
        for (int i = 0; i < lessons; i++) {
            var l = new HashMap<String, Object>(Map.of("topic_id", t, "type", "CONNECTIVE_VIDEO", "title", "Bài " + i, "status", "PUBLISHED"));
            l.put("video_source", "YOUTUBE");
            l.put("video_ref", "abc");
            l.put("duration_sec", 60);
            insert("lessons", l);
        }
        return c;
    }

    private long clazz(long course, String name) {
        return insert("classes", Map.of("course_id", course, "name", name, "starts_on", LocalDate.now().minusDays(10),
                "ends_on", LocalDate.now().plusDays(60), "mode", "ONLINE", "capacity", 50));
    }

    private void staff(long cls, Account u, String role) {
        jdbc.update("insert into class_staff (class_id, user_id, role, assigned_from) values (?, ?, ?, ?)", cls, u.id(), role, LocalDate.now().minusDays(10));
    }

    private Account member(long cls, long course) throws Exception {
        var s = register(newEmail(), PASSWORD);
        jdbc.update("insert into class_members (class_id, course_id, user_id, status, live_key, joined_at) values (?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP)",
                cls, course, s.id(), course + ":" + s.id());
        return s;
    }

    /** Buổi nhóm đã qua, đã điểm danh một học viên. */
    private long markedSession(long cls, Account teacher, Account student, String status) {
        return markedSession(cls, teacher, student, status, Instant.now().minusSeconds(7200));
    }

    /** Buổi bắt đầu lúc start, dài 1 giờ. Test có xuất báo cáo dùng ngày cố định riêng (xem day) để các lần xuất của test khác không phủ tới. */
    private long markedSession(long cls, Account teacher, Account student, String status, Instant start) {
        long id = insert("class_sessions", Map.of("class_id", cls, "kind", "GROUP", "mode", "ONLINE", "starts_at", Timestamp.from(start),
                "ends_at", Timestamp.from(start.plusSeconds(3600)), "taught_by", teacher.id(), "created_by", teacher.id()));
        jdbc.update("insert into attendance_marks (session_id, user_id, status, marked_by, marked_at) values (?, ?, ?, ?, CURRENT_TIMESTAMP)", id, student.id(), status, teacher.id());
        return id;
    }

    /** 10:00 giờ Việt Nam của ngày day. */
    private static Instant at(LocalDate day) {
        return day.atTime(10, 0).atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
    }

    private static String around(LocalDate day) {
        return "?from=" + day.minusDays(1) + "&to=" + day.plusDays(1);
    }

    private JsonNode rows(String url, String token) throws Exception {
        return json.readTree(get(url, token).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("teachers");
    }

    private static List<String> keys(JsonNode teachers, long... users) {
        List<String> out = new ArrayList<>();
        for (JsonNode n : teachers) {
            for (long u : users) {
                if (n.get("userId").asLong() == u) out.add(u + ":" + n.get("role").asText() + ":" + n.get("totalSessions").asInt());
            }
        }
        return out;
    }

    @Test
    void reportFiltersByRoleAndClassAndExportsMatchingCsv() throws Exception {
        long course = course(1);
        String nameA = "Lớp A " + UUID.randomUUID().toString().substring(0, 4);
        String nameB = "Lớp B " + UUID.randomUUID().toString().substring(0, 4);
        long a = clazz(course, nameA);
        long b = clazz(course, nameB);
        var t = withRole("TEACHER"); // giáo viên chính lớp A, trợ giảng lớp B
        var u = withRole("TEACHER"); // giáo viên chính lớp B
        staff(a, t, "TEACHER");
        staff(b, t, "ASSISTANT");
        staff(b, u, "TEACHER");
        var sa = member(a, course);
        var sb = member(b, course);
        markedSession(a, t, sa, "PRESENT", at(DAY_FILTER));
        markedSession(b, t, sb, "PRESENT", at(DAY_FILTER));
        markedSession(b, u, sb, "PRESENT", at(DAY_FILTER));
        var acc = withRole("ACCOUNTANT");
        String range = around(DAY_FILTER);

        // một người ở hai vai thì hai dòng
        assertEquals(List.of("%d:TEACHER:1".formatted(t.id()), "%d:ASSISTANT:1".formatted(t.id()), "%d:TEACHER:1".formatted(u.id())).stream().sorted().toList(),
                keys(rows("/api/admin/reports/teaching" + range, acc.accessToken()), t.id(), u.id()).stream().sorted().toList());
        assertEquals(List.of("%d:ASSISTANT:1".formatted(t.id())), keys(rows("/api/admin/reports/teaching" + range + "&role=ASSISTANT&classId=" + b, acc.accessToken()), t.id(), u.id()));
        JsonNode onlyA = rows("/api/admin/reports/teaching" + range + "&classId=" + a, acc.accessToken());
        assertEquals(1, onlyA.size());
        assertEquals("TEACHER", onlyA.get(0).get("role").asText());
        assertEquals(0, rows("/api/admin/reports/teaching" + range + "&role=ASSISTANT&classId=" + a, acc.accessToken()).size());
        JsonNode teachersB = rows("/api/admin/reports/teaching" + range + "&role=TEACHER&classId=" + b, acc.accessToken());
        assertEquals(1, teachersB.size());
        assertEquals(u.id(), teachersB.get(0).get("userId").asLong());

        // sửa điểm danh buổi lớp A: chỉ hiện khi lọc phủ lớp A
        long sessA = jdbc.queryForObject("select id from class_sessions where class_id = ? and taught_by = ?", Long.class, a, t.id());
        jdbc.update("insert into attendance_changes (session_id, user_id, old_status, new_status, changed_by, changed_at) values (?, ?, 'ABSENT', 'PRESENT', ?, CURRENT_TIMESTAMP)", sessA, sa.id(), t.id());
        get("/api/admin/reports/teaching" + range + "&classId=" + b, acc.accessToken()).andExpect(jsonPath("$.editedSessions[*].sessionId", not(hasItem((int) sessA))));
        get("/api/admin/reports/teaching" + range + "&role=ASSISTANT", acc.accessToken()).andExpect(jsonPath("$.editedSessions[*].sessionId", not(hasItem((int) sessA))));
        get("/api/admin/reports/teaching" + range + "&role=TEACHER&classId=" + a, acc.accessToken()).andExpect(jsonPath("$.editedSessions[*].sessionId", hasItem((int) sessA)));

        // lỗi tham số
        get("/api/admin/reports/teaching" + range + "&role=ADMIN", acc.accessToken()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.role").exists());
        get("/api/admin/reports/teaching" + range + "&classId=999999", acc.accessToken()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.classId").exists());

        // CSV theo bộ lọc, có cột vai trò
        send(MockMvcRequestBuilders.request(HttpMethod.POST, "/api/admin/reports/teaching/export" + range + "&role=ASSISTANT&classId=" + b), acc.accessToken())
                .andExpect(status().isOk()).andExpect(content().string(containsString("Vai trò: Trợ giảng")))
                .andExpect(content().string(containsString("Lớp: " + nameB))).andExpect(content().string(containsString("Mã người dạy,Tên,Vai trò")))
                .andExpect(content().string(containsString(t.id() + ",")))
                .andExpect(content().string(containsString(",Trợ giảng,1,")))
                .andExpect(content().string(not(containsString(",Giáo viên,1,"))));
    }

    private void change(long session, Account student, Account by, Instant at) {
        jdbc.update("insert into attendance_changes (session_id, user_id, old_status, new_status, changed_by, changed_at) values (?, ?, 'ABSENT', 'PRESENT', ?, ?)",
                session, student.id(), by.id(), Timestamp.from(at));
    }

    private void export(String query, Account acc) throws Exception {
        send(MockMvcRequestBuilders.request(HttpMethod.POST, "/api/admin/reports/teaching/export" + query), acc.accessToken()).andExpect(status().isOk());
    }

    @Test
    void exportMarkIsPerFilter() throws Exception {
        long course = course(1);
        long a = clazz(course, "Lớp mốc A " + UUID.randomUUID());
        long b = clazz(course, "Lớp mốc B " + UUID.randomUUID());
        var t = withRole("TEACHER");
        var as = withRole("TEACHER");
        staff(a, t, "TEACHER");
        staff(b, t, "TEACHER");
        staff(b, as, "ASSISTANT");
        var sa = member(a, course);
        var sb = member(b, course);
        long teacherA = markedSession(a, t, sa, "PRESENT", at(DAY_MARK));
        long teacherB = markedSession(b, t, sb, "PRESENT", at(DAY_MARK));
        long assistantB = markedSession(b, as, sb, "PRESENT", at(DAY_MARK));
        var acc = withRole("ACCOUNTANT");
        String range = around(DAY_MARK);
        Instant before = Instant.now().minusSeconds(60);
        for (long s : new long[] {teacherA, teacherB, assistantB}) change(s, sb, t, before);

        // xuất toàn bộ rồi sửa lớp B: lớp B hiện lại, lớp A (không sửa thêm) thì không
        export(range, acc);
        change(teacherB, sb, t, Instant.now().plusSeconds(30));
        get("/api/admin/reports/teaching" + range, acc.accessToken()).andExpect(jsonPath("$.editedSessions[*].sessionId", hasItem((int) teacherB)))
                .andExpect(jsonPath("$.editedSessions[*].sessionId", not(hasItem((int) teacherA)))).andExpect(jsonPath("$.lastExportAt").isNotEmpty());
        // báo cáo lớp B không thấy mốc của lần xuất lớp A nhưng thấy mốc của lần xuất toàn bộ
        get("/api/admin/reports/teaching" + range + "&classId=" + a, acc.accessToken()).andExpect(jsonPath("$.lastExportAt").isNotEmpty());
    }

    @Test
    void exportingOneClassAndRoleDoesNotClearMarkOfOtherRole() throws Exception {
        long course = course(1);
        long a = clazz(course, "Lớp mốc vai A " + UUID.randomUUID());
        long b = clazz(course, "Lớp mốc vai B " + UUID.randomUUID());
        var t = withRole("TEACHER");
        var as = withRole("TEACHER");
        staff(a, t, "TEACHER");
        staff(b, as, "ASSISTANT");
        var sa = member(a, course);
        var sb = member(b, course);
        long teacherA = markedSession(a, t, sa, "PRESENT", at(DAY_ROLE));
        long assistantB = markedSession(b, as, sb, "PRESENT", at(DAY_ROLE));
        var acc = withRole("ACCOUNTANT");
        String range = around(DAY_ROLE);
        change(teacherA, sa, t, Instant.now().minusSeconds(60));
        change(assistantB, sb, as, Instant.now().minusSeconds(60));

        export(range + "&classId=" + a + "&role=TEACHER", acc);

        get("/api/admin/reports/teaching" + range, acc.accessToken()).andExpect(jsonPath("$.editedSessions[*].sessionId", hasItem((int) assistantB)))
                .andExpect(jsonPath("$.editedSessions[*].sessionId", not(hasItem((int) teacherA))));
        get("/api/admin/reports/teaching" + range + "&classId=" + a + "&role=TEACHER", acc.accessToken()).andExpect(jsonPath("$.lastExportAt").isNotEmpty());
        get("/api/admin/reports/teaching" + range, acc.accessToken()).andExpect(jsonPath("$.lastExportAt").isEmpty()); // bộ lọc rộng hơn lần xuất
    }

    @Test
    void reportRangeIsAtMost366DaysInclusiveAndClassesListIsPermissioned() throws Exception {
        var acc = withRole("ACCOUNTANT");
        LocalDate today = LocalDate.now();
        get("/api/admin/reports/teaching?from=" + today.minusDays(365) + "&to=" + today, acc.accessToken()).andExpect(status().isOk()); // 366 ngày
        get("/api/admin/reports/teaching?from=" + today.minusDays(366) + "&to=" + today, acc.accessToken()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.to").exists()); // 367 ngày

        long course = course(1);
        long cls = clazz(course, "Lớp chọn " + UUID.randomUUID());
        jdbc.update("update classes set status = 'CLOSED' where id = ?", cls);
        get("/api/admin/reports/teaching/classes", acc.accessToken()).andExpect(status().isOk()).andExpect(jsonPath("$[*].id", hasItem((int) cls)))
                .andExpect(jsonPath("$[?(@.id == " + cls + ")].status").value(hasItem("CLOSED"))).andExpect(jsonPath("$[0].courseTitle").exists());
        get("/api/admin/reports/teaching/classes", withRole("TEACHER").accessToken()).andExpect(status().isForbidden());
    }

    @Test
    void teachingListShowsOwnClassesWithAverageAndLaggingMatchingBoard() throws Exception {
        long course = course(2);
        long a = clazz(course, "Lớp rỗng " + UUID.randomUUID());
        long b = clazz(course, "Lớp có học viên " + UUID.randomUUID());
        long noLessons = course(0);
        long c = clazz(noLessons, "Lớp khóa trống " + UUID.randomUUID());
        var t = withRole("TEACHER");
        var other = withRole("TEACHER");
        staff(a, t, "TEACHER");
        staff(b, t, "TEACHER");
        staff(c, t, "ASSISTANT");
        staff(b, other, "ASSISTANT");
        var s1 = member(b, course);
        var s2 = member(b, course);
        member(c, noLessons);
        long firstLesson = jdbc.queryForObject("select min(l.id) from lessons l join topics tp on tp.id = l.topic_id join course_levels lv on lv.id = tp.level_id where lv.course_id = ?", Long.class, course);
        jdbc.update("insert into lesson_progress (user_id, lesson_id, watched_sec, completed_at) values (?, ?, 60, CURRENT_TIMESTAMP)", s1.id(), firstLesson); // 50%
        for (int i = 0; i < 3; i++) markedSession(b, t, s2, "ABSENT"); // s2 tụt lại vì vắng

        JsonNode list = json.readTree(get("/api/teaching/classes", t.accessToken()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Map<Long, JsonNode> byId = new HashMap<>();
        list.forEach(n -> byId.put(n.get("id").asLong(), n));
        assertEquals(3, byId.size());
        assertNull(byId.get(a).get("avgLessonPercent").numberValue()); // lớp chưa có học viên
        assertEquals(0, byId.get(a).get("laggingCount").asInt());
        assertNull(byId.get(c).get("avgLessonPercent").numberValue()); // khóa chưa có bài giảng xuất bản

        JsonNode board = json.readTree(get("/api/classes/" + b + "/progress", t.accessToken()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        double sum = 0;
        int lagging = 0;
        for (JsonNode s : board.get("students")) {
            sum += s.get("lessonPercent").asInt();
            if (s.get("lagging").asBoolean()) lagging++;
        }
        assertEquals(Math.round(sum / board.get("students").size()), byId.get(b).get("avgLessonPercent").asInt());
        assertEquals(25, byId.get(b).get("avgLessonPercent").asInt());
        assertEquals(lagging, byId.get(b).get("laggingCount").asInt());
        assertEquals(1, lagging);

        // giáo viên khác chỉ thấy lớp mình (b), không thấy a, c
        JsonNode theirs = json.readTree(get("/api/teaching/classes", other.accessToken()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(1, theirs.size());
        assertEquals(b, theirs.get(0).get("id").asLong());
    }

    @Test
    void teachingListQueryCountDoesNotGrowWithStudents() throws Exception {
        long few = statementsFor(2);
        long many = statementsFor(12);
        assertEquals(few, many, "số truy vấn tăng theo số học viên: " + few + " -> " + many);
    }

    private long statementsFor(int students) throws Exception {
        long course = course(1);
        long cls = clazz(course, "Lớp đếm " + UUID.randomUUID());
        var t = withRole("TEACHER");
        staff(cls, t, "TEACHER");
        for (int i = 0; i < students; i++) member(cls, course);
        get("/api/teaching/classes", t.accessToken()).andExpect(status().isOk()); // khởi động
        var stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        get("/api/teaching/classes", t.accessToken()).andExpect(status().isOk());
        return stats.getPrepareStatementCount();
    }
}
