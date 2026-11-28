package com.pps.smartenglish.classroom;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.support.ApiTestBase;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Tester D-176 (FR-CLS-07, FR-CLS-05): vai trò theo phân công lúc dạy, quyền, biên tham số, CSV, danh sách lớp. */
class D176TeachingReportTesterTest extends ApiTestBase {

    private static final String PASSWORD = "mat-khau-1";
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String REPORT = "/api/admin/reports/teaching";

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private Account withRole(String... roles) throws Exception {
        var u = register(newEmail(), PASSWORD);
        for (String r : roles) {
            jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = ?", u.id(), r);
        }
        return login(u.email(), PASSWORD);
    }

    private ResultActions get(String url, String token) throws Exception {
        return send(MockMvcRequestBuilders.request(HttpMethod.GET, url), token);
    }

    private ResultActions export(String query, String token) throws Exception {
        return send(MockMvcRequestBuilders.request(HttpMethod.POST, REPORT + "/export" + query), token);
    }

    private long course() {
        long c = insert("courses", Map.of("slug", "d176-" + UUID.randomUUID(), "title", "Khóa D176", "status", "PUBLISHED", "delivery_mode", "CLASS_BASED", "sequential", false));
        insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED", "sort_order", 0));
        return c;
    }

    private long clazz(long course, String name) {
        return insert("classes", Map.of("course_id", course, "name", name, "starts_on", LocalDate.now(VN).minusDays(30),
                "ends_on", LocalDate.now(VN).plusDays(60), "mode", "ONLINE", "capacity", 50));
    }

    private long staff(long cls, Account u, String role, LocalDate from, LocalDate to, Instant revokedAt) {
        var m = new java.util.HashMap<String, Object>(Map.of("class_id", cls, "user_id", u.id(), "role", role, "assigned_from", from));
        if (to != null) m.put("assigned_to", to);
        if (revokedAt != null) m.put("revoked_at", Timestamp.from(revokedAt));
        return insert("class_staff", m);
    }

    private Account member(long cls, long course) throws Exception {
        var s = register(newEmail(), PASSWORD);
        jdbc.update("insert into class_members (class_id, course_id, user_id, status, live_key, joined_at) values (?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP)",
                cls, course, s.id(), course + ":" + s.id());
        return s;
    }

    /** Buổi nhóm 60 phút bắt đầu lúc 'at' (giờ VN), đã điểm danh. */
    private long session(long cls, Account teacher, Account student, Instant at) {
        long id = insert("class_sessions", Map.of("class_id", cls, "kind", "GROUP", "mode", "ONLINE", "starts_at", Timestamp.from(at),
                "ends_at", Timestamp.from(at.plusSeconds(3600)), "taught_by", teacher.id(), "created_by", teacher.id()));
        jdbc.update("insert into attendance_marks (session_id, user_id, status, marked_by, marked_at) values (?, ?, 'PRESENT', ?, CURRENT_TIMESTAMP)", id, student.id(), teacher.id());
        return id;
    }

    private static Instant vn(LocalDate d, int hour) {
        return d.atTime(LocalTime.of(hour, 0)).atZone(VN).toInstant();
    }

