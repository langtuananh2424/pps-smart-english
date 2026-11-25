package com.pps.smartenglish.lms;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.support.ApiTestBase;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;

/**
 * FR-LMS-04, FR-CLS-06, D-122, D-124, NFR-SEC-05: gate của unit với EarlyUnlocksImpl thật (unlock_overrides + class_members),
 * và mỗi học viên chỉ thấy tiến độ của chính mình.
 */
class UnitGateEarlyUnlockTest extends ApiTestBase {

    private record Fx(long course, long classId, long topicA, long topicB, long topicC, long lessonA1, long lessonA2, long trialTopic,
                      long teacherId) {}

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private long lesson(long topic, int order, int duration) {
        return insert("lessons", Map.of("topic_id", topic, "type", "CONNECTIVE_VIDEO", "title", "Bài " + order, "status", "PUBLISHED",
                "video_source", "YOUTUBE", "video_ref", "abc", "duration_sec", duration, "sort_order", order));
    }

    private Fx fixture() throws Exception {
        long c = insert("courses", Map.of("slug", "gate-eu-" + UUID.randomUUID(), "title", "Khóa gate", "status", "PUBLISHED",
                "delivery_mode", "CLASS_BASED", "sequential", true));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED", "sort_order", 0));
        long ta = insert("topics", Map.of("level_id", lv, "title", "Unit A", "is_free_trial", false, "status", "PUBLISHED", "sort_order", 0));
        long tb = insert("topics", Map.of("level_id", lv, "title", "Unit B", "is_free_trial", false, "status", "PUBLISHED", "sort_order", 1));
        long tc = insert("topics", Map.of("level_id", lv, "title", "Unit C", "is_free_trial", false, "status", "PUBLISHED", "sort_order", 2));
        long trial = insert("topics", Map.of("level_id", lv, "title", "Unit thử", "is_free_trial", true, "status", "PUBLISHED", "sort_order", 3));
        long a1 = lesson(ta, 0, 60);
        long a2 = lesson(ta, 1, 60);
        lesson(tb, 0, 60);
        lesson(tc, 0, 60);
        lesson(trial, 0, 60);
        long cls = insert("classes", Map.of("course_id", c, "name", "Lớp " + UUID.randomUUID().toString().substring(0, 4),
                "starts_on", LocalDate.now().minusDays(10), "ends_on", LocalDate.now().plusDays(60), "mode", "ONLINE", "capacity", 20));
        var teacher = register(newEmail(), "Password123!");
        return new Fx(c, cls, ta, tb, tc, a1, a2, trial, teacher.id());
    }

    private Account member(Fx f, String status) throws Exception {
        var s = register(newEmail(), "Password123!");
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', TIMESTAMP '2020-01-01 00:00:00', TIMESTAMP '2099-01-01 00:00:00')",
                s.id(), f.course());
        jdbc.update("insert into class_members (class_id, course_id, user_id, status, live_key, joined_at) values (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                f.classId(), f.course(), s.id(), status, "ACTIVE".equals(status) ? f.course() + ":" + s.id() : null);
        return s;
    }

    private long unlock(Fx f, long topic, Long userId) {
        var cols = new java.util.HashMap<String, Object>(Map.of("class_id", f.classId(), "topic_id", topic, "granted_by", f.teacherId(),
                "granted_at", java.sql.Timestamp.valueOf("2026-11-01 00:00:00"), "live_key", f.classId() + ":" + topic + ":" + (userId == null ? "ALL" : userId)));
        if (userId != null) {
            cols.put("user_id", userId);
        }
        return insert("unlock_overrides", cols);
    }

    private String url(Fx f, long topicId) {
        return "/api/me/courses/" + f.course() + "/units/" + topicId + "/gate";
    }

    @Test
    void personalUnlockOpensOnlyForThatStudent() throws Exception {
        var f = fixture();
        var a = member(f, "ACTIVE");
        var b = member(f, "ACTIVE");
        unlock(f, f.topicB(), a.id());

        send(get(url(f, f.topicB())), a.accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(false)).andExpect(jsonPath("$.openedEarly").value(true));
        send(get(url(f, f.topicB())), b.accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(true)).andExpect(jsonPath("$.openedEarly").value(false))
                .andExpect(jsonPath("$.blockingTopicId").value(f.topicA()));
    }

    @Test
    void wholeClassUnlockAppliesToActiveMembersOnlyAndRevokeRelocks() throws Exception {
        var f = fixture();
        var a = member(f, "ACTIVE");
        var left = member(f, "WITHDRAWN");
        long id = unlock(f, f.topicB(), null);

        send(get(url(f, f.topicB())), a.accessToken()).andExpect(jsonPath("$.openedEarly").value(true));
        send(get(url(f, f.topicB())), left.accessToken()).andExpect(jsonPath("$.locked").value(true));

        jdbc.update("update unlock_overrides set revoked_at = CURRENT_TIMESTAMP, live_key = null where id = ?", id);
        send(get(url(f, f.topicB())), a.accessToken()).andExpect(jsonPath("$.locked").value(true))
                .andExpect(jsonPath("$.openedEarly").value(false));
    }

