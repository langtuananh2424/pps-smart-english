package com.pps.smartenglish.home;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.ai.AiTestSupport;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** D-62, D-63: GET /api/me/courses/{id}/exercise-stats và số câu trong GET /api/me/courses. */
class ExerciseStatsApiTest extends AiTestSupport {

    /** Khóa: topic học thử; bộ luyện có 2 câu MCQ + 1 câu BUILD; bộ thi có 1 câu MCQ. */
    private record World(long course, long topic, long mcq1, long mcq2, long build, long examQ) {}

    private World world() {
        long c = insert("courses", Map.of("slug", "st-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "A", "status", "PUBLISHED"));
        long t = insert("topics", Map.of("level_id", lv, "title", "T", "is_free_trial", true, "status", "PUBLISHED"));
        long practice = bank(t, "PRACTICE");
        long exam = bank(t, "EXAM");
        return new World(c, t, question(practice, "CONNECTIVE_MCQ", 1, "a", "{}", "{}"),
                question(practice, "CONNECTIVE_MCQ", 2, "b", "{}", "{}"), question(practice, "GRAMMAR_BUILD", 3, "c", "{}", "{}"),
                question(exam, "CONNECTIVE_MCQ", 1, "d", "{}", "{}"));
    }

    private long bank(long topic, String mode) {
        return insert("question_banks", Map.of("topic_id", topic, "title", mode, "is_trial", false, "status", "PUBLISHED", "mode", mode));
    }

    private void enroll(Account u, long course) {
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at) values (?, ?, 'ACTIVE', ?)",
                u.id(), course, OffsetDateTime.now().minusDays(1));
    }

    /** Một lượt trên bộ; mỗi phần tử: {questionId, correct (null = chưa chấm)}. */
    private void attempt(Account u, long bank, boolean submitted, Object[]... answers) {
        jdbc.update("insert into student_exercise_attempts (user_id, bank_id, submitted_at) values (?, ?, ?)", u.id(), bank,
                submitted ? OffsetDateTime.now() : null);
        long id = jdbc.queryForObject("select max(id) from student_exercise_attempts", Long.class);
        for (Object[] a : answers) {
            jdbc.update("insert into student_answers (attempt_id, question_id, is_correct) values (?, ?, ?)", id, a[0], a[1]);
        }
    }

    private long bankOf(long question) {
        return jdbc.queryForObject("select bank_id from questions where id = ?", Long.class, question);
    }

    private JsonNode stats(Account u, long course) throws Exception {
        return json.readTree(getJson("/api/me/courses/" + course + "/exercise-stats", u).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString());
    }

    private JsonNode myCourses(Account u) throws Exception {
        return json.readTree(getJson("/api/me/courses", u).andReturn().getResponse().getContentAsString());
    }

    @Test
    void emptyStatsWhenNoAttempts() throws Exception {
        var w = world();
        var u = student(0);
        enroll(u, w.course());
        var n = stats(u, w.course());
        assertEquals(0, n.get("answered").asInt());
        assertEquals(0, n.get("correct").asInt());
        assertTrue(n.get("accuracy").isNull());
        assertEquals(0, n.get("typesPracticed").asInt());
        assertEquals(2, n.get("byType").size()); // bộ EXAM không thêm dòng
        assertEquals("CONNECTIVE_MCQ", n.at("/byType/0/type").asText());
        assertEquals(2, n.at("/byType/0/questionCount").asInt());
        assertTrue(n.at("/byType/0/accuracy").isNull());
        assertTrue(n.get("weakest").isNull());
        assertTrue(n.get("band").isNull());
        assertEquals(0, myCourses(u).get(0).get("answeredQuestions").asInt());
        assertEquals(0, myCourses(u).get(0).get("correctQuestions").asInt());
    }

