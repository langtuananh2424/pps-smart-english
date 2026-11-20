package com.pps.smartenglish.classroom;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.support.ApiTestBase;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** FR-CLS-01..04, NFR-SEC-05: lớp, danh sách chờ, xếp/chuyển/rút, phân công, và cách ly giữa các lớp. */
class ClassApiTest extends ApiTestBase {

    private static final String PASSWORD = "mat-khau-1";

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private Account withRole(String role) throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = ?", u.id(), role);
        return login(u.email(), PASSWORD);
    }

    private long course(String mode) {
        return insert("courses", Map.of("slug", "cls-" + UUID.randomUUID(), "title", "Khóa lớp", "status", "PUBLISHED", "delivery_mode", mode));
    }

    private void enroll(Account s, long courseId, String status, String expiresSql) {
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, ?, CURRENT_TIMESTAMP, "
                + expiresSql + ")", s.id(), courseId, status);
    }

    private ResultActions call(HttpMethod method, String url, String token, Object body) throws Exception {
        var req = MockMvcRequestBuilders.request(method, url);
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return send(req, token);
    }

    private Map<String, Object> classBody(long courseId, int capacity) {
        var m = new LinkedHashMap<String, Object>();
        m.put("courseId", courseId);
        m.put("name", "Lớp " + UUID.randomUUID().toString().substring(0, 4));
        m.put("startsOn", LocalDate.now().toString());
        m.put("endsOn", LocalDate.now().plusMonths(3).toString());
        m.put("mode", "ONLINE");
        m.put("capacity", capacity);
        return m;
    }

    private long createClass(Account manager, long courseId, int capacity) throws Exception {
        String body = call(HttpMethod.POST, "/api/admin/classes", manager.accessToken(), classBody(courseId, capacity))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private Account enrolledStudent(long courseId) throws Exception {
        var s = register(newEmail(), PASSWORD);
        enroll(s, courseId, "ACTIVE", "null");
        return s;
    }

    @Test
    void createRejectsSelfPacedCourseAndBadValues() throws Exception {
        var mgr = withRole("TRAINING_MANAGER");
        long self = course("SELF_PACED");
        call(HttpMethod.POST, "/api/admin/classes", mgr.accessToken(), classBody(self, 10))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.courseId").exists());

        long cls = course("CLASS_BASED");
        var bad = classBody(cls, 10);
        bad.put("endsOn", LocalDate.now().minusDays(1).toString());
        call(HttpMethod.POST, "/api/admin/classes", mgr.accessToken(), bad)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.endsOn").exists());
        call(HttpMethod.POST, "/api/admin/classes", mgr.accessToken(), classBody(cls, 0))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.capacity").exists());
        createClass(mgr, cls, 10);
    }

    @Test
    void waitingListThenAssignWithNotificationAndRules() throws Exception {
        var mgr = withRole("TRAINING_MANAGER");
        long course = course("CLASS_BASED");
        long classA = createClass(mgr, course, 1);
        var s = enrolledStudent(course);

        call(HttpMethod.GET, "/api/admin/classes/waiting?courseId=" + course, mgr.accessToken(), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[*].userId", hasItem((int) s.id())));

        call(HttpMethod.POST, "/api/admin/classes/" + classA + "/members", mgr.accessToken(), Map.of("userId", s.id()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE"));
        assertEquals(1, count("select count(*) from notifications where user_id = ? and type = 'CLASS_ASSIGNED'", s.id()));
        call(HttpMethod.GET, "/api/admin/classes/waiting?courseId=" + course, mgr.accessToken(), null)
                .andExpect(jsonPath("$.data[?(@.userId == " + s.id() + ")]", hasSize(0)));

        // đã có lớp đang học trong khóa; lớp đầy; chưa mua; hết hạn; lớp đóng
        long classB = createClass(mgr, course, 5);
        call(HttpMethod.POST, "/api/admin/classes/" + classB + "/members", mgr.accessToken(), Map.of("userId", s.id()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("already_in_class"));
        var s2 = enrolledStudent(course);
        call(HttpMethod.POST, "/api/admin/classes/" + classA + "/members", mgr.accessToken(), Map.of("userId", s2.id()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("class_full"));
        var noBuy = register(newEmail(), PASSWORD);
        call(HttpMethod.POST, "/api/admin/classes/" + classB + "/members", mgr.accessToken(), Map.of("userId", noBuy.id()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("not_enrolled"));
        var expired = register(newEmail(), PASSWORD);
        enroll(expired, course, "ACTIVE", "TIMESTAMPADD(DAY, -1, CURRENT_TIMESTAMP)");
        call(HttpMethod.POST, "/api/admin/classes/" + classB + "/members", mgr.accessToken(), Map.of("userId", expired.id()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("not_enrolled"));
        call(HttpMethod.POST, "/api/admin/classes/" + classB + "/close", mgr.accessToken(), null).andExpect(status().isOk());
        call(HttpMethod.POST, "/api/admin/classes/" + classB + "/members", mgr.accessToken(), Map.of("userId", s2.id()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("class_closed"));
    }

    @Test
    void transferKeepsHistoryAndWithdrawFreesSeat() throws Exception {
        var mgr = withRole("TRAINING_MANAGER");
        long course = course("CLASS_BASED");
        long a = createClass(mgr, course, 5);
        long b = createClass(mgr, course, 5);
        var s = enrolledStudent(course);
        call(HttpMethod.POST, "/api/admin/classes/" + a + "/members", mgr.accessToken(), Map.of("userId", s.id())).andExpect(status().isCreated());

        call(HttpMethod.POST, "/api/admin/classes/" + a + "/members/" + s.id() + "/transfer", mgr.accessToken(), Map.of("toClassId", b))
                .andExpect(status().isOk());
        assertEquals(1, count("select count(*) from notifications where user_id = ? and type = 'CLASS_TRANSFERRED'", s.id()));
        call(HttpMethod.GET, "/api/admin/classes/students/" + s.id() + "/history", mgr.accessToken(), null)
                .andExpect(jsonPath("$", hasSize(2))).andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[1].status").value("TRANSFERRED"));

        long otherCourse = course("CLASS_BASED");
        long c = createClass(mgr, otherCourse, 5);
        call(HttpMethod.POST, "/api/admin/classes/" + b + "/members/" + s.id() + "/transfer", mgr.accessToken(), Map.of("toClassId", c))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("course_mismatch"));

        call(HttpMethod.DELETE, "/api/admin/classes/" + b + "/members/" + s.id(), mgr.accessToken(), null).andExpect(status().isOk());
        call(HttpMethod.DELETE, "/api/admin/classes/" + b + "/members/" + s.id(), mgr.accessToken(), null).andExpect(status().isNotFound());
        call(HttpMethod.GET, "/api/admin/classes/waiting?courseId=" + course, mgr.accessToken(), null)
                .andExpect(jsonPath("$.data[*].userId", hasItem((int) s.id())));
    }

    @Test
    void replacingTeacherRevokesOldOneImmediately() throws Exception {
        var mgr = withRole("TRAINING_MANAGER");
        long cls = createClass(mgr, course("CLASS_BASED"), 5);
        var t1 = withRole("TEACHER");
        var t2 = withRole("TEACHER");

        call(HttpMethod.PUT, "/api/admin/classes/" + cls + "/teacher", mgr.accessToken(), Map.of("userId", t1.id()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        call(HttpMethod.GET, "/api/classes/" + cls, t1.accessToken(), null).andExpect(status().isOk());
        call(HttpMethod.GET, "/api/teaching/classes", t1.accessToken(), null).andExpect(jsonPath("$", hasSize(1)));

        call(HttpMethod.PUT, "/api/admin/classes/" + cls + "/teacher", mgr.accessToken(), Map.of("userId", t2.id())).andExpect(status().isOk());
        call(HttpMethod.GET, "/api/classes/" + cls, t1.accessToken(), null).andExpect(status().isNotFound());
        call(HttpMethod.GET, "/api/teaching/classes", t1.accessToken(), null).andExpect(jsonPath("$", hasSize(0)));
        call(HttpMethod.GET, "/api/classes/" + cls, t2.accessToken(), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.info.teacher.userId").value(t2.id()));
        assertEquals(1, count("select count(*) from class_staff where class_id = ? and role = 'TEACHER' and revoked_at is null", cls));

        // vai trò sai, giáo viên chính không thu hồi trực tiếp
        var plain = register(newEmail(), PASSWORD);
        call(HttpMethod.PUT, "/api/admin/classes/" + cls + "/teacher", mgr.accessToken(), Map.of("userId", plain.id()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.userId").exists());
        long teacherRow = jdbc.queryForObject("select id from class_staff where class_id = ? and role = 'TEACHER' and revoked_at is null", Long.class, cls);
        call(HttpMethod.DELETE, "/api/admin/classes/" + cls + "/staff/" + teacherRow, mgr.accessToken(), null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("teacher_required"));
    }

    @Test
    void assistantCanBeAddedAndRevoked() throws Exception {
        var mgr = withRole("TRAINING_MANAGER");
        long cls = createClass(mgr, course("CLASS_BASED"), 5);
        var as = withRole("ASSISTANT");
        String body = call(HttpMethod.POST, "/api/admin/classes/" + cls + "/assistants", mgr.accessToken(), Map.of("userId", as.id()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long rowId = json.readTree(body).get("id").asLong();
        call(HttpMethod.POST, "/api/admin/classes/" + cls + "/assistants", mgr.accessToken(), Map.of("userId", as.id()))
                .andExpect(status().isConflict());
        call(HttpMethod.GET, "/api/classes/" + cls, as.accessToken(), null).andExpect(status().isOk());
        call(HttpMethod.DELETE, "/api/admin/classes/" + cls + "/staff/" + rowId, mgr.accessToken(), null).andExpect(status().isOk());
        call(HttpMethod.GET, "/api/classes/" + cls, as.accessToken(), null).andExpect(status().isNotFound());
    }

    @Test
    void teacherOfClassAcannotSeeOrTouchClassB() throws Exception {
        var mgr = withRole("TRAINING_MANAGER");
        long course = course("CLASS_BASED");
        long a = createClass(mgr, course, 5);
        long b = createClass(mgr, course, 5);
        var ta = withRole("TEACHER");
        call(HttpMethod.PUT, "/api/admin/classes/" + a + "/teacher", mgr.accessToken(), Map.of("userId", ta.id())).andExpect(status().isOk());
        var sb = enrolledStudent(course);
        call(HttpMethod.POST, "/api/admin/classes/" + b + "/members", mgr.accessToken(), Map.of("userId", sb.id())).andExpect(status().isCreated());

        call(HttpMethod.GET, "/api/classes/" + a, ta.accessToken(), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.members").isArray());
        call(HttpMethod.GET, "/api/classes/" + b, ta.accessToken(), null).andExpect(status().isNotFound());
        // không có quyền quản lý lớp
        call(HttpMethod.PATCH, "/api/admin/classes/" + a, ta.accessToken(), Map.of("name", "Đổi tên")).andExpect(status().isForbidden());
        call(HttpMethod.POST, "/api/admin/classes/" + b + "/members", ta.accessToken(), Map.of("userId", sb.id())).andExpect(status().isForbidden());
        call(HttpMethod.GET, "/api/admin/classes", ta.accessToken(), null).andExpect(status().isForbidden());

        // học viên: thấy lớp mình, không thấy danh sách bạn học, không thấy lớp khác
        call(HttpMethod.GET, "/api/classes/" + b, sb.accessToken(), null).andExpect(status().isOk()).andExpect(jsonPath("$.members").value(nullValue()));
        call(HttpMethod.GET, "/api/classes/" + a, sb.accessToken(), null).andExpect(status().isNotFound());
        call(HttpMethod.GET, "/api/me/classes", sb.accessToken(), null).andExpect(jsonPath("$", hasSize(1)));
        // lớp không tồn tại giống như lớp của người khác
        call(HttpMethod.GET, "/api/classes/999999", sb.accessToken(), null).andExpect(status().isNotFound());
    }

    @Test
    void closingClassCompletesMembersAndBlocksEdits() throws Exception {
        var mgr = withRole("TRAINING_MANAGER");
        long course = course("CLASS_BASED");
        long a = createClass(mgr, course, 5);
        var s = enrolledStudent(course);
        call(HttpMethod.POST, "/api/admin/classes/" + a + "/members", mgr.accessToken(), Map.of("userId", s.id())).andExpect(status().isCreated());
        call(HttpMethod.PATCH, "/api/admin/classes/" + a, mgr.accessToken(), Map.of("capacity", 1)).andExpect(status().isOk());
        call(HttpMethod.PATCH, "/api/admin/classes/" + a, mgr.accessToken(), Map.of("startsOn", LocalDate.now().plusYears(1).toString()))
                .andExpect(status().isBadRequest());

        call(HttpMethod.POST, "/api/admin/classes/" + a + "/close", mgr.accessToken(), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
        call(HttpMethod.PATCH, "/api/admin/classes/" + a, mgr.accessToken(), Map.of("name", "X")).andExpect(status().isConflict());
        assertEquals("COMPLETED", jdbc.queryForObject("select status from class_members where class_id = ?", String.class, a));
        call(HttpMethod.GET, "/api/classes/" + a, s.accessToken(), null).andExpect(status().isOk());
    }

    @Test
    void courseCannotGoBackToSelfPacedOnceItHasClasses() throws Exception {
        var admin = withRole("ADMIN");
        long course = course("CLASS_BASED");
        createClass(withRole("TRAINING_MANAGER"), course, 5);
        call(HttpMethod.PATCH, "/api/admin/cms/courses/" + course, admin.accessToken(), Map.of("deliveryMode", "SELF_PACED"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("course_has_classes"));
        long free = course("SELF_PACED");
        call(HttpMethod.PATCH, "/api/admin/cms/courses/" + free, admin.accessToken(), Map.of("deliveryMode", "CLASS_BASED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.deliveryMode").value("CLASS_BASED"));
    }
}