    /** Mở sớm C nhưng B vẫn chưa xong: C mở (không bị B chặn), gate của C báo openedEarly. */
    @Test
    void unlockSkippingAUnitStillCountsAsEarly() throws Exception {
        var f = fixture();
        var a = member(f, "ACTIVE");
        unlock(f, f.topicC(), a.id());
        send(get(url(f, f.topicC())), a.accessToken()).andExpect(jsonPath("$.locked").value(false))
                .andExpect(jsonPath("$.openedEarly").value(true));
    }

    /** Unlock cho unit mà học viên đã đủ điều kiện mở: không phải "mở sớm". */
    @Test
    void unlockOnAlreadyOpenUnitIsNotEarly() throws Exception {
        var f = fixture();
        var a = member(f, "ACTIVE");
        for (long l : new long[] {f.lessonA1(), f.lessonA2()}) {
            jdbc.update("insert into lesson_progress (user_id, lesson_id, watched_sec, completed_at) values (?, ?, 60, CURRENT_TIMESTAMP)", a.id(), l);
        }
        unlock(f, f.topicB(), a.id());
        send(get(url(f, f.topicB())), a.accessToken()).andExpect(jsonPath("$.locked").value(false))
                .andExpect(jsonPath("$.openedEarly").value(false));
    }

    @Test
    void freeTrialUnitIsNeverLockedNorEarly() throws Exception {
        var f = fixture();
        var a = member(f, "ACTIVE");
        unlock(f, f.trialTopic(), a.id());
        send(get(url(f, f.trialTopic())), a.accessToken()).andExpect(jsonPath("$.locked").value(false))
                .andExpect(jsonPath("$.openedEarly").value(false));
    }

    /** NFR-SEC-05: tiến độ trong gate là của người gọi, không lẫn của học viên khác. */
    @Test
    void progressInGateIsTheCallersOwn() throws Exception {
        var f = fixture();
        var a = member(f, "ACTIVE");
        var b = member(f, "ACTIVE");
        jdbc.update("insert into lesson_progress (user_id, lesson_id, watched_sec, completed_at, max_position_sec) values (?, ?, 60, CURRENT_TIMESTAMP, 60)", a.id(), f.lessonA1());
        jdbc.update("insert into lesson_progress (user_id, lesson_id, watched_sec, max_position_sec) values (?, ?, 45, 45)", a.id(), f.lessonA2());

        send(get(url(f, f.topicB())), a.accessToken()).andExpect(jsonPath("$.lectures.completed").value(1))
                .andExpect(jsonPath("$.lectures.currentPercent").value(75));
        send(get(url(f, f.topicB())), b.accessToken()).andExpect(jsonPath("$.lectures.completed").value(0))
                .andExpect(jsonPath("$.lectures.currentPercent").value(0));
    }

    /** Vị trí xem vượt thời lượng (dữ liệu bẩn) không cho phần trăm quá 100. */
    @Test
    void currentPercentIsCappedAt100() throws Exception {
        var f = fixture();
        var a = member(f, "ACTIVE");
        jdbc.update("insert into lesson_progress (user_id, lesson_id, watched_sec, max_position_sec) values (?, ?, 90, 90)", a.id(), f.lessonA1());
        send(get(url(f, f.topicB())), a.accessToken()).andExpect(jsonPath("$.lectures.currentPercent").value(100));
    }

    @Test
    void nonNumericIdsAre404NotServerError() throws Exception {
        var f = fixture();
        var a = member(f, "ACTIVE");
        send(get("/api/me/courses/abc/units/" + f.topicA() + "/gate"), a.accessToken()).andExpect(status().isNotFound());
        send(get("/api/me/courses/" + f.course() + "/units/1e3/gate"), a.accessToken()).andExpect(status().isNotFound());
        send(get("/api/me/courses/" + f.course() + "/units/-1/gate"), a.accessToken()).andExpect(status().isNotFound());
    }

    /** 403 unit_locked từ API bài học mang đủ courseId, topicId để web dẫn tới màn điều kiện mở. */
    @Test
    void lessonUnitLockedCarriesCourseAndTopic() throws Exception {
        var f = fixture();
        var a = member(f, "ACTIVE");
        long lessonB = jdbc.queryForObject("select id from lessons where topic_id = ?", Long.class, f.topicB());
        send(get("/api/lessons/" + lessonB), a.accessToken()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("unit_locked"))
                .andExpect(jsonPath("$.error.details.courseId").value(f.course()))
                .andExpect(jsonPath("$.error.details.topicId").value(f.topicB()))
                .andExpect(jsonPath("$.error.details.blockingTopicId").value(f.topicA()));
    }
}