    @Test
    void countsSubmittedPracticeAnswersOnly() throws Exception {
        var w = world();
        var u = student(0);
        var other = student(0);
        enroll(u, w.course());
        long pb = bankOf(w.mcq1());
        attempt(u, pb, true, new Object[] {w.mcq1(), true}, new Object[] {w.mcq2(), false}, new Object[] {w.build(), true});
        attempt(u, pb, true, new Object[] {w.mcq1(), true}, new Object[] {w.build(), null}); // chưa chấm: đã làm, chưa đúng
        attempt(u, pb, false, new Object[] {w.mcq1(), true}); // chưa nộp: bỏ
        attempt(u, bankOf(w.examQ()), true, new Object[] {w.examQ(), true}); // bộ EXAM: bỏ
        attempt(other, pb, true, new Object[] {w.mcq1(), true}); // người khác: bỏ

        var n = stats(u, w.course());
        assertEquals(5, n.get("answered").asInt());
        assertEquals(3, n.get("correct").asInt());
        assertEquals(60, n.get("accuracy").asInt());
        assertEquals(2, n.get("typesPracticed").asInt());
        assertEquals(3, n.at("/byType/0/answered").asInt());
        assertEquals(2, n.at("/byType/0/correct").asInt());
        assertEquals(67, n.at("/byType/0/accuracy").asInt());
        assertEquals("GRAMMAR_BUILD", n.at("/byType/1/type").asText());
        assertEquals(1, n.at("/byType/1/questionCount").asInt());
        assertEquals(50, n.at("/byType/1/accuracy").asInt());
        assertTrue(n.get("weakest").isNull()); // chưa dạng nào đủ 10 câu

        assertEquals(5, myCourses(u).get(0).get("answeredQuestions").asInt());
        assertEquals(3, myCourses(u).get(0).get("correctQuestions").asInt());
    }

    @Test
    void weakestIsLowestAccuracyAmongTypesWithEnoughAnswers() throws Exception {
        var w = world();
        var u = student(0);
        enroll(u, w.course());
        long pb = bankOf(w.mcq1());
        for (int i = 0; i < 10; i++) {
            // MCQ đúng 8/10, BUILD đúng 5/10: BUILD thấp nhất
            attempt(u, pb, true, new Object[] {w.mcq1(), i < 8}, new Object[] {w.build(), i < 5});
        }
        var n = stats(u, w.course());
        assertEquals("GRAMMAR_BUILD", n.at("/weakest/type").asText());
        assertEquals(50, n.at("/weakest/accuracy").asInt());
        assertEquals(10, n.at("/weakest/answered").asInt());

        // BUILD còn 9 câu thì chưa đủ ngưỡng: còn lại MCQ
        jdbc.update("delete from student_answers where id = (select max(id) from student_answers where question_id = ?)", w.build());
        assertEquals("CONNECTIVE_MCQ", stats(u, w.course()).at("/weakest/type").asText());
    }

    @Test
    void bandIsLatestPlacementBand() throws Exception {
        var w = world();
        var u = student(0);
        enroll(u, w.course());
        long pb = insert("question_banks", Map.of("title", "Đầu vào", "status", "DRAFT", "mode", "EXAM", "purpose", "PLACEMENT",
                "time_limit_sec", 600));
        placement(u, pb, 1, "4.5");
        placement(u, pb, 0, "5.5");
        var band = stats(u, w.course()).get("band");
        assertEquals(5.5, band.get("value").asDouble());
        assertEquals("PLACEMENT", band.get("source").asText());
        assertTrue(band.get("at").isTextual());
    }

    private void placement(Account u, long bank, int daysAgo, String band) {
        var at = OffsetDateTime.now().minusDays(daysAgo);
        jdbc.update("insert into placement_attempts (bank_id, user_id, started_at, time_limit_sec, deadline_at, submitted_at, overall_band)"
                + " values (?, ?, ?, 600, ?, ?, ?)", bank, u.id(), at.minusMinutes(5), at, at, new BigDecimal(band));
    }

    @Test
    void trialUserGetsStatsAndStrangerGets403() throws Exception {
        var w = world();
        var trial = student(0);
        var stranger = student(0);
        long lesson = insert("lessons", Map.of("topic_id", w.topic(), "type", "CONNECTIVE_VIDEO", "title", "B", "status", "PUBLISHED",
                "video_source", "YOUTUBE", "video_ref", "abc", "duration_sec", 10));
        jdbc.update("insert into lesson_progress (user_id, lesson_id, watched_sec, max_position_sec) values (?, ?, 5, 5)", trial.id(), lesson);
        stats(trial, w.course());
        getJson("/api/me/courses/" + w.course() + "/exercise-stats", stranger).andExpect(status().isForbidden());
    }

    @Test
    void unknownDraftOrMalformedCourseIs404() throws Exception {
        var u = student(0);
        long draft = insert("courses", Map.of("slug", "d-" + UUID.randomUUID(), "title", "Nháp", "status", "DRAFT"));
        getJson("/api/me/courses/" + draft + "/exercise-stats", u).andExpect(status().isNotFound());
        getJson("/api/me/courses/999999999/exercise-stats", u).andExpect(status().isNotFound());
        getJson("/api/me/courses/abc/exercise-stats", u).andExpect(status().isNotFound());
    }
}