    private JsonNode report(String query, String token) throws Exception {
        return json.readTree(get(REPORT + query, token).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static List<String> rowsOf(JsonNode report, long user) {
        List<String> out = new ArrayList<>();
        report.get("teachers").forEach(n -> {
            if (n.get("userId").asLong() == user) out.add(n.get("role").asText() + ":" + n.get("totalSessions").asInt());
        });
        return out.stream().sorted().toList();
    }

    private static String range(LocalDate from, LocalDate to) {
        return "?from=" + from + "&to=" + to;
    }

    /** Trợ giảng lên dạy chính giữa kỳ: buổi trước ngày lên là ASSISTANT, từ ngày lên (kể cả buổi sáng sớm giờ VN) là TEACHER. */
    @Test
    void assistantPromotedMidTermIsCountedByRoleOnSessionDayInVietnamTime() throws Exception {
        LocalDate today = LocalDate.now(VN);
        LocalDate promoted = today.minusDays(3);
        long course = course();
        long cls = clazz(course, "D176 lên dạy chính " + UUID.randomUUID());
        var x = withRole("TEACHER", "ASSISTANT");
        staff(cls, x, "ASSISTANT", today.minusDays(20), promoted.minusDays(1), null);
        staff(cls, x, "TEACHER", promoted, null, null);
        var st = member(cls, course);
        session(cls, x, st, vn(today.minusDays(6), 18)); // còn là trợ giảng
        session(cls, x, st, vn(promoted, 18));            // đã lên dạy chính
        session(cls, x, st, vn(promoted, 6));             // 06:00 VN = 23:00 UTC hôm trước: vẫn là ngày lên dạy chính
        var acc = withRole("ACCOUNTANT");

        JsonNode r = report(range(today.minusDays(10), today) + "&classId=" + cls, acc.accessToken());
        assertEquals(List.of("ASSISTANT:1", "TEACHER:2"), rowsOf(r, x.id()), "vai phải theo ngày buổi học giờ Việt Nam");
    }

    /** Phân công đã thu hồi không còn quyết định vai của buổi dạy sau thời điểm thu hồi. */
    @Test
    void revokedAssignmentDoesNotDecideRoleOfLaterSessions() throws Exception {
        LocalDate today = LocalDate.now(VN);
        long course = course();
        long cls = clazz(course, "D176 thu hồi " + UUID.randomUUID());
        var x = withRole("TEACHER", "ASSISTANT");
        staff(cls, x, "TEACHER", today.minusDays(20), null, null);
        staff(cls, x, "ASSISTANT", today.minusDays(20), null, vn(today.minusDays(10), 9)); // gán nhầm rồi thu hồi
        var st = member(cls, course);
        session(cls, x, st, vn(today.minusDays(2), 18));
        var acc = withRole("ACCOUNTANT");

        assertEquals(List.of("TEACHER:1"), rowsOf(report(range(today.minusDays(5), today) + "&classId=" + cls, acc.accessToken()), x.id()));
    }

    /** Lọc theo vai cộng lại phải bằng không lọc (không mất buổi nào). */
    @Test
    void roleFilteredRowsAddUpToUnfilteredReport() throws Exception {
        LocalDate today = LocalDate.now(VN);
        long course = course();
        long cls = clazz(course, "D176 cộng " + UUID.randomUUID());
        var t = withRole("TEACHER");
        var a = withRole("ASSISTANT");
        staff(cls, t, "TEACHER", today.minusDays(20), null, null);
        staff(cls, a, "ASSISTANT", today.minusDays(20), null, null);
        var st = member(cls, course);
        session(cls, t, st, vn(today.minusDays(2), 18));
        session(cls, a, st, vn(today.minusDays(1), 18));
        var acc = withRole("ACCOUNTANT");
        String q = range(today.minusDays(5), today) + "&classId=" + cls;
        int all = sum(report(q, acc.accessToken()));
        int teachers = sum(report(q + "&role=TEACHER", acc.accessToken()));
        int assistants = sum(report(q + "&role=ASSISTANT", acc.accessToken()));
        assertEquals(2, all);
        assertEquals(all, teachers + assistants);
    }

    private static int sum(JsonNode r) {
        int s = 0;
        for (JsonNode n : r.get("teachers")) s += n.get("totalSessions").asInt();
        return s;
    }

    /** Biên tham số: năm nhuận 366 ngày được, 367 không; to trước from; thiếu from; role sai chữ hoa; classId không phải số. */
    @Test
    void parameterBoundariesReturn400WithFieldDetails() throws Exception {
        var acc = withRole("ACCOUNTANT");
        get(REPORT + range(LocalDate.of(2028, 1, 1), LocalDate.of(2028, 12, 31)), acc.accessToken()).andExpect(status().isOk());
        get(REPORT + range(LocalDate.of(2028, 1, 1), LocalDate.of(2029, 1, 1)), acc.accessToken()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.to").exists());
        get(REPORT + range(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 1)), acc.accessToken()).andExpect(status().isOk()); // 1 ngày
        get(REPORT + range(LocalDate.of(2027, 1, 2), LocalDate.of(2027, 1, 1)), acc.accessToken()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.to").exists());
        get(REPORT + "?to=2027-01-01", acc.accessToken()).andExpect(status().isBadRequest());
        get(REPORT + range(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 2)) + "&role=teacher", acc.accessToken()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.role").exists());
        get(REPORT + range(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 2)) + "&classId=-1", acc.accessToken()).andExpect(status().isBadRequest());
        // tham số sai thì không ghi nhật ký xuất
        int before = count("select count(*) from teaching_report_exports");
        export(range(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 2)) + "&role=ADMIN", acc.accessToken()).andExpect(status().isBadRequest());
        assertEquals(before, count("select count(*) from teaching_report_exports"));
    }

    /** Quyền report.teaching cho cả ba endpoint: ADMIN, TRAINING_MANAGER, ACCOUNTANT 200; TEACHER, ASSISTANT, học viên 403; khách 401. */
    @Test
    void reportEndpointsRequireReportTeachingPermission() throws Exception {
        String q = range(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 2));
        for (String role : List.of("ADMIN", "TRAINING_MANAGER", "ACCOUNTANT")) {
            String tok = withRole(role).accessToken();
            get(REPORT + "/classes", tok).andExpect(status().isOk());
            get(REPORT + q + "&role=ASSISTANT", tok).andExpect(status().isOk());
        }
        for (String role : List.of("TEACHER", "ASSISTANT")) {
            String tok = withRole(role).accessToken();
            get(REPORT + "/classes", tok).andExpect(status().isForbidden());
            get(REPORT + q, tok).andExpect(status().isForbidden());
            export(q, tok).andExpect(status().isForbidden());
        }
        String student = register(newEmail(), PASSWORD).accessToken();
        get(REPORT + "/classes", student).andExpect(status().isForbidden());
        export(q, student).andExpect(status().isForbidden());
        get(REPORT + "/classes", null).andExpect(status().isUnauthorized());
        export(q, null).andExpect(status().isUnauthorized());
    }

