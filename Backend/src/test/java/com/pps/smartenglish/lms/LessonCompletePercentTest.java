package com.pps.smartenglish.lms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.support.ApiTestBase;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** FR-LMS-03, D-124: % thời lượng xem để xong bài cài theo khóa, mặc định 80. */
class LessonCompletePercentTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";

    private record Course(long id, long lessonA, long lessonB) {}

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private long lesson(long topic) {
        var l = new HashMap<String, Object>(Map.of("topic_id", topic, "type", "CONNECTIVE_VIDEO", "title", "Bài", "status", "PUBLISHED"));
        l.put("video_source", "YOUTUBE");
        l.put("video_ref", "abc");
        l.put("duration_sec", 100);
        return insert("lessons", l);
    }

    /** Khóa 2 unit, mỗi unit một bài 100 giây; unit B bị khóa khi bật tuần tự. */
    private Course course() {
        long c = insert("courses", Map.of("slug", "pct-" + UUID.randomUUID(), "title", "Khóa ngưỡng", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED", "sort_order", 0));
        long ta = insert("topics", Map.of("level_id", lv, "title", "Unit A", "is_free_trial", false, "status", "PUBLISHED", "sort_order", 0));
        long tb = insert("topics", Map.of("level_id", lv, "title", "Unit B", "is_free_trial", false, "status", "PUBLISHED", "sort_order", 1));
        return new Course(c, lesson(ta), lesson(tb));
    }

    private Account student(Course c) throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) "
                + "values (?, ?, 'ACTIVE', TIMESTAMP '2020-01-01 00:00:00', TIMESTAMP '2099-01-01 00:00:00')", u.id(), c.id());
        return u;
    }

    private Account withRole(String role) throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = ?", u.id(), role);
        return login(u.email(), PASSWORD);
    }

    private ResultActions cmsPatch(Account a, long course, String body) throws Exception {
        return send(patch("/api/admin/cms/courses/" + course).contentType(MediaType.APPLICATION_JSON).content(body), a.accessToken());
    }

    /** Báo xem thêm `watched` giây (tách nhiều request vì mỗi lần tối đa 20 giây). */
    private void watch(Account u, long lessonId, int watched) throws Exception {
        for (int left = watched; left > 0; left -= 20) {
            send(MockMvcRequestBuilders.post("/api/lessons/" + lessonId + "/progress").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"positionSec\":" + watched + ",\"watchedDeltaSec\":" + Math.min(left, 20) + "}"), u.accessToken())
                    .andExpect(status().isOk());
        }
    }

    private boolean completed(Account u, long lessonId) throws Exception {
        var body = send(get("/api/lessons/" + lessonId + "/progress"), u.accessToken()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("completed").asBoolean();
    }

    @Test
    void defaultIs80AndCmsEditsIt() throws Exception {
        var c = course();
        var admin = withRole("ADMIN");
        assertEquals(80, jdbc.queryForObject("select lesson_complete_percent from courses where id = ?", Integer.class, c.id()));
        cmsPatch(admin, c.id(), "{\"lessonCompletePercent\":50}").andExpect(status().isOk())
                .andExpect(jsonPath("$.lessonCompletePercent").value(50));
    }

    @Test
    void defaultCourseCompletesAtExactly80() throws Exception {
        var c = course();
        var s = student(c);
        watch(s, c.lessonA(), 79);
        assertEquals(false, completed(s, c.lessonA()));
        watch(s, c.lessonA(), 1);
        assertEquals(true, completed(s, c.lessonA()));
    }

    @Test
    void customPercentAppliesAtBoundary() throws Exception {
        var c = course();
        var admin = withRole("ADMIN");
        cmsPatch(admin, c.id(), "{\"lessonCompletePercent\":50}").andExpect(status().isOk());
        var s = student(c);
        watch(s, c.lessonA(), 49);
        assertEquals(false, completed(s, c.lessonA()));
        watch(s, c.lessonA(), 1);
        assertEquals(true, completed(s, c.lessonA()));

        cmsPatch(admin, c.id(), "{\"lessonCompletePercent\":100}").andExpect(status().isOk());
        var s2 = student(c);
        watch(s2, c.lessonA(), 99);
        assertEquals(false, completed(s2, c.lessonA()));
        watch(s2, c.lessonA(), 1);
        assertEquals(true, completed(s2, c.lessonA()));
        // đổi ngưỡng không thu hồi bài đã xong của người học trước
        assertEquals(true, completed(s, c.lessonA()));
    }

    @Test
    void sequentialUnlockFollowsCoursePercent() throws Exception {
        var c = course();
        var admin = withRole("ADMIN");
        cmsPatch(admin, c.id(), "{\"sequential\":true,\"lessonCompletePercent\":50}").andExpect(status().isOk());
        var s = student(c);
        send(get("/api/lessons/" + c.lessonB()), s.accessToken()).andExpect(status().isForbidden());
        watch(s, c.lessonA(), 49);
        send(get("/api/lessons/" + c.lessonB()), s.accessToken()).andExpect(status().isForbidden());
        watch(s, c.lessonA(), 1);
        send(get("/api/lessons/" + c.lessonB()), s.accessToken()).andExpect(status().isOk());
    }

    @Test
    void invalidValueIs400AndNonCmsIs403() throws Exception {
        var c = course();
        var admin = withRole("ADMIN");
        cmsPatch(admin, c.id(), "{\"lessonCompletePercent\":0}").andExpect(status().isBadRequest());
        cmsPatch(admin, c.id(), "{\"lessonCompletePercent\":101}").andExpect(status().isBadRequest());
        var s = student(c);
        cmsPatch(s, c.id(), "{\"lessonCompletePercent\":50}").andExpect(status().isForbidden());
    }
}
