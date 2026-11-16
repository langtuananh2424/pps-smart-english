package com.pps.smartenglish.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.support.ApiTestBase;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;

/** D-57: mỗi khóa tối đa một topic học thử; khách chỉ xem được bài của topic đó. */
class FreeTrialRuleTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private long topic(long level, String title, int order, boolean free) {
        return insert("topics", Map.of("level_id", level, "title", title, "sort_order", order, "is_free_trial", free, "status", "PUBLISHED"));
    }

    private long lesson(long topic) {
        return insert("lessons", Map.of("topic_id", topic, "type", "CONNECTIVE_VIDEO", "title", "Bài", "status", "PUBLISHED",
                "video_source", "STORAGE", "video_ref", "videos/a.mp4", "duration_sec", 60));
    }

    private boolean free(long topic) {
        return jdbc.queryForObject("select is_free_trial from topics where id = ?", Boolean.class, topic);
    }

    private Account admin() throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = 'ADMIN'", u.id());
        return login(u.email(), PASSWORD);
    }

    @Test
    void enablingOneTopicTurnsOffTheOtherTopicsOfTheSameCourseOnly() throws Exception {
        long c = insert("courses", Map.of("slug", "ft-" + UUID.randomUUID(), "title", "K", "status", "PUBLISHED"));
        long l1 = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "A", "status", "PUBLISHED"));
        long l2 = insert("course_levels", Map.of("course_id", c, "code", "B", "title", "B", "status", "PUBLISHED"));
        long a = topic(l1, "A", 1, true);
        long b = topic(l2, "B", 1, false); // khác level, cùng khóa
        long other = insert("courses", Map.of("slug", "ft-" + UUID.randomUUID(), "title", "K2", "status", "PUBLISHED"));
        long ol = insert("course_levels", Map.of("course_id", other, "code", "A", "title", "A", "status", "PUBLISHED"));
        long otherFree = topic(ol, "Khác", 1, true);

        var admin = admin();
        send(patch("/api/admin/cms/topics/" + b).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("isFreeTrial", true))), admin.accessToken())
                .andExpect(status().isOk()).andExpect(jsonPath("$.isFreeTrial").value(true));
        assertEquals(false, free(a));
        assertEquals(true, free(b));
        assertEquals(true, free(otherFree)); // khóa khác không bị đụng

        // tạo topic mới đã bật học thử cũng tắt topic đang bật
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/admin/cms/levels/" + l1 + "/topics")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("title", "Mới", "isFreeTrial", true))),
                admin.accessToken()).andExpect(status().isCreated());
        assertEquals(false, free(b));
        assertEquals(1, count("select count(*) from topics t join course_levels lv on lv.id = t.level_id "
                + "where lv.course_id = ? and t.is_free_trial = true", c));
    }

    @Test
    void guestWatchesTrialTopicLessonButOtherTopicIsForbidden() throws Exception {
        long c = insert("courses", Map.of("slug", "ft-" + UUID.randomUUID(), "title", "K", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "A", "status", "PUBLISHED"));
        long trial = topic(lv, "Học thử", 1, true);
        long locked = topic(lv, "Khóa", 2, false);
        long trialLesson = lesson(trial);
        long lockedLesson = lesson(locked);

        mvc.perform(get("/api/lessons/" + trialLesson)).andExpect(status().isOk());
        mvc.perform(get("/api/lessons/" + lockedLesson)).andExpect(status().isForbidden());
    }

    @Test
    void firstTopicOfFirstLevelDefaultsToFreeTrialOthersDoNot() throws Exception {
        long c = insert("courses", Map.of("slug", "ft-" + UUID.randomUUID(), "title", "K", "status", "PUBLISHED"));
        long l1 = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "A", "sort_order", 1, "status", "PUBLISHED"));
        long l2 = insert("course_levels", Map.of("course_id", c, "code", "B", "title", "B", "sort_order", 2, "status", "PUBLISHED"));
        var admin = admin();
        long first = create(admin, l1, Map.of("title", "T1"));
        long second = create(admin, l1, Map.of("title", "T2"));
        long otherLevel = create(admin, l2, Map.of("title", "T3"));
        assertEquals(true, free(first));
        assertEquals(false, free(second));
        assertEquals(false, free(otherLevel));
    }

    private long create(Account admin, long level, Map<String, Object> body) throws Exception {
        var res = send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/admin/cms/levels/" + level + "/topics")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)), admin.accessToken())
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("id").asLong();
    }
}
