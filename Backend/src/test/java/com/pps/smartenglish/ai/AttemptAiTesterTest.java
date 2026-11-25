package com.pps.smartenglish.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pps.smartenglish.ai.dto.WritingGrade;
import com.pps.smartenglish.ai.provider.AiCallException;
import com.pps.smartenglish.ai.service.AttemptAiScores;
import com.pps.smartenglish.ai.speaking.FakeSpeakingEngineClient;
import com.pps.smartenglish.exercise.dto.SubmitRequest;
import com.pps.smartenglish.exercise.service.SubmissionService;
import com.pps.smartenglish.support.FakeStorageConfig.FakeStorage;
import java.math.BigDecimal;
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

/** Tester D-145 (hướng b): đua nộp bài/kết quả AI, passed null, chặn EXAM, lộ điểm trong lượt đang mở. */
class AttemptAiTesterTest extends AiTestSupport {

    private static final String ESSAY = "I agree with online learning because it is flexible. However, students need discipline.";
    private static final String CUE = "{\"part\":1,\"prompt\":\"Describe a trip.\"}";

    @Autowired TransactionTemplate tx;
    @Autowired SubmissionService submissions;
    @Autowired AttemptAiScores scores;
    @Autowired FakeSpeakingEngineClient engine;
    @Autowired FakeStorage storage;
    @Autowired ObjectMapper mapper;

    @BeforeEach
    void resetEngine() {
        engine.reset();
    }

    private static WritingGrade grade(int pct) {
        return new WritingGrade(pct, pct, pct, pct, List.of(), "fixed text", new WritingGrade.Connectives(2, 1, 2, List.of(), "ok"), List.of(), null, "fb");
    }

