package com.pps.smartenglish.retention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.ai.AiTestSupport;
import com.pps.smartenglish.shared.event.AiGradingFinished;
import com.pps.smartenglish.shared.event.ExerciseScored;
import com.pps.smartenglish.shared.event.LessonCompleted;
import com.pps.smartenglish.support.EventCollectorConfig;
import com.pps.smartenglish.support.EventCollectorConfig.EventCollector;
import com.pps.smartenglish.support.MutableClockConfig;
import com.pps.smartenglish.support.MutableClockConfig.MutableClock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

/** FR-RET-01 (streak) và FR-RET-02 (XP, danh hiệu). */
@Import({MutableClockConfig.class, EventCollectorConfig.class})
class StreakXpTest extends AiTestSupport {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired ApplicationEventPublisher publisher;
    @Autowired MutableClock clock;
    @Autowired EventCollector collector;
    @Autowired TransactionTemplate tx;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private static LocalDate today() {
        return LocalDate.now(VN);
    }

    private void setProfile(Account u, int current, int best, LocalDate last) {
        jdbc.update("update user_profiles set streak_current = ?, streak_best = ?, last_active_date = ? where user_id = ?",
                current, best, last, u.id());
    }

    private Map<String, Object> profile(Account u) {
        return jdbc.queryForMap("select xp, streak_current, streak_best, last_active_date from user_profiles where user_id = ?", u.id());
    }

    private long xp(Account u) {
        return ((Number) profile(u).get("XP")).longValue();
    }

    private int streak(Account u) {
        return ((Number) profile(u).get("STREAK_CURRENT")).intValue();
    }

    private void complete(Account u, long lessonId) {
        publisher.publishEvent(new LessonCompleted(u.id(), lessonId));
    }

    private long freeLesson(int duration) {
        long c = insert("courses", Map.of("slug", "ret-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A"));
        long t = insert("topics", Map.of("level_id", lv, "title", "Topic", "is_free_trial", true));
        return insert("lessons", Map.of("topic_id", t, "type", "CONNECTIVE_VIDEO", "title", "Bài", "status", "PUBLISHED",
                "video_source", "YOUTUBE", "video_ref", "abc", "duration_sec", duration));
    }

    /** Bank gồm một câu trắc nghiệm; trả [bankId, questionId, idLựaChọnĐúng]. */
    private long[] mcqBank() {
        long b = bank(true);
        long q = question(b, "CONNECTIVE_MCQ", 1, "p", "{}", "{}");
        long right = insert("question_choices", Map.of("question_id", q, "label", "A", "content", "đúng", "is_correct", true));
        insert("question_choices", Map.of("question_id", q, "label", "B", "content", "sai", "is_correct", false));
        return new long[] {b, q, right};
    }

    private void submitMcq(Account u, long[] bank) throws Exception {
        long attempt = idOf(postJson("/api/banks/" + bank[0] + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
        postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers",
                List.of(Map.of("questionId", bank[1], "answer", Map.of("choiceId", bank[2]))))).andExpect(status().isOk());
    }

    private int xpEvents(Account u, String source) {
        return count("select count(*) from xp_events where user_id = ? and source = ?", u.id(), source);
    }

    @Test
    void streakKeepsRaisesOrResetsByDay() throws Exception {
        var u = student(0);
        complete(u, 1);
        assertEquals(1, streak(u));
        assertEquals(1, ((Number) profile(u).get("STREAK_BEST")).intValue());
        assertEquals(java.sql.Date.valueOf(today()), profile(u).get("LAST_ACTIVE_DATE"));

        complete(u, 2); // cùng ngày: giữ nguyên
        assertEquals(1, streak(u));
        assertEquals(1, count("select count(*) from activity_days where user_id = ?", u.id()));

        // hôm qua có học: +1 và best theo kịp
        jdbc.update("delete from activity_days where user_id = ?", u.id());
        setProfile(u, 3, 3, today().minusDays(1));
        complete(u, 3);
        assertEquals(4, streak(u));
        assertEquals(4, ((Number) profile(u).get("STREAK_BEST")).intValue());

        // cách ngày: về 1, best giữ nguyên
        jdbc.update("delete from activity_days where user_id = ?", u.id());
        setProfile(u, 5, 9, today().minusDays(3));
        complete(u, 4);
        assertEquals(1, streak(u));
        assertEquals(9, ((Number) profile(u).get("STREAK_BEST")).intValue());
    }

