package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.exercise.service.AttemptTimeoutJob;
import com.pps.smartenglish.support.ApiTestBase;
import com.pps.smartenglish.support.MutableClockConfig;
import com.pps.smartenglish.support.MutableClockConfig.MutableClock;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Tester D-147 ân hạn nộp (P0 chống gian lận): biên đúng giây, nháp sau hạn, chạy đua job tự nộp với request. */
@Import(MutableClockConfig.class)
class AttemptTimerGraceTesterTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";

    @Autowired MutableClock clock;
    @Autowired AttemptTimeoutJob timeoutJob;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private record Bank(long id, long q, long right) {}

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private Bank bank(String mode) {
        long c = insert("courses", Map.of("slug", "gr-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED"));
        long t = insert("topics", Map.of("level_id", lv, "title", "Topic", "is_free_trial", true, "status", "PUBLISHED"));
        long bank = insert("question_banks", Map.of("topic_id", t, "title", "Bộ", "is_trial", false, "status", "PUBLISHED",
                "mode", mode, "time_limit_sec", 60));
        long q = insert("questions", Map.of("bank_id", bank, "type", "CONNECTIVE_MCQ", "prompt", "Đề", "sort_order", 1));
        long right = insert("question_choices", Map.of("question_id", q, "label", "B", "content", "Đúng", "is_correct", true));
        insert("question_choices", Map.of("question_id", q, "label", "A", "content", "Sai", "is_correct", false));
        return new Bank(bank, q, right);
    }

    private String body(Bank b) throws Exception {
        return json.writeValueAsString(Map.of("answers", List.of(Map.of("questionId", b.q(), "answer", Map.of("choiceId", b.right())))));
    }

    private ResultActions submit(long id, Bank b, String token) throws Exception {
        return send(MockMvcRequestBuilders.post("/api/attempts/" + id + "/submit").contentType(MediaType.APPLICATION_JSON).content(body(b)), token);
    }

    private long start(Bank b, String token, Instant at) throws Exception {
        clock.set(at);
        String res = send(MockMvcRequestBuilders.post("/api/banks/" + b.id() + "/attempts").contentType(MediaType.APPLICATION_JSON).content("{}"), token)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("id").asLong();
    }

    /** D-35: bộ EXAM không mở cho học thử nên học viên phải có enrollment. */
    private String enrolled(Bank b) throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at) select ?, c.id, 'ACTIVE', "
                + "TIMESTAMP '2020-01-01 00:00:00' from question_banks b join topics t on t.id = b.topic_id join course_levels l on l.id = t.level_id "
                + "join courses c on c.id = l.course_id where b.id = ?", u.id(), b.id());
        return u.accessToken();
    }

    private BigDecimal score(long id) {
        return jdbc.queryForObject("select score from student_exercise_attempts where id = ?", BigDecimal.class, id);
    }

    /** Biên: 64,999 giây sau hạn 60 giây (còn ân hạn 5 giây) nhận đáp án; đúng 65 giây thì bỏ, lấy nháp (rỗng). Bộ EXAM. */
    @Test
    void graceBoundaryIsExactOnExamBank() throws Exception {
        var b = bank("EXAM");
        String u = enrolled(b);
        Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        long inside = start(b, u, base);
        clock.set(base.plusMillis(64_999));
        submit(inside, b, u).andExpect(status().isOk()).andExpect(jsonPath("$.score").value(1));

        long edge = start(b, u, base.plusSeconds(1000));
        clock.set(base.plusSeconds(1065));
        submit(edge, b, u).andExpect(status().isOk()).andExpect(jsonPath("$.score").value(0));
    }

    /** Hết giờ thì nháp không lưu được nữa (kể cả trong ân hạn), nên job tự nộp không thể nhận đáp án gửi muộn qua nháp. */
    @Test
    void draftAfterDeadlineIsRejectedSoJobUsesOldDraft() throws Exception {
        var b = bank("EXAM");
        String u = enrolled(b);
        Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        long id = start(b, u, base);
        clock.set(base.plusSeconds(61));
        send(MockMvcRequestBuilders.put("/api/attempts/" + id + "/draft").contentType(MediaType.APPLICATION_JSON).content(body(b)), u)
                .andExpect(status().isConflict());
        clock.set(base.plusSeconds(66));
        // DB dùng chung giữa các test: job có thể nộp cả lượt hết hạn của test khác, chỉ kiểm lượt này
        timeoutJob.tick();
        assertEquals(1, jdbc.queryForObject("select count(*) from student_exercise_attempts where id = ? and submitted_at is not null", Integer.class, id));
        assertEquals(0, BigDecimal.ZERO.compareTo(score(id)));
    }

    /** Chạy đua sau ân hạn: job tự nộp và request kèm đáp án đúng cùng lúc: đúng một lần nộp, điểm theo nháp (0), không nhận đáp án gửi kèm. */
    @Test
    void racingJobAndLateRequestSubmitOnceAndIgnoreSentAnswers() throws Exception {
        var b = bank("EXAM");
        String u = enrolled(b);
        Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        for (int round = 0; round < 5; round++) {
            long id = start(b, u, base.plusSeconds(round * 1000L));
            clock.set(base.plusSeconds(round * 1000L + 70));
            var go = new CountDownLatch(1);
            var job = CompletableFuture.supplyAsync(() -> {
                await(go);
                return timeoutJob.tick();
            });
            var req = CompletableFuture.supplyAsync(() -> {
                await(go);
                try {
                    return submit(id, b, u).andReturn().getResponse().getStatus();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            go.countDown();
            int http = req.get(20, TimeUnit.SECONDS);
            job.get(20, TimeUnit.SECONDS);
            assertEquals(true, http == 200 || http == 409, "HTTP " + http);
            assertEquals(1, count("select count(*) from student_exercise_attempts where id = ? and submitted_at is not null", id));
            assertEquals(0, BigDecimal.ZERO.compareTo(score(id)), "đáp án gửi sau ân hạn được chấm (round " + round + ")");
            assertEquals(0, count("select count(*) from student_answers where attempt_id = ? and is_correct", id));
        }
    }

    private static void await(CountDownLatch l) {
        try {
            l.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
