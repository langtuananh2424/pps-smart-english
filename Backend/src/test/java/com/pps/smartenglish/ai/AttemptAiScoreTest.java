package com.pps.smartenglish.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.ai.dto.SpeakingGrade;
import com.pps.smartenglish.ai.dto.WritingGrade;
import com.pps.smartenglish.ai.service.AttemptCreditSink;
import com.pps.smartenglish.support.FakeStorageConfig.FakeStorage;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

/** D-145 (hướng b): điểm câu Viết/Nói cộng vào điểm lượt, Đạt/Chưa đạt chốt khi chấm xong. */
class AttemptAiScoreTest extends AiTestSupport {

    private static final String ESSAY = "I agree with online learning because it is flexible. However, students need discipline.";

    @Autowired AttemptCreditSink sink;
    @Autowired TransactionTemplate tx;
    @Autowired FakeStorage storage;

    private static WritingGrade grade(int pct) {
        return new WritingGrade(pct, pct, pct, pct, List.of(), "fixed", new WritingGrade.Connectives(2, 1, 2, List.of(), "ok"), List.of(), null, "fb");
    }

    private long startAttempt(Account u, long bank) throws Exception {
        return idOf(postJson("/api/banks/" + bank + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
    }

    private ResultActions openSession(Account u, long q, long attempt) throws Exception {
        guard.resetRateLimit(u.id());
        return postJson("/api/writing/sessions", u, Map.of("questionId", q, "attemptId", attempt));
    }

    private long linkedSession(Account u, long q, long attempt) throws Exception {
        return idOf(openSession(u, q, attempt).andExpect(status().isCreated()), "sessionId");
    }

    private void submitWriting(Account u, long session) throws Exception {
        postJson("/api/writing/sessions/" + session + "/submit", u, Map.of("finalText", ESSAY), UUID.randomUUID().toString())
                .andExpect(status().isAccepted());
    }

    private ResultActions submitAttempt(Account u, long attempt) throws Exception {
        return postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers", List.of())).andExpect(status().isOk());
    }

    @Test
    void writingGradedAfterSubmitRaisesScoreAndSettlesVerdict() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = writingQuestion(bank);
        long attempt = startAttempt(u, bank);
        submitWriting(u, linkedSession(u, q, attempt));
        assertEquals(attempt, count("select attempt_id from ai_jobs where kind = 'WRITING_GRADE' and user_id = ?", u.id()));

        submitAttempt(u, attempt).andExpect(jsonPath("$.maxScore").value(1)).andExpect(jsonPath("$.score").doesNotExist())
                .andExpect(jsonPath("$.passed").doesNotExist()).andExpect(jsonPath("$.pendingJobs.length()").value(1)); // D-147: không điểm tạm

        fake.override(WritingGrade.class, grade(80));
        assertEquals(1, runner.runDueNow());
        getJson("/api/attempts/" + attempt, u).andExpect(jsonPath("$.score").value(0.8)).andExpect(jsonPath("$.passed").value(true))
                .andExpect(jsonPath("$.pendingJobs.length()").value(0));

        // kết quả đến trùng: không cộng lần hai
        tx.executeWithoutResult(s -> sink.applyCredit(attempt, q, BigDecimal.ONE));
        getJson("/api/attempts/" + attempt, u).andExpect(jsonPath("$.score").value(0.8));
    }

    @Test
    void linkedButUngradedSessionKeepsVerdictOpen() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = writingQuestion(bank);
        long attempt = startAttempt(u, bank);
        linkedSession(u, q, attempt); // mở phiên nhưng không nộp chấm (hoặc hết lượt AI không nộp được)
        // D-147: phiên mở mà không nộp chấm tính 0 ngay, không chờ
        submitAttempt(u, attempt).andExpect(jsonPath("$.passed").value(false)).andExpect(jsonPath("$.score").value(0))
                .andExpect(jsonPath("$.maxScore").value(1));
    }

    @Test
    void blankWritingCountsZeroWithoutWaiting() throws Exception {
        var u = student(5);
        long bank = bank(true);
        writingQuestion(bank);
        long attempt = startAttempt(u, bank);
        submitAttempt(u, attempt).andExpect(jsonPath("$.maxScore").value(1)).andExpect(jsonPath("$.score").value(0))
                .andExpect(jsonPath("$.passed").value(false)).andExpect(jsonPath("$.pendingJobs.length()").value(0));
    }

