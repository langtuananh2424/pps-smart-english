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

/** Tester đợt #63: biên ngưỡng, ngưỡng là của từng khóa, hạ ngưỡng, quyền CMS (FR-LMS-01, FR-LMS-03, D-124). */
class LessonCompletePercentEdgeTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";

    private record Course(long id, long lesson) {}

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private Course course() {
        long c = insert("courses", Map.of("slug", "pcte-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED", "sort_order", 0));
        long t = insert("topics", Map.of("level_id", lv, "title", "Unit A", "is_free_trial", false, "status", "PUBLISHED", "sort_order", 0));
        var l = new HashMap<String, Object>(Map.of("topic_id", t, "type", "CONNECTIVE_VIDEO", "title", "Bài", "status", "PUBLISHED"));
        l.put("video_source", "YOUTUBE");
        l.put("video_ref", "abc");
        l.put("duration_sec", 100);
        return new Course(c, insert("lessons", l));
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
    void boundaryOneIsAcceptedAndPatchWithoutFieldKeepsValue() throws Exception {
        var c = course();
        var admin = withRole("ADMIN");
        cmsPatch(admin, c.id(), "{\"lessonCompletePercent\":1}").andExpect(status().isOk()).andExpect(jsonPath("$.lessonCompletePercent").value(1));
        cmsPatch(admin, c.id(), "{\"lessonCompletePercent\":100}").andExpect(status().isOk());
        cmsPatch(admin, c.id(), "{\"sequential\":true}").andExpect(status().isOk()).andExpect(jsonPath("$.lessonCompletePercent").value(100));
        cmsPatch(admin, c.id(), "{\"lessonCompletePercent\":-5}").andExpect(status().isBadRequest());
        cmsPatch(admin, c.id(), "{\"lessonCompletePercent\":\"abc\"}").andExpect(status().isBadRequest());
        cmsPatch(admin, c.id(), "{\"lessonCompletePercent\":2147483648}").andExpect(status().isBadRequest());
        assertEquals(100, jdbc.queryForObject("select lesson_complete_percent from courses where id = ?", Integer.class, c.id()));
    }

    @Test
    void teacherCannotChangeThreshold() throws Exception {
        var c = course();
        cmsPatch(withRole("TEACHER"), c.id(), "{\"lessonCompletePercent\":10}").andExpect(status().isForbidden());
        assertEquals(80, jdbc.queryForObject("select lesson_complete_percent from courses where id = ?", Integer.class, c.id()));
    }

    @Test
    void thresholdBelongsToTheLessonsCourse() throws Exception {
        var low = course();
        var other = course();
        var admin = withRole("ADMIN");
        cmsPatch(admin, low.id(), "{\"lessonCompletePercent\":10}").andExpect(status().isOk());
        var s = student(other);
        watch(s, other.lesson(), 20); // 20% đủ ở khóa kia nhưng khóa này vẫn 80
        assertEquals(false, completed(s, other.lesson()));
    }

    @Test
    void loweringThresholdCompletesOnNextReport() throws Exception {
        var c = course();
        var admin = withRole("ADMIN");
        var s = student(c);
        watch(s, c.lesson(), 60);
        assertEquals(false, completed(s, c.lesson()));
        cmsPatch(admin, c.id(), "{\"lessonCompletePercent\":50}").andExpect(status().isOk());
        assertEquals(false, completed(s, c.lesson()), "chỉ đọc tiến độ thì chưa tính lại");
        watch(s, c.lesson(), 1);
        assertEquals(true, completed(s, c.lesson()));
        assertEquals(1, count("select count(*) from lesson_progress where user_id = ? and lesson_id = ? and completed_at is not null",
                s.id(), c.lesson()));
    }
}