    private long startAttempt(Account u, long bank) throws Exception {
        return idOf(postJson("/api/banks/" + bank + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
    }

    private ResultActions openSession(Account u, long q, Long attempt) throws Exception {
        guard.resetRateLimit(u.id());
        var body = new java.util.HashMap<String, Object>();
        body.put("questionId", q);
        if (attempt != null) {
            body.put("attemptId", attempt);
        }
        return postJson("/api/writing/sessions", u, body);
    }

    private long submittedSession(Account u, long q, long attempt) throws Exception {
        long s = idOf(openSession(u, q, attempt).andExpect(status().isCreated()), "sessionId");
        guard.resetRateLimit(u.id());
        postJson("/api/writing/sessions/" + s + "/submit", u, Map.of("finalText", ESSAY), UUID.randomUUID().toString())
                .andExpect(status().isAccepted());
        return s;
    }

    private void makeExam(Account u, long bank) {
        jdbc.update("update question_banks set mode = 'EXAM' where id = ?", bank);
        long course = jdbc.queryForObject("select l.course_id from question_banks b join topics t on t.id = b.topic_id "
                + "join course_levels l on l.id = t.level_id where b.id = ?", Long.class, bank);
        jdbc.update("update courses set exam_bank_id = ?, exam_unlock_percent = 0 where id = ?", bank, course);
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', ?, null)",
                u.id(), course, java.time.OffsetDateTime.parse("2020-01-01T00:00:00Z"));
    }

    private String credit(long attempt, long q) {
        var v = jdbc.queryForObject("select credit from student_answers where attempt_id = ? and question_id = ?", BigDecimal.class, attempt, q);
        return v == null ? null : v.stripTrailingZeros().toPlainString();
    }

    // ---------- Đua và trùng lặp ----------

    /** Nộp bài và job chấm chạy thật sự đồng thời, lặp nhiều lần: điểm cuối luôn đúng 0.8, không mất, không gấp đôi. */
    @Test
    void concurrentSubmitAndGradeNeverLosesNorDoublesCredit() throws Exception {
        fake.override(WritingGrade.class, grade(80));
        var pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 12; i++) {
                var u = student(5);
                long bank = bank(true);
                long q = writingQuestion(bank);
                long attempt = startAttempt(u, bank);
                submittedSession(u, q, attempt);
                var gate = new CountDownLatch(1);
                Future<Integer> grader = pool.submit(() -> { gate.await(); return runner.runDueNow(); });
                Future<Integer> submit = pool.submit(() -> {
                    gate.await();
                    return postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers", List.of())).andReturn().getResponse().getStatus();
                });
                gate.countDown();
                assertEquals(1, grader.get(30, TimeUnit.SECONDS), "vòng " + i);
                assertEquals(200, submit.get(30, TimeUnit.SECONDS), "vòng " + i);
                getJson("/api/attempts/" + attempt, u).andExpect(jsonPath("$.score").value(0.8)).andExpect(jsonPath("$.passed").value(true));
                assertEquals("0.8", credit(attempt, q), "vòng " + i);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /** Kết quả AI giữ khóa lượt trước (job DONE chưa commit): nộp bài phải chờ rồi đọc thấy điểm. */
    @Test
    void gradeCommittingFirstBlocksSubmitWhichThenSeesTheCredit() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = writingQuestion(bank);
        long attempt = startAttempt(u, bank);
        long session = submittedSession(u, q, attempt);
        long job = count("select grade_job_id from writing_sessions where id = ?", session);
        var output = ((com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(grade(70))).put("scorePercent", 70); // postCheck thêm scorePercent
        var pool = Executors.newSingleThreadExecutor();
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            var grader = new Thread(() -> tx.executeWithoutResult(s -> {
                jdbc.update("update ai_jobs set status = 'DONE', output = ? format json where id = ?", output.toString(), job);
                scores.onWritingGraded(session, output); // khóa dòng lượt
                locked.countDown();
                try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }));
            grader.start();
            locked.await(10, TimeUnit.SECONDS);
            Future<Integer> submit = pool.submit(() ->
                    postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers", List.of())).andReturn().getResponse().getStatus());
            Thread.sleep(300); // nộp bài phải đang chờ khóa
            assertFalse(submit.isDone(), "nộp bài không được chạy qua khóa của transaction chấm");
            release.countDown();
            grader.join(10_000);
            assertEquals(200, submit.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals("0.7", credit(attempt, q));
        getJson("/api/attempts/" + attempt, u).andExpect(jsonPath("$.score").value(0.7)).andExpect(jsonPath("$.passed").value(true));
    }

    /** Nộp bài giữ khóa lượt trước: transaction chấm phải chờ rồi ghi điểm vào câu trả lời. */
    @Test
    void submitCommittingFirstBlocksGradeWhichThenAppliesTheCredit() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = writingQuestion(bank);
        long attempt = startAttempt(u, bank);
        long session = submittedSession(u, q, attempt);
        long job = count("select grade_job_id from writing_sessions where id = ?", session);
        var output = ((com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(grade(90))).put("scorePercent", 90);
        var submitted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var pool = Executors.newSingleThreadExecutor();
        try {
            var submitter = new Thread(() -> tx.executeWithoutResult(s -> {
                submissions.submit(u.id(), attempt, new SubmitRequest(List.of()));
                submitted.countDown();
                try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }));
            submitter.start();
            submitted.await(10, TimeUnit.SECONDS);
            Future<?> grader = pool.submit(() -> tx.executeWithoutResult(s -> {
                jdbc.update("update ai_jobs set status = 'DONE', output = ? format json where id = ?", output.toString(), job);
                scores.onWritingGraded(session, output);
            }));
            Thread.sleep(300);
            assertFalse(grader.isDone(), "kết quả AI không được ghi khi lượt đang bị nộp giữ khóa");
            release.countDown();
            submitter.join(10_000);
            grader.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals("0.9", credit(attempt, q));
        getJson("/api/attempts/" + attempt, u).andExpect(jsonPath("$.score").value(0.9)).andExpect(jsonPath("$.passed").value(true));
    }

    /** Job về trùng (finisher gọi effect hai lần với điểm khác): chỉ lần đầu được tính. */
    @Test
    void duplicateGradeCallbackDoesNotOverwriteOrAdd() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = writingQuestion(bank);
        long attempt = startAttempt(u, bank);
        long session = submittedSession(u, q, attempt);
        postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers", List.of())).andExpect(status().isOk());
        fake.override(WritingGrade.class, grade(60));
        assertEquals(1, runner.runDueNow());
        tx.executeWithoutResult(s -> scores.onWritingGraded(session, ((com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(grade(100))).put("scorePercent", 100)));
        assertEquals(0, runner.runDueNow());
        assertEquals("0.6", credit(attempt, q));
        getJson("/api/attempts/" + attempt, u).andExpect(jsonPath("$.score").value(0.6)).andExpect(jsonPath("$.passed").value(false));
    }

    // ---------- passed = null ----------

    /** Hết lượt AI: phiên không nộp chấm được, câu chờ, passed null. */
    @Test
    void quotaExhaustedSettlesAsZero() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = writingQuestion(bank);
        long attempt = startAttempt(u, bank);
        long s = idOf(openSession(u, q, attempt).andExpect(status().isCreated()), "sessionId");
        jdbc.update("update user_ai_quota set balance = 0 where user_id = ?", u.id());
        guard.resetRateLimit(u.id());
        postJson("/api/writing/sessions/" + s + "/submit", u, Map.of("finalText", ESSAY), UUID.randomUUID().toString())
                .andExpect(status().is4xxClientError());
        // D-147: hết lượt AI thì câu tính 0 ngay, passed chốt
        postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers", List.of())).andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(false)).andExpect(jsonPath("$.maxScore").value(1));
        assertEquals("0", credit(attempt, q));
    }

    @Test
    void failedWritingJobSettlesAsZeroAfterOneRetry() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = writingQuestion(bank);
        long attempt = startAttempt(u, bank);
        submittedSession(u, q, attempt);
        postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers", List.of())).andExpect(status().isOk());
        fake.failNext(2, new AiCallException(false, "từ chối", null, null)); // D-147: chấm lại tự động một lần rồi mới bỏ cuộc
        assertEquals(1, runner.runDueNow());
        assertEquals(1, runner.runDueNow());
        assertEquals("FAILED", jdbc.queryForObject("select status from ai_jobs where attempt_id = ?", String.class, attempt));
        getJson("/api/attempts/" + attempt, u).andExpect(jsonPath("$.passed").value(false)).andExpect(jsonPath("$.passPct").value(70));
        assertEquals("0", credit(attempt, q));
    }

    private static byte[] wav(double seconds) {
        int data = (int) (seconds * 32000);
        var b = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + data).put("WAVE".getBytes()).put("fmt ".getBytes()).putInt(16).putShort((short) 1)
                .putShort((short) 1).putInt(16000).putInt(32000).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes()).putInt(data);
        return b.array();
    }

    @Test
    void unclearSpeakingSettlesAsZeroAfterOneRetry() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = question(bank, "SPEAKING", 1, "Describe a trip.", CUE, "{}");
        long attempt = startAttempt(u, bank);
        guard.resetRateLimit(u.id());
        long id = idOf(postJson("/api/speaking/submissions", u, Map.of("questionId", q, "attemptId", attempt)).andExpect(status().isCreated()), "submissionId");
        storage.put(jdbc.queryForObject("select object_key from speaking_submissions where id = ?", String.class, id), wav(20));
        guard.resetRateLimit(u.id());
        postJson("/api/speaking/submissions/" + id + "/complete", u, Map.of()).andExpect(status().isAccepted());
        postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers", List.of())).andExpect(status().isOk());
        engine.setTranscript(FakeSpeakingEngineClient.spoken(3, 0.1, 0.9)); // dưới 5 từ: UNCLEAR
        assertEquals(1, runner.runDueNow());
        assertEquals(1, runner.runDueNow()); // D-147: chấm lại một lần rồi mới UNCLEAR hẳn

        assertEquals("UNCLEAR", jdbc.queryForObject("select status from speaking_submissions where id = ?", String.class, id));
        getJson("/api/attempts/" + attempt, u).andExpect(jsonPath("$.passed").value(false)).andExpect(jsonPath("$.score").value(0));
        assertEquals("0", credit(attempt, q));
    }

