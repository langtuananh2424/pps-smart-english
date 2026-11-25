package com.pps.smartenglish.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.ai.dto.WritingGrade;
import com.pps.smartenglish.ai.service.AttemptCreditSink;
import com.pps.smartenglish.ai.speaking.FakeSpeakingEngineClient;
import com.pps.smartenglish.exercise.dto.SubmitRequest;
import com.pps.smartenglish.exercise.service.SubmissionService;
import com.pps.smartenglish.support.FakeStorageConfig.FakeStorage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

/** D-147: danh sách lượt không lộ điểm tạm, xem lại bộ EXAM sau khi nộp, đua nộp chấm với lượt tự nộp, cờ hoàn lượt. */
class AttemptAiExamRaceTest extends AiTestSupport {

    private static final String ESSAY = "I agree with online learning because it is flexible. However, students need discipline.";
    private static final String CUE = "{\"part\":1,\"prompt\":\"Describe a trip.\"}";

    @Autowired TransactionTemplate tx;
    @Autowired SubmissionService submissions;
    @Autowired AttemptCreditSink sink;
    @Autowired FakeSpeakingEngineClient engine;
    @Autowired FakeStorage storage;

    @BeforeEach
    void resetEngine() {
        engine.reset();
    }

    private static WritingGrade grade(int pct) {
        return new WritingGrade(pct, pct, pct, pct, List.of(), "fixed text", new WritingGrade.Connectives(2, 1, 2, List.of(), "ok"), List.of(), null, "fb");
    }