    @Test
    void concurrentEventsOnTheSameDayCountOnce() throws Exception {
        var u = student(0);
        int n = 6;
        var pool = Executors.newFixedThreadPool(n);
        try {
            List<Callable<Boolean>> calls = IntStream.range(0, n).<Callable<Boolean>>mapToObj(i -> () -> {
                complete(u, 100 + i);
                return true;
            }).toList();
            for (var f : pool.invokeAll(calls)) {
                f.get();
            }
        } finally {
            pool.shutdown();
        }
        assertEquals(1, count("select count(*) from activity_days where user_id = ?", u.id()));
        assertEquals(1, streak(u));
        assertEquals(60, xp(u)); // 6 bài khác nhau, mỗi bài 10 XP, cộng nguyên tử không mất
    }

    @Test
    void dayRollsOverAtMidnightVietnamTime() throws Exception {
        var u = student(0);
        clock.set(Instant.parse("2026-11-05T16:30:00Z")); // 23:30 ngày 5/11 giờ Việt Nam
        complete(u, 1);
        assertEquals(java.sql.Date.valueOf("2026-11-05"), profile(u).get("LAST_ACTIVE_DATE"));
        clock.set(Instant.parse("2026-11-05T17:30:00Z")); // 00:30 ngày 6/11 giờ Việt Nam: sang ngày mới dù vẫn là 5/11 theo UTC
        complete(u, 2);
        assertEquals(java.sql.Date.valueOf("2026-11-06"), profile(u).get("LAST_ACTIVE_DATE"));
        assertEquals(2, streak(u));
        assertEquals(2, count("select count(*) from activity_days where user_id = ?", u.id()));
    }

    @Test
    void statsShowZeroOnceStreakIsBroken() throws Exception {
        var u = student(0);
        setProfile(u, 5, 9, today().minusDays(3));
        getJson("/api/me/stats", u).andExpect(status().isOk()).andExpect(jsonPath("$.streakCurrent").value(0))
                .andExpect(jsonPath("$.streakBest").value(9)).andExpect(jsonPath("$.activeToday").value(false));
        setProfile(u, 5, 9, today().minusDays(1));
        getJson("/api/me/stats", u).andExpect(jsonPath("$.streakCurrent").value(5)).andExpect(jsonPath("$.activeToday").value(false));
        setProfile(u, 5, 9, today());
        getJson("/api/me/stats", u).andExpect(jsonPath("$.streakCurrent").value(5)).andExpect(jsonPath("$.activeToday").value(true));
    }

    @Test
    void completingALessonRewardsOnceAndReplayDoesNot() throws Exception {
        var u = student(0);
        long lesson = freeLesson(100);
        String url = "/api/lessons/" + lesson + "/progress";
        for (int i = 1; i <= 3; i++) { // 60 giây: chưa tới 80%
            postJson(url, u, Map.of("positionSec", 20 * i, "watchedDeltaSec", 20)).andExpect(status().isOk());
        }
        assertEquals(0, collector.lessonCompletedFor(u.id(), lesson));
        assertEquals(0, xp(u));

        postJson(url, u, Map.of("positionSec", 80, "watchedDeltaSec", 20)).andExpect(jsonPath("$.completed").value(true));
        assertEquals(1, collector.lessonCompletedFor(u.id(), lesson));
        assertEquals(10, xp(u));
        assertEquals(1, streak(u));

        // xem lại bài đã hoàn thành: không phát sự kiện, không thưởng
        postJson(url, u, Map.of("positionSec", 100, "watchedDeltaSec", 20)).andExpect(jsonPath("$.completed").value(true));
        postJson(url, u, Map.of("positionSec", 5, "watchedDeltaSec", 20)).andExpect(status().isOk());
        assertEquals(1, collector.lessonCompletedFor(u.id(), lesson));
        assertEquals(10, xp(u));
        assertEquals(1, xpEvents(u, "LESSON"));
    }

    @Test
    void resubmittingTheSameBankGivesNoDuplicateXp() throws Exception {
        var u = student(0);
        var bank = mcqBank();
        submitMcq(u, bank); // đúng 100%: 10 (nộp bài) + 10 (điểm cao)
        assertEquals(20, xp(u));
        submitMcq(u, bank);
        assertEquals(20, xp(u));
        assertEquals(1, xpEvents(u, "EXERCISE"));
        assertEquals(1, xpEvents(u, "EXERCISE_HIGH"));
        assertEquals(1, streak(u)); // hai lần nộp cùng ngày vẫn là một ngày
        assertEquals(1, count("select count(*) from activity_days where user_id = ?", u.id()));
    }

