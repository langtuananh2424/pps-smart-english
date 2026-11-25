package com.pps.smartenglish.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pps.smartenglish.ai.domain.AiJobKind;
import com.pps.smartenglish.ai.domain.AiTask;
import com.pps.smartenglish.ai.dto.WritingGrade;
import com.pps.smartenglish.ai.provider.AiCallException;
import com.pps.smartenglish.ai.service.AiJobService;
import com.pps.smartenglish.ai.service.AttemptLinks;
import com.pps.smartenglish.ai.speaking.FakeSpeakingEngineClient;
import com.pps.smartenglish.ai.speaking.SpeakingEngineException;
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

/** Tester D-147 (vòng 2): thứ tự khóa, tự chấm lại, SSE, đường lách, migration V39. */
class AttemptAiD147TesterTest extends AiTestSupport {

    private static final String ESSAY = "I agree with online learning because it is flexible. However, students need discipline.";
    private static final String CUE = "{\"part\":1,\"prompt\":\"Describe a trip.\"}";

    @Autowired TransactionTemplate tx;
    @Autowired AttemptLinks links;
    @Autowired AiJobService aiJobs;
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

    private long openWriting(Account u, long q, Long attempt) throws Exception {
        guard.resetRateLimit(u.id());
        var body = new java.util.HashMap<String, Object>(Map.of("questionId", q));
        if (attempt != null) {
            body.put("attemptId", attempt);
        }
        return idOf(postJson("/api/writing/sessions", u, body).andExpect(status().isCreated()), "sessionId");
    }

    private ResultActions submitWriting(Account u, long session) throws Exception {
        guard.resetRateLimit(u.id());
        return postJson("/api/writing/sessions/" + session + "/submit", u, Map.of("finalText", ESSAY), UUID.randomUUID().toString());
    }

    private ResultActions submitAttempt(Account u, long attempt) throws Exception {
        return postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers", List.of()));
    }

    private String credit(long attempt, long q) {
        var v = jdbc.queryForObject("select credit from student_answers where attempt_id = ? and question_id = ?", BigDecimal.class, attempt, q);
        return v == null ? null : v.stripTrailingZeros().toPlainString();
    }