    private static byte[] wav(double seconds) {
        int data = (int) (seconds * 32000);
        var b = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + data).put("WAVE".getBytes()).put("fmt ".getBytes()).putInt(16).putShort((short) 1)
                .putShort((short) 1).putInt(16000).putInt(32000).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes()).putInt(data);
        return b.array();
    }

    private long startAttempt(Account u, long bank) throws Exception {
        return idOf(postJson("/api/banks/" + bank + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
    }

    private long openWriting(Account u, long q, long attempt) throws Exception {
        guard.resetRateLimit(u.id());
        return idOf(postJson("/api/writing/sessions", u, Map.of("questionId", q, "attemptId", attempt)).andExpect(status().isCreated()), "sessionId");
    }

    private ResultActions submitWriting(Account u, long session) throws Exception {
        guard.resetRateLimit(u.id());
        return postJson("/api/writing/sessions/" + session + "/submit", u, Map.of("finalText", ESSAY), UUID.randomUUID().toString());
    }

    private void makeExam(Account u, long bank) {
        jdbc.update("update question_banks set mode = 'EXAM' where id = ?", bank);
        long course = jdbc.queryForObject("select l.course_id from question_banks b join topics t on t.id = b.topic_id "
                + "join course_levels l on l.id = t.level_id where b.id = ?", Long.class, bank);
        jdbc.update("update courses set exam_bank_id = ?, exam_unlock_percent = 0 where id = ?", bank, course);
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', ?, null)",
                u.id(), course, java.time.OffsetDateTime.parse("2020-01-01T00:00:00Z"));
    }

    @Test
    void attemptListHidesProvisionalScoreWhileJobsPending() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = writingQuestion(bank);
        long attempt = startAttempt(u, bank);
        submitWriting(u, openWriting(u, q, attempt)).andExpect(status().isAccepted());
        postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers", List.of())).andExpect(status().isOk());
        getJson("/api/me/attempts?bankId=" + bank, u).andExpect(jsonPath("$.data[0].submittedAt").exists())
                .andExpect(jsonPath("$.data[0].score").doesNotExist()).andExpect(jsonPath("$.data[0].maxScore").value(1));
        fake.override(WritingGrade.class, grade(70));
        assertEquals(1, runner.runDueNow());
        getJson("/api/me/attempts?bankId=" + bank, u).andExpect(jsonPath("$.data[0].score").value(0.7));
    }

    @Test
    void examSessionReadableAfterSubmitButWritesAreLocked() throws Exception {
        var u = student(5);
        long bank = bank(false);
        long q = writingQuestion(bank);
        makeExam(u, bank);
        long attempt = startAttempt(u, bank);
        long s = openWriting(u, q, attempt);
        submitWriting(u, s).andExpect(status().isAccepted());
        postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers", List.of())).andExpect(status().isOk());
        fake.override(WritingGrade.class, grade(85));
        assertEquals(1, runner.runDueNow());

        getJson("/api/writing/sessions/" + s, u).andExpect(status().isOk()).andExpect(jsonPath("$.grade.scorePercent").value(85));
        guard.resetRateLimit(u.id());
        postJson("/api/writing/sessions/" + s + "/answer", u, Map.of("text", "x")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("attempt_already_submitted"));
    }

    @Test
    void sessionSubmitRacingAttemptSubmitGets409AndKeepsTheQuota() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = writingQuestion(bank);
        long attempt = startAttempt(u, bank);
        long s = openWriting(u, q, attempt);
        var submitted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var pool = Executors.newSingleThreadExecutor();
        try {
            var attemptSubmitter = new Thread(() -> tx.executeWithoutResult(t -> {
                submissions.submit(u.id(), attempt, new SubmitRequest(List.of())); // giữ khóa dòng lượt
                submitted.countDown();
                try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }));
            attemptSubmitter.start();
            submitted.await(10, TimeUnit.SECONDS);
            Future<Integer> late = pool.submit(() -> submitWriting(u, s).andReturn().getResponse().getStatus());
            Thread.sleep(300);
            assertFalse(late.isDone(), "nộp chấm phải chờ khóa lượt");
            release.countDown();
            attemptSubmitter.join(10_000);
            assertEquals(409, late.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(5, balance(u));
        assertEquals(0, count("select count(*) from ai_jobs where user_id = ?", u.id()));
    }

    @Test
    void speakingCompleteRacingAttemptSubmitGets409AndKeepsTheQuota() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = question(bank, "SPEAKING", 1, "Describe a trip.", CUE, "{}");
        long attempt = startAttempt(u, bank);
        guard.resetRateLimit(u.id());
        long id = idOf(postJson("/api/speaking/submissions", u, Map.of("questionId", q, "attemptId", attempt)).andExpect(status().isCreated()), "submissionId");
        storage.put(jdbc.queryForObject("select object_key from speaking_submissions where id = ?", String.class, id), wav(20));
        var submitted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var pool = Executors.newSingleThreadExecutor();
        try {
            var attemptSubmitter = new Thread(() -> tx.executeWithoutResult(t -> {
                submissions.submit(u.id(), attempt, new SubmitRequest(List.of()));
                submitted.countDown();
                try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }));
            attemptSubmitter.start();
            submitted.await(10, TimeUnit.SECONDS);
            guard.resetRateLimit(u.id());
            Future<Integer> late = pool.submit(() -> postJson("/api/speaking/submissions/" + id + "/complete", u, Map.of()).andReturn().getResponse().getStatus());
            Thread.sleep(300);
            assertFalse(late.isDone(), "xác nhận bản nói phải chờ khóa lượt");
            release.countDown();
            attemptSubmitter.join(10_000);
            assertEquals(409, late.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(5, balance(u));
        assertEquals(0, count("select count(*) from ai_jobs where user_id = ?", u.id()));
    }

    @Test
    void failureWithoutRefundIsReportedAsNotRefunded() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = writingQuestion(bank);
        long attempt = startAttempt(u, bank);
        submitWriting(u, openWriting(u, q, attempt)).andExpect(status().isAccepted());
        postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers", List.of())).andExpect(status().isOk());
        jdbc.update("update ai_jobs set status = 'FAILED' where attempt_id = ?", attempt); // giả bài giao: job không trừ lượt
        tx.executeWithoutResult(t -> sink.applyFailure(attempt, q, "AI chấm lỗi", false));
        getJson("/api/attempts/" + attempt, u).andExpect(jsonPath("$.results[0].aiFailure").value("AI chấm lỗi"))
                .andExpect(jsonPath("$.results[0].aiRefunded").value(false));
    }
}