    @Test
    void highScoreBonusStartsExactlyAtEightyPercent() throws Exception {
        var u = student(0);
        publisher.publishEvent(new ExerciseScored(u.id(), 11, 1, 4, 5, true)); // 80%: đúng biên
        publisher.publishEvent(new ExerciseScored(u.id(), 12, 1, 79, 100, true)); // 79%
        publisher.publishEvent(new ExerciseScored(u.id(), 13, 1, 3, 5, true)); // 60%
        publisher.publishEvent(new ExerciseScored(u.id(), 14, 1, 0, 0, true)); // không có câu chấm được
        assertEquals(1, xpEvents(u, "EXERCISE_HIGH"));
        assertEquals(4, xpEvents(u, "EXERCISE"));
        assertEquals(50, xp(u));
        assertEquals(1, count("select count(*) from xp_events where user_id = ? and source = 'EXERCISE_HIGH' and ref_id = 11", u.id()));
    }

    @Test
    void lateAiGradedRewriteStillEarnsTheHighScoreBonus() throws Exception {
        var u = student(1);
        long b = bank(true);
        long mcq = question(b, "CONNECTIVE_MCQ", 1, "p", "{}", "{}");
        long right = insert("question_choices", Map.of("question_id", mcq, "label", "A", "content", "đúng", "is_correct", true));
        List<Map<String, Object>> answers = new ArrayList<>();
        answers.add(Map.of("questionId", mcq, "answer", Map.of("choiceId", right)));
        for (int i = 2; i <= 5; i++) {
            long q = question(b, "GRAMMAR_REWRITE", i, "Viết lại " + i, "{\"sentence\":\"s\",\"requiredElement\":\"PASSIVE\"}", "{}");
            answers.add(Map.of("questionId", q, "answer", Map.of("text", "A rewritten sentence " + i)));
        }
        long attempt = idOf(postJson("/api/banks/" + b + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
        postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers", answers)).andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(1)).andExpect(jsonPath("$.maxScore").value(5));
        assertEquals(10, xp(u)); // mới 1/5: có thưởng nộp bài, chưa có thưởng điểm cao
        assertEquals(0, xpEvents(u, "EXERCISE_HIGH"));

        runner.runDueNow(); // AI chấm đúng cả 4 câu: 5/5
        assertEquals(20, xp(u));
        assertEquals(1, xpEvents(u, "EXERCISE_HIGH"));
        // sự kiện sau AI không tạo ngày học mới và không đổi chuỗi
        assertEquals(1, count("select count(*) from activity_days where user_id = ?", u.id()));
        assertEquals(1, streak(u));
    }

    @Test
    void aiGradingFinishedForOtherKindsDoesNothingToStats() throws Exception {
        var u = student(0);
        publisher.publishEvent(new AiGradingFinished(1, u.id(), "REWRITE_GRADE"));
        assertEquals(0, xp(u));
        assertEquals(0, streak(u));
    }

    @Test
    void titleThresholdsComeFromConfig() throws Exception {
        var u = student(0);
        Object[][] cases = {{0, "Tập sự", "Tự tin", 500}, {499, "Tập sự", "Tự tin", 500}, {500, "Tự tin", "Chuyên gia", 2000},
                {1999, "Tự tin", "Chuyên gia", 2000}, {2000, "Chuyên gia", null, null}};
        for (Object[] c : cases) {
            jdbc.update("update user_profiles set xp = ? where user_id = ?", c[0], u.id());
            var r = getJson("/api/me/stats", u).andExpect(status().isOk()).andExpect(jsonPath("$.xp").value(c[0]))
                    .andExpect(jsonPath("$.title").value(c[1]));
            if (c[2] == null) {
                r.andExpect(jsonPath("$.nextTitle").doesNotExist()).andExpect(jsonPath("$.nextTitleXp").doesNotExist());
            } else {
                r.andExpect(jsonPath("$.nextTitle").value(c[2])).andExpect(jsonPath("$.nextTitleXp").value(c[3]));
            }
        }
    }

    @Test
    void retentionFailuresNeverBreakTheCallerOrItsTransaction() throws Exception {
        var u = student(7);
        // người dùng không tồn tại: ghi retention vi phạm khóa ngoại, nhưng chỉ bị log
        tx.executeWithoutResult(s -> {
            jdbc.update("update user_ai_quota set balance = 77 where user_id = ?", u.id());
            publisher.publishEvent(new LessonCompleted(999_999_999L, 1));
            publisher.publishEvent(new ExerciseScored(999_999_999L, 1, 1, 5, 5, true));
        });
        assertEquals(77, balance(u));
        assertEquals(0, count("select count(*) from activity_days where user_id = 999999999"));
        assertNotNull(profile(u));
    }
}
