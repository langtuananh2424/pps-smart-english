package com.pps.smartenglish.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.ai.dto.WritingGrade;
import com.pps.smartenglish.ai.provider.AiCallException;
import com.pps.smartenglish.ai.speaking.FakeSpeakingEngineClient;
import com.pps.smartenglish.support.FakeStorageConfig.FakeStorage;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

/** D-147: khóa kết quả phiên trong lúc làm bài, câu chưa chấm tính 0 lúc nộp, phiên khóa sau nộp, chấm lỗi thì chấm lại một lần. */
class AttemptAiSealRetryTest extends AiTestSupport {

    private static final String ESSAY = "I agree with online learning because it is flexible. However, students need discipline.";
    private static final String CUE = "{\"part\":1,\"prompt\":\"Describe a trip.\"}";

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

    private ResultActions submitAttempt(Account u, long attempt) throws Exception {
        return postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers", List.of()));
    }

    /** Mở bản nộp Nói gắn lượt và tải file lên, chưa xác nhận. */
    private long openSpeaking(Account u, long q, long attempt) throws Exception {
        guard.resetRateLimit(u.id());
        long id = idOf(postJson("/api/speaking/submissions", u, Map.of("questionId", q, "attemptId", attempt)).andExpect(status().isCreated()), "submissionId");
        storage.put(jdbc.queryForObject("select object_key from speaking_submissions where id = ?", String.class, id), wav(20));
        return id;
    }

    private ResultActions completeSpeaking(Account u, long id) throws Exception {
        guard.resetRateLimit(u.id());
        return postJson("/api/speaking/submissions/" + id + "/complete", u, Map.of());
    }

    private String credit(long attempt, long q) {
        var v = jdbc.queryForObject("select credit from student_answers where attempt_id = ? and question_id = ?", BigDecimal.class, attempt, q);
        return v == null ? null : v.stripTrailingZeros().toPlainString();
    }

    // ---------- Mục 1: khóa kết quả trong lúc lượt còn mở ----------

    @Test
    void speakingResultIsSealedWhileAttemptIsOpenThenShownWhenSettled() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = question(bank, "SPEAKING", 1, "Describe a trip.", CUE, "{}");
        long attempt = startAttempt(u, bank);
        long id = openSpeaking(u, q, attempt);
        completeSpeaking(u, id).andExpect(status().isAccepted());
        assertEquals(1, runner.runDueNow());
        assertEquals("GRADED", jdbc.queryForObject("select status from speaking_submissions where id = ?", String.class, id));

