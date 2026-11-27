package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.ai.AiTestSupport;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** D-152 mục 5: câu Viết trong Luyện thêm trừ ví lượt định kỳ của khóa chứa câu, hết ví khóa thì trừ pool. */
class ExtraPracticeQuotaTest extends AiTestSupport {

    private static final String ESSAY = "I agree with online learning because it is flexible. However, students need discipline.";

    private long course;
    private long topic;
    private long bank;

    private void setup(Account u, int periodQuota) {
        course = insert("courses", Map.of("slug", "xq-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED",
                "ai_period", "WEEK", "ai_period_quota", periodQuota));
        long level = insert("course_levels", Map.of("course_id", course, "code", "A", "title", "Level A", "status", "PUBLISHED"));
        topic = insert("topics", Map.of("level_id", level, "title", "Topic", "status", "PUBLISHED"));
        bank = insert("question_banks", Map.of("topic_id", topic, "title", "Bộ", "is_trial", false, "status", "PUBLISHED", "mode", "PRACTICE"));
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', ?, null)",
                u.id(), course, OffsetDateTime.parse("2020-01-01T00:00:00Z"));
    }

    /** Mở lượt Luyện thêm có một câu Viết rồi nộp phiên Viết gắn lượt đó. */
    private void submitExtraWriting(Account u) throws Exception {
        long wq = writingQuestion(bank);
        long extra = idOf(postJson("/api/me/courses/" + course + "/extra-practice/attempts", u,
                Map.of("scope", "CHALLENGE", "topicId", topic, "kind", "WRITING")).andExpect(status().isCreated()), "id");
        guard.resetRateLimit(u.id());
        long session = idOf(postJson("/api/writing/sessions", u, Map.of("questionId", wq, "attemptId", extra)).andExpect(status().isCreated()), "sessionId");
        guard.resetRateLimit(u.id());
        postJson("/api/writing/sessions/" + session + "/submit", u, Map.of("finalText", ESSAY), UUID.randomUUID().toString()).andExpect(status().isAccepted());
    }

    private int used(Account u) {
        return count("select coalesce(sum(used), 0) from ai_period_usage where user_id = ? and course_id = ?", u.id(), course);
    }

    @Test
    void extraWritingChargesTheCourseWalletBeforeThePool() throws Exception {
        var u = student(3);
        setup(u, 5);
        submitExtraWriting(u);
        assertEquals(1, used(u));
        assertEquals(3, balance(u));
    }

    @Test
    void extraWritingFallsBackToPoolWhenCourseWalletIsEmpty() throws Exception {
        var u = student(3);
        setup(u, 0);
        submitExtraWriting(u);
        assertEquals(0, used(u));
        assertEquals(2, balance(u));
    }
}
