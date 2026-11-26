package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.ai.AiTestSupport;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

/** D-152: ưu tiên câu sai phải đúng trên cả Topic, không chỉ trên pool-limit câu có id nhỏ nhất (P0 dữ liệu lớn). */
@TestPropertySource(properties = "app.extra-practice.pool-limit=50")
class ExtraPracticePoolLimitTest extends AiTestSupport {

    private static final String MCQ = "CONNECTIVE_MCQ";

    private long course;
    private long level;

    private long newCourse(boolean sequential) {
        course = insert("courses", Map.of("slug", "ext-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED", "sequential", sequential));
        level = insert("course_levels", Map.of("course_id", course, "code", "A", "title", "Level A", "status", "PUBLISHED"));
        return course;
    }

    private long topic(String title, int order, boolean trial) {
        return insert("topics", Map.of("level_id", level, "title", title, "sort_order", order, "is_free_trial", trial, "status", "PUBLISHED"));
    }

    private long bank(long topic, String mode, String status) {
        return insert("question_banks", Map.of("topic_id", topic, "title", "Bộ", "is_trial", false, "status", status, "mode", mode));
    }

    private List<Long> questions(long bank, int n, String type) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ids.add(question(bank, type, i + 1, "Đề " + i, "{}", "{}"));
        }
        return ids;
    }

    private long topicWithQuestions(String title, int order, int n) {
        long t = topic(title, order, false);
        questions(bank(t, "PRACTICE", "PUBLISHED"), n, MCQ);
        return t;
    }

    private void enroll(Account u, OffsetDateTime expiresAt) {
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', ?, ?)",
                u.id(), course, OffsetDateTime.parse("2020-01-01T00:00:00Z"), expiresAt);
    }

    private void complete(Account u, long topic) {
        long bank = jdbc.queryForObject("select id from question_banks where topic_id = ? order by id limit 1", Long.class, topic);
        long q = jdbc.queryForObject("select id from questions where bank_id = ? order by id limit 1", Long.class, bank);
        long attempt = insert("student_exercise_attempts", Map.of("user_id", u.id(), "bank_id", bank,
                "started_at", OffsetDateTime.now(), "submitted_at", OffsetDateTime.now(), "score", 1, "max_score", 1));
        insert("student_answers", Map.of("attempt_id", attempt, "question_id", q, "is_correct", true, "credit", 1));
    }

    private ResultActions start(Account u, Map<String, Object> body) throws Exception {
        return postJson("/api/me/courses/" + course + "/extra-practice/attempts", u, body);
    }

    private long startOk(Account u, Map<String, Object> body) throws Exception {
        return idOf(start(u, body).andExpect(status().isCreated()), "id");
    }

    private Set<Long> attemptQuestions(long attempt) {
        return new HashSet<>(jdbc.queryForList("select question_id from attempt_questions where attempt_id = ?", Long.class, attempt));
    }

    @Test
    void wrongQuestionsBeyondThePoolLimitAreStillPreferred() throws Exception {
        newCourse(false);
        var u = student(0);
        enroll(u, null);
        long t = topic("Lớn", 1, false);
        var qs = questions(bank(t, "PRACTICE", "PUBLISHED"), 120, MCQ); // trần min(12, ceil(18)) = 12
        long other = bank(topic("Khác", 2, false), "PRACTICE", "PUBLISHED");
        long old = insert("student_exercise_attempts", Map.of("user_id", u.id(), "bank_id", other,
                "started_at", OffsetDateTime.now(), "submitted_at", OffsetDateTime.now(), "score", 0, "max_score", 1));
        var wrong = qs.subList(110, 115); // câu sai nằm sau 50 câu đầu theo id
        for (long q : wrong) {
            insert("attempt_question_checks", Map.of("attempt_id", old, "question_id", q, "wrong_count", 3));
        }
        long attempt = startOk(u, Map.of("scope", "CHALLENGE", "topicId", t));
        var picked = attemptQuestions(attempt);
        assertEquals(12, picked.size());
        assertTrue(picked.containsAll(wrong), "câu sai bị bỏ vì candidates 'order by q.id limit pool-limit': " + picked);
    }

    @Test
    void reviewDrawsFromAllCompletedTopicsNotOnlyTheOldestIds() throws Exception {
        newCourse(false);
        var u = student(0);
        enroll(u, null);
        long first = topicWithQuestions("Cũ", 1, 60);
        long second = topicWithQuestions("Mới", 2, 60);
        complete(u, first);
        complete(u, second);
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < 6; i++) {
            long attempt = startOk(u, Map.of("scope", "REVIEW", "kind", "AUTO"));
            seen.addAll(attemptQuestions(attempt));
            jdbc.update("update student_exercise_attempts set submitted_at = CURRENT_TIMESTAMP where id = ?", attempt);
        }
        long fromSecond = seen.stream().filter(id -> count("select count(*) from questions q join question_banks b on b.id = q.bank_id where q.id = ? and b.topic_id = ?", id, second) == 1).count();
        assertTrue(fromSecond > 0, "6 lượt Ôn lại (60 câu) không có câu nào của Topic thứ hai: chỉ lấy " + 50 + " câu id nhỏ nhất");
    }
}
