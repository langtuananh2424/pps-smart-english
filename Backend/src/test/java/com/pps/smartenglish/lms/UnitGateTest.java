package com.pps.smartenglish.lms;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.lms.service.EarlyUnlocks;
import com.pps.smartenglish.support.ApiTestBase;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** FR-LMS-04, D-122, D-124: GET /api/me/courses/{courseId}/units/{topicId}/gate. */
class UnitGateTest extends ApiTestBase {

    @MockitoBean EarlyUnlocks early;

    private record Course(long id, long topicA, long topicB, long lessonA1, long lessonA2, long practiceA, long testA) {}

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private long lesson(long topic, int order, int duration) {
        return insert("lessons", Map.of("topic_id", topic, "type", "CONNECTIVE_VIDEO", "title", "Bài " + order, "status", "PUBLISHED",
                "video_source", "YOUTUBE", "video_ref", "abc", "duration_sec", duration, "sort_order", order));
    }

    private long bank(long topic, String mode) {
        long b = insert("question_banks", Map.of("topic_id", topic, "title", mode + topic, "is_trial", false, "status", "PUBLISHED", "mode", mode));
        insert("questions", Map.of("bank_id", b, "type", "CONNECTIVE_MCQ", "prompt", "Câu hỏi"));
        return b;
    }

    /** Unit A: 2 bài (60s), 1 bộ PRACTICE, bộ kiểm tra chương; unit B: 1 bài. */
    private Course course(boolean sequential) {
        long c = insert("courses", Map.of("slug", "gate-" + UUID.randomUUID(), "title", "Khóa gate", "status", "PUBLISHED", "sequential", sequential));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED", "sort_order", 0));
        long ta = insert("topics", Map.of("level_id", lv, "title", "Unit A", "is_free_trial", false, "status", "PUBLISHED", "sort_order", 0));
        long tb = insert("topics", Map.of("level_id", lv, "title", "Unit B", "is_free_trial", false, "status", "PUBLISHED", "sort_order", 1));
        long l1 = lesson(ta, 0, 60);
        long l2 = lesson(ta, 1, 60);
        lesson(tb, 0, 60);
        long practice = bank(ta, "PRACTICE");
        long test = bank(ta, "EXAM");
        jdbc.update("update topics set chapter_test_bank_id = ? where id = ?", test, ta);
        return new Course(c, ta, tb, l1, l2, practice, test);
    }

    private String url(long courseId, long topicId) {
        return "/api/me/courses/" + courseId + "/units/" + topicId + "/gate";
    }

    @Test
    void nonSequentialCourseIsNeverLocked() throws Exception {
        var c = course(false);
        var u = register(newEmail(), "Password123!");
        send(get(url(c.id(), c.topicB())), u.accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(false)).andExpect(jsonPath("$.blockingTopicId").doesNotExist())
                .andExpect(jsonPath("$.lectures").doesNotExist());
    }

    @Test
    void firstUnitIsOpenEvenWhenSequential() throws Exception {
        var c = course(true);
        var u = register(newEmail(), "Password123!");
        send(get(url(c.id(), c.topicA())), u.accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.topicId").value(c.topicA())).andExpect(jsonPath("$.locked").value(false))
                .andExpect(jsonPath("$.openedEarly").value(false));
    }

    @Test
    void lockedUnitReportsBlockingUnitProgress() throws Exception {
        var c = course(true);
        var u = register(newEmail(), "Password123!");
        // bài 1 hoàn thành, bài 2 xem tới giây 30/60, chưa nộp bài tập, chưa nộp kiểm tra chương
        jdbc.update("insert into lesson_progress (user_id, lesson_id, watched_sec, max_position_sec, completed_at) values (?, ?, 60, 60, CURRENT_TIMESTAMP)",
                u.id(), c.lessonA1());
        jdbc.update("insert into lesson_progress (user_id, lesson_id, watched_sec, max_position_sec) values (?, ?, 30, 30)", u.id(), c.lessonA2());

        send(get(url(c.id(), c.topicB())), u.accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(true)).andExpect(jsonPath("$.openedEarly").value(false))
                .andExpect(jsonPath("$.blockingTopicId").value(c.topicA())).andExpect(jsonPath("$.blockingTitle").value("Unit A"))
                .andExpect(jsonPath("$.lectures.completed").value(1)).andExpect(jsonPath("$.lectures.total").value(2))
                .andExpect(jsonPath("$.lectures.requiredPercent").value(80)).andExpect(jsonPath("$.lectures.currentPercent").value(50))
                .andExpect(jsonPath("$.exercises.completed").value(0)).andExpect(jsonPath("$.exercises.total").value(1))
                .andExpect(jsonPath("$.chapterTest.submitted").value(false));

        jdbc.update("insert into student_exercise_attempts (user_id, bank_id, submitted_at, score, max_score) values (?, ?, CURRENT_TIMESTAMP, 0, 1)",
                u.id(), c.practiceA());
        jdbc.update("insert into student_exercise_attempts (user_id, bank_id, submitted_at, score, max_score) values (?, ?, CURRENT_TIMESTAMP, 0, 1)",
                u.id(), c.testA());
        send(get(url(c.id(), c.topicB())), u.accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(true)) // bài 2 vẫn chưa hoàn thành
                .andExpect(jsonPath("$.exercises.completed").value(1)).andExpect(jsonPath("$.chapterTest.submitted").value(true));
    }

    @Test
    void earlyOpenedUnitIsNotLockedButFlagged() throws Exception {
        var c = course(true);
        var u = register(newEmail(), "Password123!");
        when(early.openedTopics(anyLong(), anyLong())).thenReturn(Set.of(c.topicB()));
        send(get(url(c.id(), c.topicB())), u.accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(false)).andExpect(jsonPath("$.openedEarly").value(true))
                .andExpect(jsonPath("$.blockingTopicId").doesNotExist());
    }

    @Test
    void unitOfAnotherCourseOrUnknownIs404AndGuestIs401() throws Exception {
        var c = course(true);
        var other = course(true);
        var u = register(newEmail(), "Password123!");
        send(get(url(c.id(), other.topicA())), u.accessToken()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("unit_not_found"));
        send(get(url(c.id(), 999999L)), u.accessToken()).andExpect(status().isNotFound());
        send(get(url(999999L, c.topicA())), u.accessToken()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("course_not_found"));
        mvc.perform(get(url(c.id(), c.topicA()))).andExpect(status().isUnauthorized());
    }

    @Test
    void draftCourseIs404() throws Exception {
        var c = course(true);
        jdbc.update("update courses set status = 'DRAFT' where id = ?", c.id());
        var u = register(newEmail(), "Password123!");
        send(get(url(c.id(), c.topicA())), u.accessToken()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("course_not_found"));
    }
}