    @Test
    void latestSessionOfTheQuestionWins() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = writingQuestion(bank);
        long attempt = startAttempt(u, bank);
        fake.override(WritingGrade.class, grade(80));
        submitWriting(u, linkedSession(u, q, attempt));
        assertEquals(1, runner.runDueNow()); // phiên cũ đã có 80%
        submitWriting(u, linkedSession(u, q, attempt)); // phiên mới nhất còn đang chấm
        submitAttempt(u, attempt).andExpect(jsonPath("$.score").doesNotExist()).andExpect(jsonPath("$.passed").doesNotExist());

        fake.override(WritingGrade.class, grade(40));
        assertEquals(1, runner.runDueNow());
        getJson("/api/attempts/" + attempt, u).andExpect(jsonPath("$.score").value(0.4)).andExpect(jsonPath("$.passed").value(false));
    }

    @Test
    void resultOfAnOlderSessionIsIgnoredAfterSubmit() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = writingQuestion(bank);
        long attempt = startAttempt(u, bank);
        submitWriting(u, linkedSession(u, q, attempt)); // phiên cũ: job chưa chạy
        submitWriting(u, linkedSession(u, q, attempt)); // phiên mới
        submitAttempt(u, attempt);
        fake.override(WritingGrade.class, grade(90));
        assertEquals(2, runner.runDueNow());
        // hai job cùng 90%, chỉ phiên mới nhất được cộng: score 0.9, không phải 1.8
        getJson("/api/attempts/" + attempt, u).andExpect(jsonPath("$.score").value(0.9)).andExpect(jsonPath("$.passed").value(true));
    }

    @Test
    void cannotLinkToAnotherUsersOrSubmittedOrForeignBankAttempt() throws Exception {
        var u = student(5);
        var other = student(5);
        long bank = bank(true);
        long q = writingQuestion(bank);
        long attempt = startAttempt(u, bank);
        openSession(other, q, attempt).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("attempt_not_found"));

        long otherBankQuestion = writingQuestion(bank(true));
        openSession(u, otherBankQuestion, attempt).andExpect(status().isBadRequest());

        submitAttempt(u, attempt);
        openSession(u, q, attempt).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("attempt_already_submitted"));
        assertEquals(0, count("select count(*) from writing_sessions where attempt_id = ?", attempt));
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
    void speakingOnlyBankConvertsBandByConfiguredMax() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = question(bank, "SPEAKING", 1, "Describe a trip.", "{\"part\":2,\"prompt\":\"Describe a trip.\",\"cueCard\":[\"where\"]}", "{}");
        long attempt = startAttempt(u, bank);
        guard.resetRateLimit(u.id());
        var r = postJson("/api/speaking/submissions", u, Map.of("questionId", q, "attemptId", attempt)).andExpect(status().isCreated());
        long id = idOf(r, "submissionId");
        storage.put(jdbc.queryForObject("select object_key from speaking_submissions where id = ?", String.class, id), wav(20));
        guard.resetRateLimit(u.id());
        postJson("/api/speaking/submissions/" + id + "/complete", u, Map.of()).andExpect(status().isAccepted());
        assertEquals(attempt, count("select attempt_id from ai_jobs where kind = 'SPEAKING_GRADE' and user_id = ?", u.id()));

        submitAttempt(u, attempt).andExpect(jsonPath("$.maxScore").value(1)).andExpect(jsonPath("$.passed").doesNotExist());
        fake.override(SpeakingGrade.class, new SpeakingGrade(7, 6.5, "Tốt.", List.of()));
        assertEquals(1, runner.runDueNow());
        double overall = Double.parseDouble(jdbc.queryForObject("select result from speaking_submissions where id = ?", String.class, id)
                .replaceAll("(?s).*\"overall\"\\s*:\\s*([0-9.]+).*", "$1"));
        // credit = overall / 9.0, làm tròn 4 chữ số rồi score 2 chữ số
        double expected = Math.round(Math.round(overall / 9.0 * 10000) / 10000.0 * 100) / 100.0;
        getJson("/api/attempts/" + attempt, u).andExpect(jsonPath("$.score").value(expected))
                .andExpect(jsonPath("$.passed").value(expected >= 0.7));
    }
}
