package com.pps.smartenglish.home;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.ai.AiTestSupport;
import com.pps.smartenglish.support.MutableClockConfig;
import com.pps.smartenglish.support.MutableClockConfig.MutableClock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** Issue #52: các API đọc của Trang chủ (D-51, D-52, D-53). */
@Import(MutableClockConfig.class)
class HomeApiTest extends AiTestSupport {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired MutableClock clock;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    /** Khóa 1 level, 2 topic: T1 học thử (L1 20 s, L2 100 s), T2 trả phí (L3, L4). */
    private record World(long course, String slug, long level, long t1, long t2, long l1, long l2, long l3, long l4,
                         long lessonBank, long topicBank, long examBank, long writingQ) {}

    private long lesson(long topic, String title, int order, int duration, String status) {
        var cols = new HashMap<String, Object>(Map.of("topic_id", topic, "type", "CONNECTIVE_VIDEO", "title", title,
                "status", status, "video_source", "YOUTUBE", "video_ref", "abc", "duration_sec", duration));
        cols.put("sort_order", order);
        return insert("lessons", cols);
    }

    private long bank(long topic, Long lessonId, String title, String mode) {
        var cols = new HashMap<String, Object>(Map.of("topic_id", topic, "title", title, "is_trial", false,
                "status", "PUBLISHED", "mode", mode));
        cols.put("lesson_id", lessonId);
        return insert("question_banks", cols);
    }

