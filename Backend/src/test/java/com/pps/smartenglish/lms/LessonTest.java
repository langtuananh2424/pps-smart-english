package com.pps.smartenglish.lms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.support.ApiTestBase;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.web.servlet.ResultActions;

class LessonTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    /** Dựng course > level > topic > lesson; source/ref/duration null nghĩa là chưa có video / chưa biết thời lượng. */
    private long lesson(boolean free, String source, String ref, Integer duration, String lessonStatus, String courseStatus) {
        long c = insert("courses", Map.of("slug", "lms-" + UUID.randomUUID(), "title", "Khóa", "status", courseStatus));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A"));
        long t = insert("topics", Map.of("level_id", lv, "title", "Topic", "is_free_trial", free));
        var cols = new HashMap<String, Object>(
                Map.of("topic_id", t, "type", "CONNECTIVE_VIDEO", "title", "Bài", "status", lessonStatus));
        cols.put("video_source", source);
        cols.put("video_ref", ref);
        cols.put("duration_sec", duration);
        return insert("lessons", cols);
    }

    private long trial(String source, String ref, Integer duration) {
        return lesson(true, source, ref, duration, "PUBLISHED", "PUBLISHED");
    }

    private ResultActions progress(long lessonId, String token, Object body) throws Exception {
        var req = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/lessons/" + lessonId + "/progress").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body));
        return token == null ? mvc.perform(req) : send(req, token);
    }

    private ResultActions ping(long lessonId, String token, int pos, int delta) throws Exception {
        return progress(lessonId, token, Map.of("positionSec", pos, "watchedDeltaSec", delta));
    }

    private String completedAt(long lessonId) {
        return jdbc.queryForObject("select cast(completed_at as varchar) from lesson_progress where lesson_id = ?",
                String.class, lessonId);
    }

    @Test
    void guestGetsSignedUrlWith120MinuteExpiryForFreeTrialLesson() throws Exception {
        long id = trial("STORAGE", "videos/a.mp4", 100);
        var before = Instant.now();
        var body = mvc.perform(get("/api/lessons/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Bài"))
                .andExpect(jsonPath("$.durationSec").value(100))
                .andExpect(jsonPath("$.video.source").value("STORAGE"))
                .andExpect(jsonPath("$.video.url").value("http://fake-storage/videos/a.mp4?ttl=7200"))
                .andExpect(jsonPath("$.video.ref").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        var expires = Instant.parse(json.readTree(body).at("/video/expiresAt").asText());
        long minutes = Duration.between(before, expires).toMinutes();
        assertEquals(true, minutes == 119 || minutes == 120, "hạn phải ~120 phút, nhận " + minutes);
    }

    @Test
    void lockedLessonReturns403WithoutUrl() throws Exception {
        long id = lesson(false, "STORAGE", "videos/secret.mp4", 100, "PUBLISHED", "PUBLISHED");
        mvc.perform(get("/api/lessons/" + id)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("lesson_locked"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("secret.mp4"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("fake-storage"))));
    }

    @Test
    void youtubeLessonReturnsRefWithoutUrlOrExpiry() throws Exception {
        long id = trial("YOUTUBE", "dQw4w9WgXcQ", 60);
        mvc.perform(get("/api/lessons/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.video.source").value("YOUTUBE"))
                .andExpect(jsonPath("$.video.ref").value("dQw4w9WgXcQ"))
                .andExpect(jsonPath("$.video.url").doesNotExist())
                .andExpect(jsonPath("$.video.expiresAt").doesNotExist());
    }

    @Test
    void lessonWithoutVideoHasNullVideo() throws Exception {
        long id = trial(null, null, null);
        mvc.perform(get("/api/lessons/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.video").value(Matchers.nullValue()));
    }

    @Test
    void unpublishedOrUnknownLessonReturns404() throws Exception {
        long draftLesson = lesson(true, "STORAGE", "x.mp4", 10, "DRAFT", "PUBLISHED");
        long draftCourse = lesson(true, "STORAGE", "x.mp4", 10, "PUBLISHED", "DRAFT");
        for (String id : List.of(String.valueOf(draftLesson), String.valueOf(draftCourse), "999999999", "abc")) {
            mvc.perform(get("/api/lessons/" + id)).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("lesson_not_found"));
        }
    }

    @Test
    void progressRequiresToken() throws Exception {
        long id = trial("YOUTUBE", "abc", 100);
        ping(id, null, 5, 5).andExpect(status().isUnauthorized());
    }

    @Test
    void progressOnLockedLessonReturns403() throws Exception {
        long id = lesson(false, "YOUTUBE", "abc", 100, "PUBLISHED", "PUBLISHED");
        var u = register(newEmail(), PASSWORD);
        ping(id, u.accessToken(), 5, 5).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("lesson_locked"));
        assertEquals(0, count("select count(*) from lesson_progress where lesson_id = ?", id));
    }

    @Test
    void deltaIsCappedAt20SecondsAndMaxPositionKeepsLargest() throws Exception {
        long id = trial("YOUTUBE", "abc", 1000);
        var u = register(newEmail(), PASSWORD);
        ping(id, u.accessToken(), 500, 500).andExpect(status().isOk())
                .andExpect(jsonPath("$.watchedSec").value(20)).andExpect(jsonPath("$.maxPositionSec").value(500));
        ping(id, u.accessToken(), 30, 7).andExpect(status().isOk())
                .andExpect(jsonPath("$.watchedSec").value(27)).andExpect(jsonPath("$.maxPositionSec").value(500))
                .andExpect(jsonPath("$.completed").value(false));
    }

    @Test
    void completedAtIsSetOnceAtEightyPercent() throws Exception {
        long id = trial("YOUTUBE", "abc", 100);
        var u = register(newEmail(), PASSWORD);
        for (int i = 0; i < 3; i++) {
            ping(id, u.accessToken(), 20 * (i + 1), 20).andExpect(jsonPath("$.completed").value(false)); // 60
        }
        ping(id, u.accessToken(), 70, 19).andExpect(jsonPath("$.completed").value(false)); // 79: chưa tới 80%
        ping(id, u.accessToken(), 80, 1).andExpect(jsonPath("$.completed").value(true)); // 80 = đúng ngưỡng
        String first = completedAt(id);
        assertNotNull(first);
        ping(id, u.accessToken(), 90, 10).andExpect(jsonPath("$.watchedSec").value(90))
                .andExpect(jsonPath("$.completed").value(true));
        assertEquals(first, completedAt(id));
    }

    @Test
    void lessonWithoutDurationNeverCompletes() throws Exception {
        long id = trial("YOUTUBE", "abc", null);
        var u = register(newEmail(), PASSWORD);
        ping(id, u.accessToken(), 20, 20).andExpect(jsonPath("$.completed").value(false));
    }

    @Test
    void negativeOrMissingValuesReturn400() throws Exception {
        long id = trial("YOUTUBE", "abc", 100);
        var u = register(newEmail(), PASSWORD);
        ping(id, u.accessToken(), 5, -1).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_failed"));
        ping(id, u.accessToken(), -5, 5).andExpect(status().isBadRequest());
        progress(id, u.accessToken(), Map.of("positionSec", 5)).andExpect(status().isBadRequest());
    }

    @Test
    void concurrentFirstRequestsDoNotLoseDeltaOrDuplicateRow() throws Exception {
        long id = trial("YOUTUBE", "abc", 10000);
        var u = register(newEmail(), PASSWORD);
        int n = 8;
        var pool = Executors.newFixedThreadPool(n);
        try {
            List<Callable<Integer>> calls = IntStream.range(0, n)
                    .<Callable<Integer>>mapToObj(i -> () -> ping(id, u.accessToken(), 10, 10)
                            .andReturn().getResponse().getStatus())
                    .toList();
            for (var f : pool.invokeAll(calls)) {
                assertEquals(200, f.get());
            }
        } finally {
            pool.shutdown();
        }
        assertEquals(1, count("select count(*) from lesson_progress where lesson_id = ?", id));
        assertEquals(80, count("select watched_sec from lesson_progress where lesson_id = ?", id));
    }
}
