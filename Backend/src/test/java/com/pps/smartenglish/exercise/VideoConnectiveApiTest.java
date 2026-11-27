package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.support.ApiTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.pps.smartenglish.lms.repository.UnitGateRepository;
import com.pps.smartenglish.lms.repository.UnitProgressRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** FR-EX-04, D-159: video từ nối. Xem khóa tua (chống nhảy cóc), quiz tối đa 2 lần nộp, lượt đạt, phân quyền theo lượt. */
class VideoConnectiveApiTest extends ApiTestBase {

    @Autowired UnitGateRepository gate;
    @Autowired UnitProgressRepository units;

    private static final String PASSWORD = "Password123!";
    private static final double DURATION = 100;

    private record Bank(long id, long topic, long group, long q1, long q1right, long q1wrong, long q2, long q2right, long q2wrong) {}

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private long question(long bank, long group, int sort) {
        var kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("insert into questions (bank_id, group_id, type, prompt, payload, answer_key, explanation, sort_order)"
                    + " values (?, ?, 'VIDEO_CONNECTIVE', 'Chọn từ nối', '{}' format json, '{}' format json, 'Giải thích VN', ?)", new String[] {"id"});
            ps.setLong(1, bank);
            ps.setLong(2, group);
            ps.setInt(3, sort);
            return ps;
        }, kh);
        return kh.getKey().longValue();
    }

    /** Bộ có một nhóm video YouTube, hai câu quiz một đáp án. */
    private Bank bank() {
        long c = insert("courses", Map.of("slug", "vc-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED"));
        long t = insert("topics", Map.of("level_id", lv, "title", "Topic", "is_free_trial", true, "status", "PUBLISHED"));
        long bank = insert("question_banks", Map.of("topic_id", t, "title", "Video từ nối", "is_trial", false, "status", "PUBLISHED", "mode", "PRACTICE"));
        long g = insert("question_groups", Map.of("bank_id", bank, "sort_order", 1, "title", "Video", "video_youtube_id", "dQw4w9WgXcQ", "video_duration_sec", 100));
        long q1 = question(bank, g, 1);
        long q2 = question(bank, g, 2);
        return new Bank(bank, t, g, q1, choice(q1, true), choice(q1, false), q2, choice(q2, true), choice(q2, false));
    }

    private long choice(long q, boolean right) {
        return insert("question_choices", Map.of("question_id", q, "label", right ? "B" : "A", "content", right ? "However" : "And", "is_correct", right));
    }

    private ResultActions post(String url, String token, Object body) throws Exception {
        return send(MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)), token);
    }

    private long start(Bank b, String token) throws Exception {
        String body = post("/api/banks/" + b.id() + "/attempts", token, Map.of()).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private ResultActions progress(long attempt, Bank b, String token, double position) throws Exception {
        return post("/api/attempts/" + attempt + "/video-progress", token, Map.of("groupId", b.group(), "position", position));
    }

    /** Lùi mốc thời gian báo để mô phỏng thời gian thực đã trôi (không cần ngủ). */
    private void ageReports(long attempt, int firstSecAgo, int lastSecAgo) {
        jdbc.update("update attempt_video_groups set first_report_at = timestampadd(SECOND, ?, current_timestamp),"
                + " last_report_at = timestampadd(SECOND, ?, current_timestamp) where attempt_id = ?", -firstSecAgo, -lastSecAgo, attempt);
    }

    /** Xem liền mạch từ đầu tới cuối: mỗi bước 5 giây, mỗi bước cách lần báo trước 5 giây thực. */
    private void watchAll(long attempt, Bank b, String token) throws Exception {
        progress(attempt, b, token, 0).andExpect(status().isOk());
        for (double pos = 5; pos <= DURATION; pos += 5) {
            ageReports(attempt, 1000, 5);
            progress(attempt, b, token, pos).andExpect(status().isOk());
        }
    }

    private ResultActions quiz(long attempt, Bank b, String token, boolean first, boolean second) throws Exception {
        return post("/api/attempts/" + attempt + "/groups/" + b.group() + "/video-quiz", token, Map.of("answers", List.of(
                Map.of("questionId", b.q1(), "answer", Map.of("choiceIds", List.of(first ? b.q1right() : b.q1wrong()))),
                Map.of("questionId", b.q2(), "answer", Map.of("choiceIds", List.of(second ? b.q2right() : b.q2wrong()))))));
    }

    /** CHECK questions.type phải nhận mọi giá trị enum hiện có (V48 từng làm rơi VIDEO_CONNECTIVE; migration sau không được lặp lỗi). */
    @Test
    void typeCheckAcceptsEveryQuestionType() {
        var b = bank();
        for (var t : com.pps.smartenglish.exercise.domain.QuestionType.values()) {
            jdbc.update("insert into questions (bank_id, type, prompt, payload, answer_key, sort_order) values (?, ?, 'x', '{}' format json, '{}' format json, 99)",
                    b.id(), t.name());
        }
    }

    @Test
    void skippingAheadIsNotCountedAndQuizStaysClosed() throws Exception {
        var b = bank();
        var u = register(newEmail(), PASSWORD).accessToken();
        long id = start(b, u);
        progress(id, b, u, 0).andExpect(status().isOk()).andExpect(jsonPath("$.watched").value(false));
        ageReports(id, 60, 5);
        progress(id, b, u, 95).andExpect(jsonPath("$.watchedSec").value(0.0)).andExpect(jsonPath("$.watched").value(false)); // nhảy cóc
        ageReports(id, 60, 5);
        progress(id, b, u, 100).andExpect(jsonPath("$.watchedSec").value(0.0)); // đi tiếp từ chỗ nhảy cũng không cộng
        quiz(id, b, u, true, true).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("video_not_watched"));
    }

    @Test
    void burstOfReportsCannotOutrunRealTime() throws Exception {
        var b = bank();
        var u = register(newEmail(), PASSWORD).accessToken();
        long id = start(b, u);
        progress(id, b, u, 0).andExpect(status().isOk());
        for (double pos = 8; pos <= 80; pos += 8) { // 10 báo cáo dồn trong vài mili giây, mỗi lần +8s
            progress(id, b, u, pos).andExpect(status().isOk());
        }
        JsonNode s = json.readTree(progress(id, b, u, 90).andReturn().getResponse().getContentAsString());
        assertTrue(s.get("watchedSec").asDouble() <= 10.5, "tổng không vượt thời gian thực + dung sai: " + s);
        assertFalse(s.get("watched").asBoolean());
    }

    @Test
    void fullWatchOpensQuizTwoSubmitsMaxAndPassedAttemptCounts() throws Exception {
        var b = bank();
        var u = register(newEmail(), PASSWORD).accessToken();
        long id = start(b, u);
        watchAll(id, b, u);

        // lần 1 sai một câu: báo câu sai, chưa lộ đáp án và giải thích
        quiz(id, b, u, true, false).andExpect(status().isOk()).andExpect(jsonPath("$.finalized").value(false))
                .andExpect(jsonPath("$.submits").value(1)).andExpect(jsonPath("$.results[0].correct").value(true))
                .andExpect(jsonPath("$.results[1].correct").value(false)).andExpect(jsonPath("$.results[1].correctAnswer").doesNotExist())
                .andExpect(jsonPath("$.results[1].explanation").doesNotExist());
        // lần 2 đúng: chốt, đạt, hiện đáp án và giải thích
        quiz(id, b, u, true, true).andExpect(status().isOk()).andExpect(jsonPath("$.finalized").value(true))
                .andExpect(jsonPath("$.passed").value(true)).andExpect(jsonPath("$.results[1].explanation").value("Giải thích VN"))
                .andExpect(jsonPath("$.results[1].correctAnswer.choiceIds[0]").value(b.q2right()));
        quiz(id, b, u, true, true).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("video_quiz_finalized"));

        // client gửi đáp án sai khi nộp lượt: vẫn chấm theo đáp án quiz đã chốt
        post("/api/attempts/" + id + "/submit", u, Map.of("answers", List.of(
                Map.of("questionId", b.q1(), "answer", Map.of("choiceIds", List.of(b.q1wrong())))))).andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(2.0)).andExpect(jsonPath("$.passed").value(true));
        send(get("/api/attempts/" + id + "/video-quiz"), u).andExpect(status().isOk()).andExpect(jsonPath("$.attemptNo").value(1))
                .andExpect(jsonPath("$.passedAttempts").value(1)).andExpect(jsonPath("$.requiredAttempts").value(1))
                .andExpect(jsonPath("$.groups[0].finalized").value(true));
    }

    @Test
    void thirdSubmitIsBlockedAndTwoWrongSubmitsFailTheRun() throws Exception {
        var b = bank();
        var u = register(newEmail(), PASSWORD).accessToken();
        long id = start(b, u);
        watchAll(id, b, u);
        quiz(id, b, u, false, false).andExpect(status().isOk()).andExpect(jsonPath("$.finalized").value(false));
        quiz(id, b, u, false, true).andExpect(status().isOk()).andExpect(jsonPath("$.finalized").value(true))
                .andExpect(jsonPath("$.passed").value(false)).andExpect(jsonPath("$.results[0].correctAnswer").exists());
        quiz(id, b, u, true, true).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("video_quiz_finalized"));

        post("/api/attempts/" + id + "/submit", u, Map.of("answers", List.of())).andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(false));
        send(get("/api/attempts/" + id + "/video-quiz"), u).andExpect(jsonPath("$.passedAttempts").value(0));
    }

    @Test
    void unwatchedQuizAnswersFromSubmitAreIgnored() throws Exception {
        var b = bank();
        var u = register(newEmail(), PASSWORD).accessToken();
        long id = start(b, u);
        post("/api/attempts/" + id + "/submit", u, Map.of("answers", List.of(
                Map.of("questionId", b.q1(), "answer", Map.of("choiceIds", List.of(b.q1right()))),
                Map.of("questionId", b.q2(), "answer", Map.of("choiceIds", List.of(b.q2right())))))).andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(0.0)).andExpect(jsonPath("$.passed").value(false));
    }

    @Test
    void incompleteQuizAnswersDoNotCostASubmit() throws Exception {
        var b = bank();
        var u = register(newEmail(), PASSWORD).accessToken();
        long id = start(b, u);
        watchAll(id, b, u);
        post("/api/attempts/" + id + "/groups/" + b.group() + "/video-quiz", u, Map.of("answers", List.of(
                Map.of("questionId", b.q1(), "answer", Map.of("choiceIds", List.of(b.q1right())))))).andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("select submits from attempt_video_groups where attempt_id = ?", Integer.class, id));
    }

    @Test
    void onlyTheOwnerOfAnOpenAttemptCanReportOrSubmit() throws Exception {
        var b = bank();
        var owner = register(newEmail(), PASSWORD).accessToken();
        var other = register(newEmail(), PASSWORD).accessToken();
        long id = start(b, owner);
        progress(id, b, other, 0).andExpect(status().isNotFound());
        quiz(id, b, other, true, true).andExpect(status().isNotFound());
        send(get("/api/attempts/" + id + "/video-quiz"), other).andExpect(status().isNotFound());
        post("/api/attempts/" + id + "/video-progress", owner, Map.of("groupId", 999999, "position", 1)).andExpect(status().isNotFound());
        post("/api/attempts/" + id + "/video-progress", owner, Map.of("groupId", b.group(), "position", -1)).andExpect(status().isBadRequest());

        post("/api/attempts/" + id + "/submit", owner, Map.of("answers", List.of())).andExpect(status().isOk());
        progress(id, b, owner, 0).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("attempt_already_submitted"));
    }

    @Test
    void videoQuestionsAreNotCheckedOneByOne() throws Exception {
        var b = bank();
        var u = register(newEmail(), PASSWORD).accessToken();
        long id = start(b, u);
        post("/api/attempts/" + id + "/questions/" + b.q1() + "/check", u, Map.of("answer", Map.of("choiceIds", List.of(b.q1right()))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("video_quiz_group"));
    }

    @Test
    void unitCompletionNeedsAPassedRunForVideoBanks() throws Exception {
        var b = bank();
        var u = register(newEmail(), PASSWORD);
        long first = start(b, u.accessToken());
        post("/api/attempts/" + first + "/submit", u.accessToken(), Map.of("answers", List.of())).andExpect(status().isOk());
        assertEquals(0, done(b, u.id()), "nộp mà chưa đạt thì bộ chưa hoàn thành");
        assertTrue(units.unfinishedTopicIds(courseOf(b), u.id()).contains(b.topic()));

        long second = start(b, u.accessToken());
        watchAll(second, b, u.accessToken());
        quiz(second, b, u.accessToken(), true, true).andExpect(status().isOk());
        assertEquals(0, done(b, u.id()), "lượt đạt chưa nộp thì chưa tính");
        post("/api/attempts/" + second + "/submit", u.accessToken(), Map.of("answers", List.of())).andExpect(status().isOk());
        assertEquals(1, done(b, u.id()));
        assertFalse(units.unfinishedTopicIds(courseOf(b), u.id()).contains(b.topic()));
    }

    private long courseOf(Bank b) {
        return jdbc.queryForObject("select lv.course_id from topics t join course_levels lv on lv.id = t.level_id where t.id = ?", Long.class, b.topic());
    }

    /** Số bộ đã hoàn thành của unit theo đúng truy vấn của cổng unit. */
    private int done(Bank b, long userId) {
        return ((Number) gate.exerciseCounts(b.topic(), userId).get(0)[1]).intValue();
    }
}