    /** CSV: BOM, dòng bộ lọc có tên lớp chứa dấu phẩy được bọc nháy, tên bắt đầu bằng = được chèn ', cột vai trò. */
    @Test
    void csvHasBomFilterLineAndEscapesNames() throws Exception {
        LocalDate today = LocalDate.now(VN);
        long course = course();
        String name = "=HYPERLINK(\"x\"),Lớp " + UUID.randomUUID().toString().substring(0, 4);
        long cls = clazz(course, name);
        var t = withRole("TEACHER");
        jdbc.update("update user_profiles set display_name = ? where user_id = ?", "@SUM(1+1)", t.id());
        staff(cls, t, "TEACHER", today.minusDays(20), null, null);
        var st = member(cls, course);
        session(cls, t, st, vn(today.minusDays(1), 18));
        var acc = withRole("ACCOUNTANT");
        byte[] body = export(range(today.minusDays(5), today) + "&classId=" + cls + "&role=TEACHER", acc.accessToken())
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertEquals((byte) 0xEF, body[0]);
        String csv = new String(body, 3, body.length - 3, StandardCharsets.UTF_8);
        String[] lines = csv.split("\n");
        assertTrue(lines[0].startsWith("Báo cáo số buổi dạy," + today.minusDays(5) + "," + today + ",Vai trò: Giáo viên,"), lines[0]);
        assertTrue(lines[0].contains("\"Lớp: =HYPERLINK(\"\"x\"\"),Lớp "), "tên lớp có dấu phẩy, nháy phải được bọc: " + lines[0]);
        assertTrue(csv.contains(t.id() + ",'@SUM(1+1),Giáo viên,1,1.00,0,0.00,1,1.00") || csv.contains(t.id() + ",'@SUM(1+1),Giáo viên,1,1,"), csv);
    }

    /** Xuất theo một lớp không được làm mất danh sách "buổi bị sửa sau lần xuất trước" của lớp khác chưa từng xuất. */
    @Test
    void exportingOneClassKeepsEditedSessionsOfOtherClasses() throws Exception {
        LocalDate today = LocalDate.now(VN);
        long course = course();
        long a = clazz(course, "D176 xuất A " + UUID.randomUUID());
        long b = clazz(course, "D176 xuất B " + UUID.randomUUID());
        var t = withRole("TEACHER");
        staff(a, t, "TEACHER", today.minusDays(20), null, null);
        staff(b, t, "TEACHER", today.minusDays(20), null, null);
        var sa = member(a, course);
        var sb = member(b, course);
        session(a, t, sa, vn(today.minusDays(25), 18));
        long sessB = session(b, t, sb, vn(today.minusDays(25), 18));
        jdbc.update("insert into attendance_changes (session_id, user_id, old_status, new_status, changed_by, changed_at) values (?, ?, 'ABSENT', 'PRESENT', ?, ?)",
                sessB, sb.id(), t.id(), Timestamp.from(Instant.now().minusSeconds(60)));
        var acc = withRole("ACCOUNTANT");
        String q = range(today.minusDays(27), today.minusDays(23));
        get(REPORT + q + "&classId=" + b, acc.accessToken()).andExpect(jsonPath("$.editedSessions[*].sessionId", hasItem((int) sessB)));

        export(q + "&classId=" + a, acc.accessToken()).andExpect(status().isOk()); // chỉ xuất lớp A

        get(REPORT + q + "&classId=" + b, acc.accessToken()).andExpect(jsonPath("$.editedSessions[*].sessionId", hasItem((int) sessB)));
    }


