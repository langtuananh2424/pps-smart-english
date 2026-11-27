package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.exercise.service.AttemptTimeoutJob;
import com.pps.smartenglish.support.ApiTestBase;
import com.pps.smartenglish.support.MutableClockConfig;
import com.pps.smartenglish.support.MutableClockConfig.MutableClock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** FR-EX-06, D-11: tạm dừng và tiếp tục (chỉ PRACTICE), lưu nháp, hết giờ thì máy chủ tự nộp bằng đáp án đã lưu. */
@Import(MutableClockConfig.class)
class AttemptTimerTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";

    @Autowired MutableClock clock;
    @Autowired AttemptTimeoutJob timeoutJob;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    /** Bộ một câu trắc nghiệm đúng: choiceId đúng là right. Tạo trong topic miễn phí nên học viên làm được. */
    private record Bank(long id, long question, long right) {}

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private Bank bank(String mode, Integer timeLimitSec) {
        long c = insert("courses", Map.of("slug", "tm-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED"));
        long t = insert("topics", Map.of("level_id", lv, "title", "Topic", "is_free_trial", true, "status", "PUBLISHED"));
        var bankCols = new HashMap<String, Object>(Map.of("topic_id", t, "title", "Bộ", "is_trial", false, "status", "PUBLISHED",
                "mode", mode));
        if (timeLimitSec != null) {
            bankCols.put("time_limit_sec", timeLimitSec);
        }
        long bank = insert("question_banks", bankCols);
        var kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("insert into questions (bank_id, type, prompt, payload, answer_key, sort_order)"
                    + " values (?, 'CONNECTIVE_MCQ', 'Đề', '{}' format json, '{}' format json, 1)", new String[] {"id"});
            ps.setLong(1, bank);
            return ps;
        }, kh);
        long q = kh.getKey().longValue();
        long right = insert("question_choices", Map.of("question_id", q, "label", "B", "content", "Đúng", "is_correct", true));
        insert("question_choices", Map.of("question_id", q, "label", "A", "content", "Sai", "is_correct", false));
        return new Bank(bank, q, right);
    }

    private ResultActions postAuth(String url, String token) throws Exception {
        return send(MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content("{}"), token);
    }

    private ResultActions putAuth(String url, String token, Object body) throws Exception {
        return send(MockMvcRequestBuilders.put(url).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)), token);
    }

    private ResultActions getAuth(String url, String token) throws Exception {
        return send(get(url), token);
    }

    private long startAttempt(long bankId, String token, Instant at) throws Exception {
        clock.set(at);
        String body = postAuth("/api/banks/" + bankId + "/attempts", token).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }

    @Test
    void pauseStopsTheClockAndDraftIsSubmittedWhenTimeRunsOut() throws Exception {
        var b = bank("PRACTICE", 60);
        var u = register(newEmail(), PASSWORD);
        Instant base = now();
        long id = startAttempt(b.id(), u.accessToken(), base);

        clock.set(base.plusSeconds(20));
        send(MockMvcRequestBuilders.post("/api/attempts/" + id + "/pause"), u.accessToken())
                .andExpect(status().isOk()).andExpect(jsonPath("$.paused").value(true))
                .andExpect(jsonPath("$.remainingSec").value(40));

        clock.set(base.plusSeconds(1000)); // đang tạm dừng: không mất giờ
        getAuth("/api/attempts/" + id + "/progress", u.accessToken()).andExpect(jsonPath("$.remainingSec").value(40));

        send(MockMvcRequestBuilders.post("/api/attempts/" + id + "/resume"), u.accessToken())
                .andExpect(status().isOk()).andExpect(jsonPath("$.paused").value(false));
        clock.set(base.plusSeconds(1030));
        getAuth("/api/attempts/" + id + "/progress", u.accessToken()).andExpect(jsonPath("$.remainingSec").value(10));

        putAuth("/api/attempts/" + id + "/draft", u.accessToken(),
                Map.of("answers", List.of(Map.of("questionId", b.question(), "answer", Map.of("choiceId", b.right())))))
                .andExpect(status().isOk());
        getAuth("/api/attempts/" + id + "/progress", u.accessToken())
                .andExpect(jsonPath("$.answers[0].questionId").value(b.question()));

        clock.set(base.plusSeconds(1050)); // hết giờ: máy chủ tự nộp bằng đáp án đã lưu
        getAuth("/api/attempts/" + id + "/progress", u.accessToken()).andExpect(jsonPath("$.remainingSec").value(0));
        assertTrue(timeoutJob.tick() >= 1);

        getAuth("/api/attempts/" + id, u.accessToken()).andExpect(jsonPath("$.score").value(1))
                .andExpect(jsonPath("$.maxScore").value(1));
        clock.set(base.plusSeconds(1051));
        send(MockMvcRequestBuilders.post("/api/attempts/" + id + "/pause"), u.accessToken())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("attempt_already_submitted"));
    }

    @Test
    void submitWithinGraceKeepsSentAnswersButLateOneUsesDraft() throws Exception {
        var b = bank("PRACTICE", 60);
        var u = register(newEmail(), PASSWORD);
        Instant base = now();
        var answers = Map.of("answers", List.of(Map.of("questionId", b.question(), "answer", Map.of("choiceId", b.right()))));

        long inGrace = startAttempt(b.id(), u.accessToken(), base);
        clock.set(base.plusSeconds(62)); // quá hạn 2 giây, trong ân hạn mặc định 5 giây
        send(MockMvcRequestBuilders.post("/api/attempts/" + inGrace + "/submit").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(answers)), u.accessToken())
                .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(1));

        long late = startAttempt(b.id(), u.accessToken(), base.plusSeconds(1000));
        clock.set(base.plusSeconds(1070)); // quá hạn 10 giây: đáp án gửi kèm bị bỏ, nháp rỗng
        send(MockMvcRequestBuilders.post("/api/attempts/" + late + "/submit").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(answers)), u.accessToken())
                .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(0));
    }

    @Test
    void timeoutJobWaitsOutGraceBeforeAutoSubmit() throws Exception {
        var b = bank("PRACTICE", 60);
        var u = register(newEmail(), PASSWORD);
        Instant base = now();
        long id = startAttempt(b.id(), u.accessToken(), base);

        clock.set(base.plusSeconds(62)); // quá hạn nhưng còn trong ân hạn: job chưa nộp
        timeoutJob.tick();
        getAuth("/api/attempts/" + id + "/progress", u.accessToken()).andExpect(status().isOk());

        clock.set(base.plusSeconds(70));
        assertTrue(timeoutJob.tick() >= 1);
        send(MockMvcRequestBuilders.post("/api/attempts/" + id + "/pause"), u.accessToken())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("attempt_already_submitted"));
    }

    @Test
    void examBankCannotBePaused() throws Exception {
        var b = bank("EXAM", 60);
        var u = register(newEmail(), PASSWORD);
        // D-35: bộ EXAM không mở cho học thử, nên học viên phải có enrollment của khóa
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at) select ?, c.id, 'ACTIVE', "
                + "CURRENT_TIMESTAMP from question_banks b join topics t on t.id = b.topic_id join course_levels l on l.id = t.level_id "
                + "join courses c on c.id = l.course_id where b.id = ?", u.id(), b.id());
        long id = startAttempt(b.id(), u.accessToken(), now());
        send(MockMvcRequestBuilders.post("/api/attempts/" + id + "/pause"), u.accessToken())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("attempt_not_pausable"));
    }

    @Test
    void untimedBankHasNoClockAndCannotBePaused() throws Exception {
        var b = bank("PRACTICE", null);
        var u = register(newEmail(), PASSWORD);
        long id = startAttempt(b.id(), u.accessToken(), now());
        getAuth("/api/attempts/" + id + "/progress", u.accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingSec").doesNotExist());
        send(MockMvcRequestBuilders.post("/api/attempts/" + id + "/pause"), u.accessToken())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("attempt_not_pausable"));
    }

    @Test
    void expiredAttemptRejectsDraftAndPause() throws Exception {
        var b = bank("PRACTICE", 10);
        var u = register(newEmail(), PASSWORD);
        Instant base = now();
        long id = startAttempt(b.id(), u.accessToken(), base);
        clock.set(base.plusSeconds(10));
        putAuth("/api/attempts/" + id + "/draft", u.accessToken(), Map.of("answers", List.of()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("attempt_time_up"));
        send(MockMvcRequestBuilders.post("/api/attempts/" + id + "/pause"), u.accessToken())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("attempt_time_up"));
    }

    @Test
    void draftRejectsQuestionsOutsideTheBank() throws Exception {
        var b = bank("PRACTICE", 60);
        var u = register(newEmail(), PASSWORD);
        long id = startAttempt(b.id(), u.accessToken(), now());
        putAuth("/api/attempts/" + id + "/draft", u.accessToken(),
                Map.of("answers", List.of(Map.of("questionId", 999_999_999L, "answer", Map.of("choiceId", b.right())))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void otherUsersCannotPauseOrReadProgress() throws Exception {
        var b = bank("PRACTICE", 60);
        var owner = register(newEmail(), PASSWORD);
        var other = register(newEmail(), PASSWORD);
        long id = startAttempt(b.id(), owner.accessToken(), now());
        send(MockMvcRequestBuilders.post("/api/attempts/" + id + "/pause"), other.accessToken())
                .andExpect(status().isNotFound());
        getAuth("/api/attempts/" + id + "/progress", other.accessToken()).andExpect(status().isNotFound());
    }
}