    private World world() {
        String slug = "home-" + UUID.randomUUID();
        long c = insert("courses", Map.of("slug", slug, "title", "Khóa " + slug, "status", "PUBLISHED", "target_type", "IELTS"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED"));
        long t1 = insert("topics", Map.of("level_id", lv, "title", "Topic 1", "is_free_trial", true, "status", "PUBLISHED",
                "sort_order", 1));
        long t2 = insert("topics", Map.of("level_id", lv, "title", "Topic 2", "is_free_trial", false, "status", "PUBLISHED",
                "sort_order", 2));
        long l1 = lesson(t1, "Bài 1", 1, 20, "PUBLISHED");
        long l2 = lesson(t1, "Bài 2", 2, 100, "PUBLISHED");
        lesson(t1, "Bài nháp", 3, 100, "DRAFT");
        long l3 = lesson(t2, "Bài 3", 1, 100, "PUBLISHED");
        long l4 = lesson(t2, "Bài 4", 2, 100, "PUBLISHED");
        long b1 = bank(t1, l1, "Bài tập Bài 1", "PRACTICE");
        question(b1, "CONNECTIVE_MCQ", 1, "p1", "{}", "{}");
        question(b1, "CONNECTIVE_MCQ", 2, "p2", "{}", "{}");
        long b2 = bank(t1, null, "Luyện viết Topic 1", "PRACTICE");
        long w = writingQuestion(b2);
        long exam = bank(t1, null, "Đề thi", "EXAM");
        question(exam, "CONNECTIVE_MCQ", 1, "p", "{}", "{}");
        return new World(c, slug, lv, t1, t2, l1, l2, l3, l4, b1, b2, exam, w);
    }

    private void enroll(Account u, long course, OffsetDateTime expires) {
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', ?, ?)",
                u.id(), course, OffsetDateTime.now().minusDays(1), expires);
    }

    private void completeLesson(Account u, long lesson, int watched) {
        jdbc.update("insert into lesson_progress (user_id, lesson_id, watched_sec, max_position_sec, completed_at) values (?, ?, ?, ?, CURRENT_TIMESTAMP)",
                u.id(), lesson, watched, watched);
    }

    private void xp(Account u, String source, long ref, int amount) {
        jdbc.update("insert into xp_events (user_id, source, ref_id, xp) values (?, ?, ?, ?)", u.id(), source, ref, amount);
    }

    private JsonNode body(String url, Account u) throws Exception {
        var res = getJson(url, u).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        System.out.println("GET " + url + " -> " + res);
        return json.readTree(res);
    }

    private void activeToday(Account u) {
        jdbc.update("update user_profiles set last_active_date = ?, streak_current = 2 where user_id = ?",
                LocalDate.now(VN), u.id());
    }

    // ---------- GET /api/me/courses ----------

    @Test
    void myCoursesSortedByLastActivityAndExpiredStatus() throws Exception {
        var w1 = world();
        var w2 = world();
        var w3 = world();
        var u = student(0);
        var other = student(0);
        enroll(u, w1.course(), OffsetDateTime.now().plusDays(30));
        enroll(u, w2.course(), OffsetDateTime.now().minusDays(2)); // hết hạn
        enroll(u, w3.course(), null); // chưa học gì
        completeLesson(u, w1.l1(), 20);
        completeLesson(u, w2.l1(), 20);
        jdbc.update("update lesson_progress set updated_at = ? where user_id = ? and lesson_id = ?",
                OffsetDateTime.now().minusDays(5), u.id(), w1.l1());
        jdbc.update("update lesson_progress set updated_at = ? where user_id = ? and lesson_id = ?",
                OffsetDateTime.now().minusDays(1), u.id(), w2.l1());

        var n = body("/api/me/courses", u);
        assertEquals(3, n.size());
        assertEquals(w2.course(), n.get(0).get("courseId").asLong()); // học gần nhất, dù hết hạn
        assertEquals("EXPIRED", n.get(0).get("status").asText());
        assertEquals(w1.course(), n.get(1).get("courseId").asLong());
        assertEquals("ACTIVE", n.get(1).get("status").asText());
        assertEquals(w3.course(), n.get(2).get("courseId").asLong());
        assertTrue(n.get(2).get("lastActivityAt").isNull());
        assertEquals("IELTS", n.get(0).get("targetType").asText());

        assertEquals(0, body("/api/me/courses", other).size()); // người khác không thấy
    }

    @Test
    void attemptAlsoCountsAsCourseActivity() throws Exception {
        var w1 = world();
        var w2 = world();
        var u = student(0);
        enroll(u, w1.course(), null);
        enroll(u, w2.course(), null);
        completeLesson(u, w1.l1(), 20);
        jdbc.update("update lesson_progress set updated_at = ? where user_id = ?", OffsetDateTime.now().minusDays(3), u.id());
        jdbc.update("insert into student_exercise_attempts (user_id, bank_id, submitted_at) values (?, ?, CURRENT_TIMESTAMP)",
                u.id(), w2.lessonBank());
        assertEquals(w2.course(), body("/api/me/courses", u).get(0).get("courseId").asLong());
    }

    // ---------- GET /api/me/courses/{id}/roadmap ----------

    @Test
    void roadmapResumePicksMostRecentUnfinishedLessonAndReturnsPosition() throws Exception {
        var w = world();
        var u = student(0);
        enroll(u, w.course(), null);
        for (long l : new long[] {w.l2(), w.l4()}) {
            jdbc.update("insert into lesson_progress (user_id, lesson_id, watched_sec, max_position_sec, updated_at) values (?, ?, 30, 45, ?)",
                    u.id(), l, OffsetDateTime.now().minusDays(l == w.l2() ? 3 : 1));
        }
        var n = body("/api/me/courses/" + w.course() + "/roadmap", u);
        assertEquals(w.l4(), n.at("/resume/lessonId").asLong()); // xem gần nhất, dù l1 chưa xem đứng trước
        assertEquals(45, n.at("/resume/positionSec").asInt());
        assertEquals(100, n.at("/resume/durationSec").asInt());
        assertEquals(45, n.at("/levels/0/topics/1/lessons/1/maxPositionSec").asInt());
        assertFalse(n.at("/levels/0/topics/1/lessons/1/lastWatchedAt").isNull());
        assertTrue(n.at("/levels/0/topics/0/lessons/0/lastWatchedAt").isNull());
        jdbc.update("update topics set skill = 'READING' where id = ?", w.t1());
        assertEquals("READING", body("/api/me/courses/" + w.course() + "/roadmap", u).at("/levels/0/topics/0/skill").asText());
    }

    @Test
    void trialCourseAppearsOnlyAfterTrialProgressAndCountsLessons() throws Exception {
        var w = world();
        var u = student(0);
        assertEquals(0, body("/api/me/courses", u).size());
        jdbc.update("insert into lesson_progress (user_id, lesson_id, watched_sec, max_position_sec) values (?, ?, 5, 5)", u.id(), w.l3());
        assertEquals(0, body("/api/me/courses", u).size()); // topic trả phí không tính
        completeLesson(u, w.l1(), 20);
        var n = body("/api/me/courses", u);
        assertEquals(1, n.size());
        assertEquals("TRIAL", n.get(0).get("status").asText());
        assertEquals(1, n.get(0).get("completedLessons").asInt());
        assertEquals(4, n.get(0).get("totalLessons").asInt()); // bài nháp không tính
        enroll(u, w.course(), null);
        assertEquals("ACTIVE", body("/api/me/courses", u).get(0).get("status").asText());
    }

    @Test
    void roadmapOpensTrialTopicAndLocksPaidOneUntilPurchase() throws Exception {
        var w = world();
        var u = student(0);

        var n = body("/api/me/courses/" + w.course() + "/roadmap", u);
        var topics = n.at("/levels/0/topics");
        assertEquals("A", n.at("/levels/0/code").asText());
        assertFalse(topics.get(0).get("locked").asBoolean());
        assertTrue(topics.get(0).get("isFreeTrial").asBoolean());
        assertEquals("NOT_STARTED", topics.get(0).get("status").asText());
        assertTrue(topics.get(1).get("locked").asBoolean());
        assertEquals("LOCKED", topics.get(1).get("status").asText());
        assertEquals(2, topics.get(0).get("totalLessons").asInt()); // bài nháp không tính
        assertEquals(2, topics.get(0).get("lessons").get(1).get("order").asInt());
        assertEquals(w.l1(), n.at("/resume/lessonId").asLong()); // phần được học chỉ có topic thử

        enroll(u, w.course(), OffsetDateTime.now().plusDays(30));
        n = body("/api/me/courses/" + w.course() + "/roadmap", u);
        assertFalse(n.at("/levels/0/topics/1/locked").asBoolean());
        assertEquals("NOT_STARTED", n.at("/levels/0/topics/1/status").asText());
    }

    @Test
    void roadmapResumePointsAtFirstUnfinishedLessonAndStatusFollowsProgress() throws Exception {
        var w = world();
        var u = student(0);
        enroll(u, w.course(), null);
        completeLesson(u, w.l1(), 20);

        var n = body("/api/me/courses/" + w.course() + "/roadmap", u);
        assertEquals(w.l2(), n.at("/resume/lessonId").asLong());
        assertEquals("Bài 2", n.at("/resume/lessonTitle").asText());
        assertEquals(2, n.at("/resume/lessonOrder").asInt());
        assertEquals(1, n.at("/resume/completedInTopic").asInt());
        assertEquals(2, n.at("/resume/totalInTopic").asInt());
        assertEquals("Topic 1", n.at("/resume/topicTitle").asText());
        assertEquals("A", n.at("/resume/levelCode").asText());
        assertEquals("IN_PROGRESS", n.at("/levels/0/topics/0/status").asText());
        assertTrue(n.at("/levels/0/topics/0/lessons/0/completed").asBoolean());
        assertEquals(20, n.at("/levels/0/topics/0/lessons/0/watchedSec").asInt());

        completeLesson(u, w.l2(), 100);
        n = body("/api/me/courses/" + w.course() + "/roadmap", u);
        assertEquals("DONE", n.at("/levels/0/topics/0/status").asText());
        assertEquals(w.l3(), n.at("/resume/lessonId").asLong()); // sang topic kế tiếp

        completeLesson(u, w.l3(), 100);
        completeLesson(u, w.l4(), 100);
        assertTrue(body("/api/me/courses/" + w.course() + "/roadmap", u).get("resume").isNull()); // học xong hết
    }

    @Test
    void roadmapSumsEarnedXpPerTopicForCurrentUserOnly() throws Exception {
        var w = world();
        var u = student(0);
        var other = student(0);
        enroll(u, w.course(), null);
        xp(u, "LESSON", w.l1(), 10);
        xp(u, "LESSON", w.l3(), 10);
        xp(u, "EXERCISE", w.lessonBank(), 10);
        xp(u, "EXERCISE_HIGH", w.lessonBank(), 10);
        xp(u, "EXERCISE", w.examBank(), 10); // bộ của topic nhưng không hiển thị: vẫn tính XP
        xp(u, "LESSON", w.l2() + 100000, 99); // ref lạ không thuộc khóa
        xp(other, "LESSON", w.l1(), 500);

        var n = body("/api/me/courses/" + w.course() + "/roadmap", u);
        assertEquals(40, n.at("/levels/0/topics/0/xpEarned").asInt());
        assertEquals(10, n.at("/levels/0/topics/1/xpEarned").asInt());
        assertEquals(500, body("/api/me/courses/" + w.course() + "/roadmap", other).at("/levels/0/topics/0/xpEarned").asInt());
    }

    @Test
    void roadmapListsPracticeBanksWithQuestionCountOnly() throws Exception {
        var w = world();
        var u = student(0);
        var banks = body("/api/me/courses/" + w.course() + "/roadmap", u).at("/levels/0/topics/0/banks");
        assertEquals(2, banks.size()); // không có bộ EXAM
        assertEquals(w.lessonBank(), banks.get(0).get("id").asLong());
        assertEquals(w.l1(), banks.get(0).get("lessonId").asLong());
        assertEquals(2, banks.get(0).get("questionCount").asInt());
        assertTrue(banks.get(1).get("lessonId").isNull());
        assertEquals(1, banks.get(1).get("questionCount").asInt());
    }

    @Test
    void roadmapReturns404ForUnknownDraftOrMalformedCourse() throws Exception {
        var u = student(0);
        long draft = insert("courses", Map.of("slug", "draft-" + UUID.randomUUID(), "title", "Nháp", "status", "DRAFT"));
        getJson("/api/me/courses/" + draft + "/roadmap", u).andExpect(status().isNotFound());
        getJson("/api/me/courses/999999999/roadmap", u).andExpect(status().isNotFound());
        getJson("/api/me/courses/abc/roadmap", u).andExpect(status().isNotFound());
        mvc.perform(get("/api/me/courses/" + draft + "/roadmap")).andExpect(status().isUnauthorized());
    }

    // ---------- GET /api/me/today ----------

    @Test
    void todayListsVideoExerciseAndWritingThenKeepsVideoAfterCompletion() throws Exception {
        var w = world();
        var u = student(0);
        enroll(u, w.course(), null);

        var n = body("/api/me/today", u);
        assertEquals(w.course(), n.get("courseId").asLong());
        assertEquals(3, n.get("total").asInt());
        assertEquals(0, n.get("doneCount").asInt());
        var tasks = n.get("tasks");
        assertEquals("VIDEO", tasks.get(0).get("kind").asText());
        assertEquals(w.l1(), tasks.get(0).get("lessonId").asLong());
        assertEquals(20, tasks.get(0).get("remainingSec").asInt());
        assertEquals("EXERCISE", tasks.get(1).get("kind").asText());
        assertEquals(w.lessonBank(), tasks.get(1).get("bankId").asLong());
        assertEquals(2, tasks.get(1).get("questionCount").asInt());
        assertEquals("WRITING", tasks.get(2).get("kind").asText());
        assertEquals(w.writingQ(), tasks.get(2).get("questionId").asLong());
        assertFalse(tasks.get(2).get("doneToday").asBoolean());

        // xem xong bài 1 hôm nay: vẫn là việc đầu, đã xong, không nhảy sang bài 2
        postJson("/api/lessons/" + w.l1() + "/progress", u, Map.of("positionSec", 20, "watchedDeltaSec", 20)).andExpect(status().isOk());
        n = body("/api/me/today?courseId=" + w.course(), u);
        assertEquals(w.l1(), n.at("/tasks/0/lessonId").asLong());
        assertTrue(n.at("/tasks/0/doneToday").asBoolean());
        assertEquals(0, n.at("/tasks/0/remainingSec").asInt());
        assertEquals(1, n.get("doneCount").asInt());
        assertEquals(3, n.get("total").asInt());

        // nộp bộ bài tập hôm nay: việc vẫn còn trong danh sách, đã xong
        jdbc.update("insert into student_exercise_attempts (user_id, bank_id, submitted_at) values (?, ?, CURRENT_TIMESTAMP)",
                u.id(), w.lessonBank());
        n = body("/api/me/today", u);
        assertEquals(w.lessonBank(), n.at("/tasks/1/bankId").asLong());
        assertTrue(n.at("/tasks/1/doneToday").asBoolean());
        assertEquals(2, n.get("doneCount").asInt());

        // writing session nộp hôm nay
        jdbc.update("insert into writing_sessions (user_id, question_id, turns, status) values (?, ?, '[]' format json, 'SUBMITTED')",
                u.id(), w.writingQ());
        n = body("/api/me/today", u);
        assertTrue(n.at("/tasks/2/doneToday").asBoolean());
        assertEquals(3, n.get("doneCount").asInt());
    }

    @Test
    void todayMovesOnWhenWorkWasFinishedOnAnEarlierDay() throws Exception {
        var w = world();
        var u = student(0);
        enroll(u, w.course(), null);
        completeLesson(u, w.l1(), 20);
        jdbc.update("update lesson_progress set completed_at = ? where user_id = ?", OffsetDateTime.now().minusDays(2), u.id());
        jdbc.update("insert into student_exercise_attempts (user_id, bank_id, submitted_at) values (?, ?, ?)",
                u.id(), w.lessonBank(), OffsetDateTime.now().minusDays(2));

        var n = body("/api/me/today", u);
        assertEquals(w.l2(), n.at("/tasks/0/lessonId").asLong()); // bài kế tiếp
        assertFalse(n.at("/tasks/0/doneToday").asBoolean());
        assertEquals("EXERCISE", n.at("/tasks/1/kind").asText()); // bộ topic chưa nộp, vì bộ của bài 2 không có
        assertEquals(w.topicBank(), n.at("/tasks/1/bankId").asLong());
        assertEquals(0, n.get("doneCount").asInt());
    }

    @Test
    void todayIsEmptyWithoutCourseAndPerUser() throws Exception {
        var u = student(0);
        var n = body("/api/me/today", u);
        assertTrue(n.get("courseId").isNull());
        assertEquals(0, n.get("tasks").size());
        assertEquals(0, n.get("total").asInt());

        var w = world();
        var learner = student(0);
        enroll(learner, w.course(), null);
        postJson("/api/lessons/" + w.l1() + "/progress", learner, Map.of("positionSec", 20, "watchedDeltaSec", 20));
        // người khác xem cùng khóa (học thử) không thấy tiến độ của learner
        n = body("/api/me/today?courseId=" + w.course(), u);
        assertEquals(w.l1(), n.at("/tasks/0/lessonId").asLong());
        assertFalse(n.at("/tasks/0/doneToday").asBoolean());
        getJson("/api/me/today?courseId=999999999", u).andExpect(status().isNotFound());
        getJson("/api/me/today?courseId=abc", u).andExpect(status().isNotFound());
    }

    // ---------- GET /api/me/stats weekDays ----------

    @Test
    void statsWeekDaysCoverOnlyCurrentWeekMondayToSunday() throws Exception {
        clock.set(Instant.parse("2026-11-18T05:00:00Z")); // thứ Tư 18/11 12:00 giờ VN
        var u = student(0);
        for (String d : new String[] {"2026-11-15", "2026-11-16", "2026-11-18", "2026-11-22", "2026-11-23"}) {
            jdbc.update("insert into activity_days (user_id, activity_date) values (?, ?)", u.id(), LocalDate.parse(d));
        }
        var days = body("/api/me/stats", u).get("weekDays");
        assertEquals(3, days.size());
        assertEquals("2026-11-16", days.get(0).asText());
        assertEquals("2026-11-18", days.get(1).asText());
        assertEquals("2026-11-22", days.get(2).asText());

        var fresh = student(0);
        assertEquals(0, body("/api/me/stats", fresh).get("weekDays").size());
        assertEquals(0, body("/api/me/stats", fresh).get("xp").asInt()); // trường cũ vẫn còn
    }

    // ---------- GET /api/me/notifications/unread-count ----------

    @Test
    void unreadCountIgnoresReadAndOtherUsers() throws Exception {
        var u = student(0);
        var other = student(0);
        assertEquals(0, body("/api/me/notifications/unread-count", u).get("count").asInt());
        for (int i = 0; i < 3; i++) {
            jdbc.update("insert into notifications (user_id, type, payload, dedupe_key) values (?, 'STREAK_REMINDER', '{}' format json, ?)",
                    u.id(), "k" + i);
        }
        jdbc.update("insert into notifications (user_id, type, payload, dedupe_key) values (?, 'STREAK_REMINDER', '{}' format json, 'o')",
                other.id());
        jdbc.update("update notifications set read_at = CURRENT_TIMESTAMP where user_id = ? and dedupe_key = 'k0'", u.id());
        assertEquals(2, body("/api/me/notifications/unread-count", u).get("count").asInt());
        assertEquals(1, body("/api/me/notifications/unread-count", other).get("count").asInt());
        mvc.perform(get("/api/me/notifications/unread-count")).andExpect(status().isUnauthorized());
    }

    // ---------- GET /api/lessons/{id}/progress ----------

    @Test
    void lessonProgressReturnsOwnProgressZeroWhenNone() throws Exception {
        var w = world();
        var u = student(0);
        var other = student(0);
        var n = body("/api/lessons/" + w.l1() + "/progress", u);
        assertEquals(0, n.get("watchedSec").asInt());
        assertFalse(n.get("completed").asBoolean());
        assertTrue(n.get("completedAt").isNull());

        postJson("/api/lessons/" + w.l2() + "/progress", u, Map.of("positionSec", 15, "watchedDeltaSec", 15)).andExpect(status().isOk());
        n = body("/api/lessons/" + w.l2() + "/progress", u);
        assertEquals(15, n.get("watchedSec").asInt());
        assertEquals(15, n.get("maxPositionSec").asInt());
        assertEquals(w.l2(), n.get("lessonId").asLong());
        assertEquals(0, body("/api/lessons/" + w.l2() + "/progress", other).get("watchedSec").asInt());

        postJson("/api/lessons/" + w.l1() + "/progress", u, Map.of("positionSec", 20, "watchedDeltaSec", 20));
        n = body("/api/lessons/" + w.l1() + "/progress", u);
        assertTrue(n.get("completed").asBoolean());
        assertFalse(n.get("completedAt").isNull());
    }

    @Test
    void lessonProgressChecksAccessLikeLessonGet() throws Exception {
        var w = world();
        var u = student(0);
        getJson("/api/lessons/" + w.l3() + "/progress", u).andExpect(status().isForbidden()); // topic trả phí, chưa mua
        getJson("/api/lessons/999999999/progress", u).andExpect(status().isNotFound());
        mvc.perform(get("/api/lessons/" + w.l1() + "/progress")).andExpect(status().isUnauthorized());
        enroll(u, w.course(), null);
        getJson("/api/lessons/" + w.l3() + "/progress", u).andExpect(status().isOk()).andExpect(jsonPath("$.completed").value(false));
    }

    // ---------- GET /api/me/cam-messages ----------

    private JsonNode cam(Account u) throws Exception {
        return body("/api/me/cam-messages", u).get("messages");
    }

    private static String kinds(JsonNode messages) {
        var sb = new StringBuilder();
        messages.forEach(m -> sb.append(m.get("kind").asText()).append(','));
        return sb.toString();
    }

    @Test
    void camGreetsByTimeOfDayWithDisplayName() throws Exception {
        var u = student(0);
        activeToday(u);
        String[][] cases = {{"2026-11-18T01:00:00Z", "buổi sáng"}, {"2026-11-18T05:30:00Z", "buổi trưa"},
                {"2026-11-18T09:00:00Z", "buổi chiều"}, {"2026-11-18T14:00:00Z", "buổi tối"}};
        for (String[] c : cases) {
            clock.set(Instant.parse(c[0]));
            jdbc.update("update user_profiles set last_active_date = ? where user_id = ?",
                    Instant.parse(c[0]).atZone(VN).toLocalDate(), u.id()); // đã học trong ngày của đồng hồ giả
            var m = cam(u);
            assertEquals("GREETING", m.get(0).get("kind").asText());
            assertTrue(m.get(0).get("text").asText().contains(c[1]), m.get(0).get("text").asText());
            assertTrue(m.get(0).get("text").asText().contains("Học viên"));
        }
        assertEquals("GREETING,", kinds(cam(u))); // đã học hôm nay, không có gì khác
    }

    @Test
    void camRemindsToKeepOrStartStreakOnlyWhenNotActiveToday() throws Exception {
        var fresh = student(0);
        var m = cam(fresh);
        assertEquals("STREAK", m.get(1).get("kind").asText());
        assertTrue(m.get(1).get("text").asText().contains("Bắt đầu"));

        var keeper = student(0);
        jdbc.update("update user_profiles set last_active_date = ?, streak_current = 5 where user_id = ?",
                LocalDate.now(VN).minusDays(1), keeper.id());
        m = cam(keeper);
        assertEquals("STREAK", m.get(1).get("kind").asText());
        assertTrue(m.get(1).get("text").asText().contains("5 ngày"));

        activeToday(keeper);
        assertEquals("GREETING,", kinds(cam(keeper)));
    }

    @Test
    void camRemindsUnfinishedAttemptWithRemainingTime() throws Exception {
        var w = world();
        var u = student(0);
        var other = student(0);
        activeToday(u);
        activeToday(other);
        jdbc.update("update question_banks set time_limit_sec = 600 where id = ?", w.lessonBank());
        postJson("/api/banks/" + w.lessonBank() + "/attempts", u, Map.of()).andExpect(status().isCreated());

        var m = cam(u);
        assertEquals("GREETING,IN_PROGRESS_ATTEMPT,", kinds(m));
        assertTrue(m.get(1).get("text").asText().contains("Bài tập Bài 1"));
        assertTrue(m.get(1).get("text").asText().contains("10 phút"), m.get(1).get("text").asText());
        assertEquals("/bai-tap/" + w.lessonBank(), m.at("/1/action/path").asText());
        assertEquals("GREETING,", kinds(cam(other))); // lượt của người khác không lộ
    }

    @Test
    void camWarnsCourseExpiringWithinThresholdOnly() throws Exception {
        var near = world();
        var far = world();
        var u = student(0);
        activeToday(u);
        enroll(u, far.course(), OffsetDateTime.now(ZoneOffset.UTC).plusDays(30));
        assertEquals("GREETING,", kinds(cam(u)));

        enroll(u, near.course(), OffsetDateTime.now(ZoneOffset.UTC).plusDays(3).plusHours(1));
        var m = cam(u);
        assertEquals("GREETING,COURSE_EXPIRING,", kinds(m));
        assertTrue(m.get(1).get("text").asText().contains("còn 4 ngày"), m.get(1).get("text").asText());
        assertEquals("/khoa-hoc/" + near.slug(), m.at("/1/action/path").asText());
    }

    @Test
    void camReportsUnreadNotificationsAndCapsAtThreeMessages() throws Exception {
        var w = world();
        var u = student(0);
        activeToday(u);
        jdbc.update("insert into notifications (user_id, type, payload, dedupe_key) values (?, 'STREAK_REMINDER', '{}' format json, 'u1')", u.id());
        jdbc.update("insert into notifications (user_id, type, payload, dedupe_key) values (?, 'STREAK_REMINDER', '{}' format json, 'u2')", u.id());
        var m = cam(u);
        assertEquals("GREETING,UNREAD,", kinds(m));
        assertTrue(m.get(1).get("text").asText().contains("2 thông báo"));

        // đủ mọi loại: lời chào + 2 ưu tiên cao nhất (lượt dở, chuỗi), thông báo bị cắt
        jdbc.update("update user_profiles set last_active_date = ? where user_id = ?", LocalDate.now(VN).minusDays(1), u.id());
        postJson("/api/banks/" + w.lessonBank() + "/attempts", u, Map.of()).andExpect(status().isCreated());
        enroll(u, w.course(), OffsetDateTime.now(ZoneOffset.UTC).plusDays(2));
        assertEquals("GREETING,IN_PROGRESS_ATTEMPT,STREAK,", kinds(cam(u)));
        assertEquals("Làm tiếp", cam(u).at("/1/action/label").asText());
    }
}