    private static byte[] wav(double seconds) {
        int data = (int) (seconds * 32000);
        var b = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + data).put("WAVE".getBytes()).put("fmt ".getBytes()).putInt(16).putShort((short) 1)
                .putShort((short) 1).putInt(16000).putInt(32000).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes()).putInt(data);
        return b.array();
    }

    /** Job đến hạn chạy ngay: bỏ qua backoff của lần thử lại (run_after về quá khứ). */
    private static final java.time.OffsetDateTime PAST = java.time.OffsetDateTime.parse("2000-01-01T00:00:00Z");

    private int runDue(long attempt) {
        jdbc.update("update ai_jobs set run_after = ? where attempt_id = ? and status = 'PENDING'", PAST, attempt);
        return runner.runDueNow();
    }

    // ---------- Mục 3: thứ tự khóa giữa nộp chấm và job lỗi ----------

    /**
     * LỖI (Trung bình): job chấm Viết của lượt còn mở lỗi hẳn thì finisher hoàn lượt (khóa dòng user_ai_quota) RỒI mới khóa
     * dòng lượt (AiCreditApplier.lockById). Nộp chấm câu khác của cùng lượt khóa dòng lượt (requireOpenLocked) RỒI mới trừ
     * lượt (khóa user_ai_quota). Hai thứ tự ngược nhau: PostgreSQL phát hiện deadlock và hủy một bên.
     * Bên A dưới đây gọi đúng chuỗi của WritingService.submit (requireOpenLocked rồi enqueueCharged).
     */
    @Test
    void failingJobRefundAndConcurrentSessionSubmitDoNotDeadlock() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q1 = writingQuestion(bank);
        long q2 = question(bank, "WRITING", 2, "Viết đoạn văn ngắn", "{\"task\":\"Describe your hometown.\",\"requiredWords\":100}", "{}");
        long attempt = startAttempt(u, bank);
        submitWriting(u, openWriting(u, q1, attempt)).andExpect(status().isAccepted());
        long s2 = openWriting(u, q2, attempt);
        assertEquals(4, balance(u));
        fake.failNext(1, new AiCallException(false, "từ chối", null, null)); // lượt còn mở: không tự chấm lại, FAILED + hoàn lượt

        var aLocked = new CountDownLatch(1);
        var bBlocked = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> a = pool.submit(() -> tx.executeWithoutResult(t -> {
                links.requireOpenLocked(attempt);
                aLocked.countDown();
                try { bBlocked.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                guard.resetRateLimit(u.id());
                aiJobs.enqueueCharged(u.id(), AiJobKind.WRITING_GRADE, mapper.createObjectNode().put("sessionId", s2).put("text", ESSAY),
                        s2, attempt, AiTask.WRITING_TASK2, null);
            }));
            aLocked.await(10, TimeUnit.SECONDS);
            Future<Integer> b = pool.submit(() -> runner.runDueNow());
            Thread.sleep(500); // B đã hoàn lượt và đang chờ khóa dòng lượt do A giữ
            assertFalse(b.isDone(), "job lỗi phải đang chờ khóa dòng lượt");
            bBlocked.countDown();
            a.get(30, TimeUnit.SECONDS); // deadlock: PostgreSQL hủy A (hoặc B) sau deadlock_timeout
            assertEquals(1, b.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------- Mục 4: tự chấm lại ----------

    /** Lỗi tạm thời hết số lần thử sau khi nộp: chấm lại đúng một vòng nữa, rồi FAILED, hoàn đúng một lần, câu tính 0. */
    @Test
    void retryableFailuresAfterSubmitGetOneMoreRoundThenRefundOnce() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = writingQuestion(bank);
        long attempt = startAttempt(u, bank);
        submitWriting(u, openWriting(u, q, attempt)).andExpect(status().isAccepted());
        submitAttempt(u, attempt).andExpect(status().isOk());
        fake.failNext(6, new AiCallException(true, "quá tải", null, null));
        for (int i = 0; i < 6; i++) { // 3 lần thử, chấm lại tự động, thêm 3 lần thử
            assertEquals(1, runDue(attempt), "lần " + i);
        }
        assertEquals(0, runDue(attempt), "không được có vòng thứ ba");
        assertEquals("FAILED", jdbc.queryForObject("select status from ai_jobs where attempt_id = ?", String.class, attempt));
        assertTrue(jdbc.queryForObject("select auto_retried from ai_jobs where attempt_id = ?", Boolean.class, attempt));
        assertEquals(5, balance(u));
        assertEquals(1, count("select count(*) from ai_quota_ledger where user_id = ? and reason = 'REFUND'", u.id()));
        assertEquals("0", credit(attempt, q));
        getJson("/api/attempts/" + attempt, u).andExpect(jsonPath("$.passed").value(false))
                .andExpect(jsonPath("$.results[0].aiFailure").value("AI chấm lỗi")).andExpect(jsonPath("$.results[0].aiRefunded").value(true));
    }

    /** Worker chết giữa chừng (lease hết hạn, đủ số lần thử): đường claimer cũng chỉ được chấm lại một lần. */
    @Test
    void staleLeaseAfterSubmitIsRetriedOnceThenFailed() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = writingQuestion(bank);
        long attempt = startAttempt(u, bank);
        submitWriting(u, openWriting(u, q, attempt)).andExpect(status().isAccepted());
        submitAttempt(u, attempt).andExpect(status().isOk());
        String stale = "update ai_jobs set status = 'RUNNING', attempts = 3, locked_until = ? where attempt_id = ?";
        jdbc.update(stale, PAST, attempt);
        runner.runDueNow(); // claimer: hết số lần thử -> chấm lại tự động
        assertEquals("PENDING", jdbc.queryForObject("select status from ai_jobs where attempt_id = ?", String.class, attempt));
        jdbc.update(stale, PAST, attempt);
        runner.runDueNow(); // lần hai: FAILED
        assertEquals("FAILED", jdbc.queryForObject("select status from ai_jobs where attempt_id = ?", String.class, attempt));
        assertEquals(5, balance(u));
        assertEquals("0", credit(attempt, q));
    }

    /** Nói: engine lỗi hẳn sau khi nộp lượt thì chấm lại một lần, vẫn lỗi thì hoàn một lần và câu tính 0 kèm lý do. */
    @Test
    void speakingEngineFailureAfterSubmitIsRetriedOnceThenRefundedOnce() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = question(bank, "SPEAKING", 1, "Describe a trip.", CUE, "{}");
        long attempt = startAttempt(u, bank);
        guard.resetRateLimit(u.id());
        long id = idOf(postJson("/api/speaking/submissions", u, Map.of("questionId", q, "attemptId", attempt)).andExpect(status().isCreated()), "submissionId");
        storage.put(jdbc.queryForObject("select object_key from speaking_submissions where id = ?", String.class, id), wav(20));
        guard.resetRateLimit(u.id());
        postJson("/api/speaking/submissions/" + id + "/complete", u, Map.of()).andExpect(status().isAccepted());
        int charged = balance(u);
        submitAttempt(u, attempt).andExpect(status().isOk()).andExpect(jsonPath("$.score").doesNotExist());
        engine.failNext(2, new SpeakingEngineException(false, "engine hỏng", null));
        assertEquals(1, runner.runDueNow());
        assertEquals("PENDING", jdbc.queryForObject("select status from ai_jobs where attempt_id = ?", String.class, attempt));
        assertEquals(charged, balance(u));
        assertEquals(1, runner.runDueNow());
        assertEquals("FAILED", jdbc.queryForObject("select status from ai_jobs where attempt_id = ?", String.class, attempt));
        assertEquals(charged + 1, balance(u));
        assertEquals(0, runner.runDueNow());
        assertEquals(charged + 1, balance(u));
        assertEquals("0", credit(attempt, q));
        getJson("/api/attempts/" + attempt, u).andExpect(jsonPath("$.passed").value(false))
                .andExpect(jsonPath("$.results[0].aiFailure").value("AI chấm lỗi"));
    }

    /** Phiên luyện tập không gắn lượt: lỗi hẳn thì FAILED và hoàn ngay, không tự chấm lại. */
    @Test
    void autoRetryDoesNotApplyToPracticeSessionWithoutAttempt() throws Exception {
        var u = student(5);
        long q = writingQuestion(bank(true));
        long s = openWriting(u, q, null);
        submitWriting(u, s).andExpect(status().isAccepted());
        fake.failNext(1, new AiCallException(false, "từ chối", null, null));
        assertEquals(1, runner.runDueNow());
        assertEquals("FAILED", jdbc.queryForObject("select j.status from ai_jobs j join writing_sessions w on w.grade_job_id = j.id where w.id = ?", String.class, s));
        assertEquals(5, balance(u));
    }

    // ---------- Mục 1: SSE ----------

    /** Luồng SSE của job chấm phiên gắn lượt còn mở không mang điểm hay bản sửa. */
    @Test
    void sseOfLinkedJobIsSealedWhileAttemptOpen() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = writingQuestion(bank);
        long attempt = startAttempt(u, bank);
        long s = openWriting(u, q, attempt);
        submitWriting(u, s).andExpect(status().isAccepted());
        fake.override(WritingGrade.class, grade(55));
        assertEquals(1, runner.runDueNow());
        long job = count("select grade_job_id from writing_sessions where id = ?", s);
        String body = getJson("/api/ai/jobs/" + job + "/stream", u).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("scorePercent"), body);
        assertFalse(body.contains("fixed text"), body);
        getJson("/api/me/writing", u).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].scorePercent").doesNotExist());
    }

    // ---------- Mục 2: lách bằng phiên không gắn lượt (D-147: đã chặn) ----------

    /** Trong lúc lượt còn mở, không luyện riêng cùng câu: 409 question_in_open_attempt kèm attemptId. */
    @Test
    void practiceSessionForSameQuestionRevealsCorrectionWhileAttemptOpen() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = writingQuestion(bank);
        long attempt = startAttempt(u, bank);
        openWriting(u, q, attempt);
        guard.resetRateLimit(u.id());
        postJson("/api/writing/sessions", u, Map.of("questionId", q)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("question_in_open_attempt"))
                .andExpect(jsonPath("$.error.details.attemptId").value(attempt));
    }

    /** Lượt thứ hai trên cùng bộ khi lượt đầu còn mở: 409 attempt_in_progress kèm attemptId của lượt đang mở. */
    @Test
    void secondAttemptOnSamePracticeBankWhileFirstOpen() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long first = startAttempt(u, bank);
        postJson("/api/banks/" + bank + "/attempts", u, Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("attempt_in_progress"))
                .andExpect(jsonPath("$.error.details.attemptId").value(first));
        assertEquals(1, count("select count(*) from student_exercise_attempts where user_id = ?", u.id()));
    }

    // ---------- Mục 1: learning-profile khi lượt đã nộp còn câu đang chấm ----------

    /**
     * CẦN QUYẾT ĐỊNH (Thấp): lượt đã nộp, câu Viết 1 đã có điểm, câu Viết 2 đang chấm. GET lượt ẩn điểm, phiên câu 1 bị khóa kết
     * quả, nhưng /api/me/learning-profile đã cộng credit câu 1 vào nhóm WRITING nên suy ra được điểm câu 1.
     */
    
    @Test
    void learningProfileDoesNotRevealCreditOfAttemptStillGrading() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q1 = writingQuestion(bank);
        long q2 = question(bank, "WRITING", 2, "Viết đoạn văn ngắn", "{\"task\":\"Describe your hometown.\",\"requiredWords\":100}", "{}");
        long attempt = startAttempt(u, bank);
        submitWriting(u, openWriting(u, q1, attempt)).andExpect(status().isAccepted());
        fake.override(WritingGrade.class, grade(60));
        assertEquals(1, runner.runDueNow());
        submitWriting(u, openWriting(u, q2, attempt)).andExpect(status().isAccepted());
        submitAttempt(u, attempt).andExpect(status().isOk()).andExpect(jsonPath("$.score").doesNotExist());
        assertEquals("0.6", credit(attempt, q1));
        var profile = json.readTree(getJson("/api/me/learning-profile", u).andReturn().getResponse().getContentAsString());
        assertEquals(0, profile.path("credit").decimalValue().signum(), profile.toString());
    }

    // ---------- Mục 5: migration V39 tương thích ngược ----------

    /** Bản cũ (chưa biết cột mới) chèn dòng không có cột V39: mặc định đúng, không lỗi NOT NULL. */
    @Test
    void v39ColumnsHaveSafeDefaultsForOldWriters() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = writingQuestion(bank);
        long attempt = startAttempt(u, bank);
        submitAttempt(u, attempt).andExpect(status().isOk());
        jdbc.update("insert into ai_jobs (user_id, kind, status, attempt_id) values (?, 'WRITING_GRADE', 'DONE', ?)", u.id(), attempt);
        assertFalse(jdbc.queryForObject("select auto_retried from ai_jobs where attempt_id = ?", Boolean.class, attempt));
        jdbc.update("delete from student_answers where attempt_id = ?", attempt);
        jdbc.update("insert into student_answers (attempt_id, question_id, answer, credit) values (?, ?, null, 0)", attempt, q);
        assertFalse(jdbc.queryForObject("select ai_fail_refunded from student_answers where attempt_id = ?", Boolean.class, attempt));
        assertEquals(null, jdbc.queryForObject("select ai_fail_reason from student_answers where attempt_id = ?", String.class, attempt));
    }
}
