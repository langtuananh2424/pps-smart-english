package com.pps.smartenglish.retention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.ai.AiTestSupport;
import com.pps.smartenglish.ai.provider.AiCallException;
import com.pps.smartenglish.ai.speaking.FakeSpeakingEngineClient;
import com.pps.smartenglish.shared.event.AttemptGradingSettled;
import com.pps.smartenglish.support.FakeStorageConfig.FakeStorage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

/** FR-RET-03, D-149: một thông báo EXERCISE_GRADED cho mỗi lượt khi job AI cuối của lượt đã nộp xong. */
class NotificationExerciseGradedTest extends AiTestSupport {

    private static final String ESSAY = "I agree with online learning because it is flexible. However, students need discipline.";
    private static final String CUE = "{\"part\":1,\"prompt\":\"Describe a trip.\"}";

    @Autowired FakeSpeakingEngineClient engine;
    @Autowired FakeStorage storage;
    @Autowired ApplicationEventPublisher publisher;

    @BeforeEach
    void resetEngine() {
        engine.reset();
    }

    private static byte[] wav() {
        int data = 20 * 32000;
        var b = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + data).put("WAVE".getBytes()).put("fmt ".getBytes()).putInt(16).putShort((short) 1)
                .putShort((short) 1).putInt(16000).putInt(32000).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes()).putInt(data);
        return b.array();
    }

    private long startAttempt(Account u, long bank) throws Exception {
        return idOf(postJson("/api/banks/" + bank + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
    }

    /** Mở phiên Viết gắn lượt và nộp chấm. */
    private void gradeWriting(Account u, long q, long attempt) throws Exception {
        guard.resetRateLimit(u.id());
        long s = idOf(postJson("/api/writing/sessions", u, Map.of("questionId", q, "attemptId", attempt)).andExpect(status().isCreated()), "sessionId");
        guard.resetRateLimit(u.id());
        postJson("/api/writing/sessions/" + s + "/submit", u, Map.of("finalText", ESSAY), UUID.randomUUID().toString())
                .andExpect(status().isAccepted());
    }

    private void gradeSpeaking(Account u, long q, long attempt) throws Exception {
        guard.resetRateLimit(u.id());
        long id = idOf(postJson("/api/speaking/submissions", u, Map.of("questionId", q, "attemptId", attempt)).andExpect(status().isCreated()), "submissionId");
        storage.put(jdbc.queryForObject("select object_key from speaking_submissions where id = ?", String.class, id), wav());
        guard.resetRateLimit(u.id());
        postJson("/api/speaking/submissions/" + id + "/complete", u, Map.of()).andExpect(status().isAccepted());
    }

    private void submit(Account u, long attempt) throws Exception {
        postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers", List.of())).andExpect(status().isOk());
    }

    private int graded(Account u) {
        return count("select count(*) from notifications where user_id = ? and type = 'EXERCISE_GRADED'", u.id());
    }

    @Test
    void oneNotificationWhenTheLastOfTwoWritingAndOneSpeakingJobsFinishes() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long w1 = question(bank, "WRITING", 1, "Q1", "{\"task\":\"Task one\",\"requiredWords\":100}", "{}");
        long w2 = question(bank, "WRITING", 2, "Q2", "{\"task\":\"Task two\",\"requiredWords\":100}", "{}");
        long sp = question(bank, "SPEAKING", 3, "Describe a trip.", CUE, "{}");
        long attempt = startAttempt(u, bank);
        gradeWriting(u, w1, attempt);
        gradeWriting(u, w2, attempt);
        gradeSpeaking(u, sp, attempt);
        submit(u, attempt);
        assertEquals(0, graded(u)); // nộp lúc còn job chờ: chưa thông báo

        assertEquals(3, runner.runDueNow());

        assertEquals(1, graded(u));
        var payload = json.readTree(jdbc.queryForObject("select payload from notifications where user_id = ? and type = 'EXERCISE_GRADED'", String.class, u.id()));
        assertEquals("/bai-tap/" + bank + "/ket-qua/" + attempt, payload.get("url").asText());
        assertEquals("Bài \"Bộ\" đã chấm xong", payload.get("title").asText());
        assertEquals("Mở để xem kết quả.", payload.get("body").asText());
        assertEquals(0, count("select count(*) from notifications where user_id = ? and type = 'WRITING_GRADED'", u.id()));
        publisher.publishEvent(new AttemptGradingSettled(attempt, u.id())); // phát lại không trùng
        assertEquals(1, graded(u));
    }

    @Test
    void noNotificationWhenJobsFinishedBeforeSubmitOrAttemptStillOpen() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long attempt = startAttempt(u, bank);
        gradeWriting(u, writingQuestion(bank), attempt);
        assertEquals(1, runner.runDueNow()); // lượt chưa nộp
        assertEquals(0, graded(u));
        submit(u, attempt); // lúc nộp không còn job chờ
        assertEquals(0, graded(u));
    }

    @Test
    void notifiesOnlyAfterSubmitWhenJobFinishesLater() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long attempt = startAttempt(u, bank);
        gradeWriting(u, writingQuestion(bank), attempt);
        submit(u, attempt);
        assertEquals(1, runner.runDueNow());
        assertEquals(1, graded(u));
    }

    @Test
    void refundedFailedJobCountsAsFinished() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long attempt = startAttempt(u, bank);
        gradeWriting(u, writingQuestion(bank), attempt);
        submit(u, attempt);
        fake.failNext(2, new AiCallException(false, "từ chối", null, null));
        assertEquals(1, runner.runDueNow()); // lỗi lần đầu: chấm lại một lần, chưa xong
        assertEquals(0, graded(u));
        assertEquals(1, runner.runDueNow());
        assertEquals("FAILED", jdbc.queryForObject("select status from ai_jobs where attempt_id = ?", String.class, attempt));
        assertEquals(1, graded(u));
    }

    @Test
    void standaloneWritingStillSendsWritingGraded() throws Exception {
        var u = student(1);
        guard.resetRateLimit(u.id());
        long session = idOf(postJson("/api/writing/sessions", u, Map.of("questionId", writingQuestion(bank(true)))), "sessionId");
        postJson("/api/writing/sessions/" + session + "/submit", u, Map.of("finalText", ESSAY), UUID.randomUUID().toString())
                .andExpect(status().isAccepted());
        runner.runDueNow();
        assertEquals(1, count("select count(*) from notifications where user_id = ? and type = 'WRITING_GRADED'", u.id()));
        assertEquals(0, graded(u));
    }
}