    // ---------- Phân quyền theo bản ghi, chặn EXAM ----------

    @Test
    void fakeOrForeignBankAttemptCannotUnlockExamQuestion() throws Exception {
        var u = student(5);
        long examBank = bank(false);
        long wq = writingQuestion(examBank);
        long sq = question(examBank, "SPEAKING", 2, "Describe a trip.", CUE, "{}");
        makeExam(u, examBank);
        long otherAttempt = startAttempt(u, bank(true)); // lượt thật của chính học viên nhưng bộ khác

        openSession(u, wq, 99_999_999L).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("attempt_not_found"));
        openSession(u, wq, otherAttempt).andExpect(status().isBadRequest());
        getJson("/api/speaking/questions/" + sq + "?attemptId=" + otherAttempt, u).andExpect(status().isBadRequest());
        getJson("/api/speaking/questions/" + sq + "?attemptId=99999999", u).andExpect(status().isNotFound());
        getJson("/api/speaking/questions/" + sq + "?attemptId=abc", u).andExpect(status().isNotFound());
        guard.resetRateLimit(u.id());
        postJson("/api/speaking/submissions", u, Map.of("questionId", sq, "attemptId", otherAttempt)).andExpect(status().isBadRequest());
        assertEquals(0, count("select count(*) from writing_sessions where user_id = ?", u.id()));
        assertEquals(0, count("select count(*) from speaking_submissions where user_id = ?", u.id()));
    }

    @Test
    void submittedAttemptRejectsSpeakingQuestionViewAndNewSubmission() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long sq = question(bank, "SPEAKING", 1, "Describe a trip.", CUE, "{}");
        long attempt = startAttempt(u, bank);
        postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers", List.of())).andExpect(status().isOk());
        getJson("/api/speaking/questions/" + sq + "?attemptId=" + attempt, u).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("attempt_already_submitted"));
        guard.resetRateLimit(u.id());
        postJson("/api/speaking/submissions", u, Map.of("questionId", sq, "attemptId", attempt)).andExpect(status().isConflict());
    }

    @Test
    void anotherUsersAiSessionsAndSpeakingSubmissionAreHidden() throws Exception {
        var u = student(5);
        var other = student(5);
        long bank = bank(true);
        long sq = question(bank, "SPEAKING", 1, "Describe a trip.", CUE, "{}");
        long attempt = startAttempt(u, bank);
        guard.resetRateLimit(other.id());
        postJson("/api/speaking/submissions", other, Map.of("questionId", sq, "attemptId", attempt)).andExpect(status().isNotFound());
        getJson("/api/attempts/" + attempt + "/ai-sessions", other).andExpect(status().isNotFound());
        getJson("/api/attempts/abc/ai-sessions", u).andExpect(status().isNotFound());
    }

    // ---------- Lộ điểm ----------

    /**
     * LỖI (Cao): web ẩn điểm khi làm bài (sealed) nhưng API phiên vẫn trả grade (điểm + bản sửa) trong lúc lượt còn mở.
     * Học viên đọc bản sửa qua API, mở phiên mới (phiên mới nhất thắng) và nộp lại bản sửa để nâng điểm bộ, kể cả bộ EXAM.
     */
    @Test
    void writingGradeOfLinkedSessionIsSealedWhileAttemptIsOpen() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = writingQuestion(bank);
        long attempt = startAttempt(u, bank);
        long s = submittedSession(u, q, attempt);
        fake.override(WritingGrade.class, grade(40));
        assertEquals(1, runner.runDueNow());
        long job = count("select grade_job_id from writing_sessions where id = ?", s);
        getJson("/api/writing/sessions/" + s, u).andExpect(status().isOk()).andExpect(jsonPath("$.grade").doesNotExist());
        getJson("/api/ai/jobs/" + job, u).andExpect(jsonPath("$.result.scorePercent").doesNotExist());
    }

    /** D-147: phiên gắn lượt bị khóa khi lượt đã nộp (hoặc tự nộp lúc hết giờ): nộp chấm muộn trả 409, điểm lượt giữ nguyên. */
    @Test
    void linkedSessionLockedAfterAttemptSubmitted() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = writingQuestion(bank);
        long attempt = startAttempt(u, bank);
        long s = idOf(openSession(u, q, attempt).andExpect(status().isCreated()), "sessionId");
        postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers", List.of())).andExpect(status().isOk());
        guard.resetRateLimit(u.id());
        postJson("/api/writing/sessions/" + s + "/submit", u, Map.of("finalText", ESSAY), UUID.randomUUID().toString())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("attempt_already_submitted"));
        assertEquals(0, runner.runDueNow());
        getJson("/api/attempts/" + attempt, u).andExpect(jsonPath("$.score").value(0)).andExpect(jsonPath("$.passed").value(false));
    }
}