    /** Tham số sai kiểu phải 400, không 500 (classId mới thêm; from sai định dạng để so sánh hành vi sẵn có). */
    @Test
    void malformedQueryParamsReturn400Not500() throws Exception {
        var acc = withRole("ACCOUNTANT");
        get(REPORT + range(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 2)) + "&classId=abc", acc.accessToken()).andExpect(status().isBadRequest());
        get(REPORT + "?from=abc&to=2027-01-02", acc.accessToken()).andExpect(status().isBadRequest());
    }

    private void edit(long session, Account student, Account by, Instant at) {
        jdbc.update("insert into attendance_changes (session_id, user_id, old_status, new_status, changed_by, changed_at) values (?, ?, 'ABSENT', 'PRESENT', ?, ?)",
                session, student.id(), by.id(), Timestamp.from(at));
    }

    private List<Long> edited(String query, String token) throws Exception {
        List<Long> out = new ArrayList<>();
        report(query, token).get("editedSessions").forEach(n -> out.add(n.get("sessionId").asLong()));
        return out;
    }

    /** Mốc theo bộ lọc: xuất theo vai chỉ đóng mốc buổi của vai đó; xuất khoảng ngày khác không đóng; xuất toàn bộ đóng mọi buổi; sửa sau mốc hiện lại. */
    @Test
    void exportMarkFollowsFilterOfEachExport() throws Exception {
        LocalDate today = LocalDate.now(VN);
        long course = course();
        long cls = clazz(course, "D176 mốc " + UUID.randomUUID());
        var t = withRole("TEACHER");
        var a = withRole("ASSISTANT");
        staff(cls, t, "TEACHER", today.minusDays(20), null, null);
        staff(cls, a, "ASSISTANT", today.minusDays(20), null, null);
        var st = member(cls, course);
        long s1 = session(cls, t, st, vn(today.minusDays(15), 18));
        long s2 = session(cls, a, st, vn(today.minusDays(15), 19));
        edit(s1, st, t, Instant.now().minusSeconds(60));
        edit(s2, st, a, Instant.now().minusSeconds(60));
        var acc = withRole("ACCOUNTANT");
        String q = range(today.minusDays(17), today.minusDays(13)) + "&classId=" + cls;

        export(range(today.minusDays(12), today.minusDays(11)) + "&classId=" + cls, acc.accessToken()).andExpect(status().isOk()); // không chứa ngày buổi
        assertEquals(List.of(s1, s2), edited(q, acc.accessToken()).stream().sorted().toList());

        export(q + "&role=TEACHER", acc.accessToken()).andExpect(status().isOk());
        assertEquals(List.of(s2), edited(q, acc.accessToken()));
        assertTrue(report(q, acc.accessToken()).get("lastExportAt").isNull(), "chưa có lần xuất nào phủ trọn bộ lọc đang xem");
        assertTrue(!report(q + "&role=TEACHER", acc.accessToken()).get("lastExportAt").isNull());
        assertEquals(List.of(), edited(q + "&role=TEACHER", acc.accessToken()));

        edit(s1, st, t, Instant.now().plusSeconds(30)); // sửa sau lần xuất
        JsonNode again = report(q + "&role=TEACHER", acc.accessToken()).get("editedSessions");
        assertEquals(1, again.size());
        assertEquals(1, again.get(0).get("edits").asInt());

        export(range(today.minusDays(17), today.minusDays(13)), acc.accessToken()).andExpect(status().isOk()); // toàn bộ
        assertEquals(List.of(s1), edited(q, acc.accessToken())); // chỉ còn lần sửa sau mọi lần xuất
    }
}