        getJson("/api/speaking/submissions/" + id, u).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("GRADED"))
                .andExpect(jsonPath("$.result").doesNotExist());
        getJson("/api/me/speaking", u).andExpect(jsonPath("$.data[0].result").doesNotExist());

        submitAttempt(u, attempt).andExpect(status().isOk()).andExpect(jsonPath("$.pendingJobs.length()").value(0));
        getJson("/api/speaking/submissions/" + id, u).andExpect(jsonPath("$.result.overall").exists());
    }

    @Test
    void jobResultStaysSealedUntilAttemptSubmittedAndEverythingGraded() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = writingQuestion(bank);
        long attempt = startAttempt(u, bank);
        long s = openWriting(u, q, attempt);
        submitWriting(u, s).andExpect(status().isAccepted());
        fake.override(WritingGrade.class, grade(60));
        assertEquals(1, runner.runDueNow());
        long job = count("select grade_job_id from writing_sessions where id = ?", s);
        getJson("/api/ai/jobs/" + job, u).andExpect(jsonPath("$.status").value("DONE")).andExpect(jsonPath("$.result").doesNotExist());
        getJson("/api/writing/sessions/" + s, u).andExpect(jsonPath("$.grade").doesNotExist());
        getJson("/api/me/writing", u).andExpect(jsonPath("$.data[0].scorePercent").doesNotExist());

        submitAttempt(u, attempt).andExpect(status().isOk());
        getJson("/api/ai/jobs/" + job, u).andExpect(jsonPath("$.result.scorePercent").value(60));
        getJson("/api/writing/sessions/" + s, u).andExpect(jsonPath("$.grade.scorePercent").value(60));
    }

    // ---------- Mục 2: không có điểm tạm ở API ----------

    @Test
    void provisionalScoreIsHiddenWhileGradingAndShownAfter() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = writingQuestion(bank);
        long attempt = startAttempt(u, bank);
        submitWriting(u, openWriting(u, q, attempt)).andExpect(status().isAccepted());
        submitAttempt(u, attempt).andExpect(status().isOk()).andExpect(jsonPath("$.maxScore").value(1))
                .andExpect(jsonPath("$.pendingJobs.length()").value(1)).andExpect(jsonPath("$.score").doesNotExist())
                .andExpect(jsonPath("$.results").doesNotExist()).andExpect(jsonPath("$.passed").doesNotExist());
        getJson("/api/attempts/" + attempt, u).andExpect(jsonPath("$.maxScore").value(1)).andExpect(jsonPath("$.score").doesNotExist())
                .andExpect(jsonPath("$.results").doesNotExist()).andExpect(jsonPath("$.breakdown").doesNotExist())
                .andExpect(jsonPath("$.passed").doesNotExist());
        fake.override(WritingGrade.class, grade(80));
        assertEquals(1, runner.runDueNow());
        getJson("/api/attempts/" + attempt, u).andExpect(jsonPath("$.score").value(0.8)).andExpect(jsonPath("$.passed").value(true))
                .andExpect(jsonPath("$.results").exists());
    }

    // ---------- Mục 3a: câu chưa chấm tính 0 lúc nộp ----------

    @Test
    void unsubmittedSessionCountsZeroAndSettlesVerdictAtSubmit() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = writingQuestion(bank);
        long attempt = startAttempt(u, bank);
        openWriting(u, q, attempt); // mở mà không nộp chấm
        submitAttempt(u, attempt).andExpect(status().isOk()).andExpect(jsonPath("$.pendingJobs.length()").value(0))
                .andExpect(jsonPath("$.score").value(0)).andExpect(jsonPath("$.passed").value(false));
        assertEquals("0", credit(attempt, q));
    }

    @Test
    void speakingNotConfirmedCountsZeroAtSubmit() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = question(bank, "SPEAKING", 1, "Describe a trip.", CUE, "{}");
        long attempt = startAttempt(u, bank);
        openSpeaking(u, q, attempt);
        submitAttempt(u, attempt).andExpect(status().isOk()).andExpect(jsonPath("$.passed").value(false));
        assertEquals("0", credit(attempt, q));
    }

    @Test
    void outOfQuotaSessionCountsZeroAtSubmit() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = writingQuestion(bank);
        long attempt = startAttempt(u, bank);
        long s = openWriting(u, q, attempt);
        jdbc.update("update user_ai_quota set balance = 0 where user_id = ?", u.id());
        submitWriting(u, s).andExpect(status().is4xxClientError());
        submitAttempt(u, attempt).andExpect(status().isOk()).andExpect(jsonPath("$.passed").value(false));
        assertEquals("0", credit(attempt, q));
    }

    // ---------- Mục 3b: phiên khóa sau khi lượt đã nộp ----------

    @Test
    void sessionIsLockedOnceAttemptSubmitted() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = writingQuestion(bank);
        long sq = question(bank, "SPEAKING", 2, "Describe a trip.", CUE, "{}");
        long attempt = startAttempt(u, bank);
        long s = openWriting(u, q, attempt);
        long sp = openSpeaking(u, sq, attempt);
        submitAttempt(u, attempt).andExpect(status().isOk());

        submitWriting(u, s).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("attempt_already_submitted"));
        guard.resetRateLimit(u.id());
        postJson("/api/writing/sessions/" + s + "/answer", u, Map.of("text", "x")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("attempt_already_submitted"));
        guard.resetRateLimit(u.id());
        postJson("/api/writing/sessions/" + s + "/draft", u, Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("attempt_already_submitted"));
        completeSpeaking(u, sp).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("attempt_already_submitted"));
        assertEquals(0, count("select count(*) from ai_jobs where user_id = ?", u.id()));
    }

    // ---------- Mục 3c: chấm lỗi thì chấm lại một lần, rồi hoàn lượt và tính 0 ----------

    @Test
    void failureThenSuccessOnAutoRetryGivesTheScore() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = writingQuestion(bank);
        long attempt = startAttempt(u, bank);
        submitWriting(u, openWriting(u, q, attempt)).andExpect(status().isAccepted());
        submitAttempt(u, attempt).andExpect(status().isOk());
        fake.failNext(1, new AiCallException(false, "từ chối", null, null));
        assertEquals(1, runner.runDueNow());
        assertEquals("PENDING", jdbc.queryForObject("select status from ai_jobs where attempt_id = ?", String.class, attempt));
        getJson("/api/attempts/" + attempt, u).andExpect(jsonPath("$.pendingJobs.length()").value(1)).andExpect(jsonPath("$.score").doesNotExist());

        fake.override(WritingGrade.class, grade(90));
        assertEquals(1, runner.runDueNow());
        getJson("/api/attempts/" + attempt, u).andExpect(jsonPath("$.score").value(0.9)).andExpect(jsonPath("$.passed").value(true))
                .andExpect(jsonPath("$.results[0].aiFailure").doesNotExist());
        assertEquals(4, balance(u)); // một lượt đã dùng, không hoàn
    }

    @Test
    void failingTwiceRefundsOnceCountsZeroAndSettlesVerdict() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = writingQuestion(bank);
        long attempt = startAttempt(u, bank);
        submitWriting(u, openWriting(u, q, attempt)).andExpect(status().isAccepted());
        assertEquals(4, balance(u));
        submitAttempt(u, attempt).andExpect(status().isOk());
        fake.failNext(2, new AiCallException(false, "từ chối", null, null));
        assertEquals(1, runner.runDueNow());
        assertEquals(4, balance(u)); // lần lỗi đầu chưa hoàn
        assertEquals(1, runner.runDueNow());
        assertEquals("FAILED", jdbc.queryForObject("select status from ai_jobs where attempt_id = ?", String.class, attempt));
        assertEquals(5, balance(u)); // hoàn đúng một lần
        assertEquals("0", credit(attempt, q));
        getJson("/api/attempts/" + attempt, u).andExpect(jsonPath("$.passed").value(false)).andExpect(jsonPath("$.score").value(0))
                .andExpect(jsonPath("$.results[0].aiFailure").value("Cam chưa chấm được câu này"));
        assertEquals(0, runner.runDueNow()); // không còn gì để chạy lại, không hoàn lần hai
        assertEquals(5, balance(u));
    }

    @Test
    void unclearSpeakingIsRetriedOnceThenRefundedAndCountsZero() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = question(bank, "SPEAKING", 1, "Describe a trip.", CUE, "{}");
        long attempt = startAttempt(u, bank);
        long id = openSpeaking(u, q, attempt);
        completeSpeaking(u, id).andExpect(status().isAccepted());
        int charged = balance(u);
        submitAttempt(u, attempt).andExpect(status().isOk());
        engine.setTranscript(FakeSpeakingEngineClient.spoken(3, 0.1, 0.9)); // dưới 5 từ: UNCLEAR
        assertEquals(1, runner.runDueNow());
        assertEquals("GRADING", jdbc.queryForObject("select status from speaking_submissions where id = ?", String.class, id));
        assertEquals(charged, balance(u));
        assertEquals(1, runner.runDueNow()); // lần chấm lại vẫn không rõ

        assertEquals("UNCLEAR", jdbc.queryForObject("select status from speaking_submissions where id = ?", String.class, id));
        assertEquals(charged + 1, balance(u)); // hoàn đúng một lần (trọng số 1 trong test)
        assertEquals("0", credit(attempt, q));
        getJson("/api/attempts/" + attempt, u).andExpect(jsonPath("$.passed").value(false))
                .andExpect(jsonPath("$.results[0].aiFailure").value("bản ghi âm không rõ"));
    }

    @Test
    void failureBeforeSubmitIsNotAutoRetriedAndCountsZeroWithReason() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = writingQuestion(bank);
        long attempt = startAttempt(u, bank);
        submitWriting(u, openWriting(u, q, attempt)).andExpect(status().isAccepted());
        fake.failNext(1, new AiCallException(false, "từ chối", null, null));
        assertEquals(1, runner.runDueNow());
        assertEquals("FAILED", jdbc.queryForObject("select status from ai_jobs where attempt_id = ?", String.class, attempt));
        assertEquals(5, balance(u));
        var body = new HashMap<String, Object>(Map.of("answers", List.of()));
        postJson("/api/attempts/" + attempt + "/submit", u, body).andExpect(status().isOk()).andExpect(jsonPath("$.passed").value(false))
                .andExpect(jsonPath("$.results[0].aiFailure").value("Cam chưa chấm được câu này"));
    }
}
