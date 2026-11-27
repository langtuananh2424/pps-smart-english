package com.pps.smartenglish.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.ai.dto.SpeakingGrade;
import com.pps.smartenglish.ai.dto.WritingGrade;
import com.pps.smartenglish.support.FakeStorageConfig.FakeStorage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

/** D-158, D-145: Video phản xạ chấm như Nói gắn lượt; VIDEO_SHORT có bước Viết mở bước Nói; tối đa 3 lần ghi mỗi câu. */
class VideoReflexFlowTest extends AiTestSupport {

    @Autowired FakeStorage storage;

    private static WritingGrade grade(int pct) {
        return new WritingGrade(pct, pct, pct, pct, List.of(), "fixed", new WritingGrade.Connectives(2, 1, 2, List.of(), "ok"), List.of(), null, "fb");
    }

    private static byte[] wav(double seconds) {
        int data = (int) (seconds * 32000);
        var b = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + data).put("WAVE".getBytes()).put("fmt ".getBytes()).putInt(16).putShort((short) 1)
                .putShort((short) 1).putInt(16000).putInt(32000).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes()).putInt(data);
        return b.array();
    }

    /** Bộ có một nhóm video (YouTube 300 giây) và một câu thuộc nhóm. */
    private long reflexQuestion(long bank, String type, String payload) {
        long group = insert("question_groups", Map.of("bank_id", bank, "sort_order", 0, "video_youtube_id", "dQw4w9WgXcQ", "video_duration_sec", 300));
        long q = question(bank, type, 1, "Câu video", payload, null);
        jdbc.update("update questions set group_id = ? where id = ?", group, q);
        return q;
    }

    private long startAttempt(Account u, long bank) throws Exception {
        return idOf(postJson("/api/banks/" + bank + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
    }

    private ResultActions permit(Account u, long q, long attempt) throws Exception {
        guard.resetRateLimit(u.id());
        return postJson("/api/speaking/submissions", u, Map.of("questionId", q, "attemptId", attempt));
    }

    /** Ghi một lần: xin quyền, đưa WAV lên kho giả, xác nhận. */
    private long record(Account u, long q, long attempt) throws Exception {
        long id = idOf(permit(u, q, attempt).andExpect(status().isCreated()), "submissionId");
        storage.put(jdbc.queryForObject("select object_key from speaking_submissions where id = ?", String.class, id), wav(12));
        guard.resetRateLimit(u.id());
        postJson("/api/speaking/submissions/" + id + "/complete", u, Map.of()).andExpect(status().isAccepted());
        return id;
    }

    private ResultActions writeOnce(Account u, long q, long attempt) throws Exception {
        long session = idOf(postJson("/api/writing/sessions", u, Map.of("questionId", q, "attemptId", attempt)).andExpect(status().isCreated()), "sessionId");
        guard.resetRateLimit(u.id());
        return postJson("/api/writing/sessions/" + session + "/submit", u, Map.of("finalText", "I went home after school."), UUID.randomUUID().toString());
    }

    @Test
    void fourthRecordingIsRejected() throws Exception {
        var u = student(10);
        long bank = bank(true);
        long q = reflexQuestion(bank, "VIDEO_CUE", "{\"at\":30,\"prompt\":\"Describe a place.\",\"cue\":\"Where is it?\"}");
        long attempt = startAttempt(u, bank);
        getJson("/api/speaking/questions/" + q + "?attemptId=" + attempt, u).andExpect(jsonPath("$.maxSeconds").value(60));
        for (int i = 0; i < 3; i++) {
            record(u, q, attempt);
        }
        permit(u, q, attempt).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("recording_limit_reached"));
        getJson("/api/attempts/" + attempt + "/video-reflex/" + q, u).andExpect(jsonPath("$.recordUsed").value(3)).andExpect(jsonPath("$.recordMax").value(3))
                .andExpect(jsonPath("$.speakOpen").value(true));
    }

    @Test
    void shortSpeakingStepClosedUntilWritten() throws Exception {
        var u = student(10);
        long bank = bank(true);
        long q = reflexQuestion(bank, "VIDEO_SHORT", "{\"at\":45,\"prompt\":\"What did you do after school?\"}");
        long attempt = startAttempt(u, bank);
        getJson("/api/speaking/questions/" + q + "?attemptId=" + attempt, u).andExpect(jsonPath("$.maxSeconds").value(30));
        getJson("/api/attempts/" + attempt + "/video-reflex/" + q, u).andExpect(jsonPath("$.speakOpen").value(false)).andExpect(jsonPath("$.writeUsed").value(0));
        permit(u, q, attempt).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("writing_step_required"));

        // viết chưa đạt ngưỡng 70%: vẫn đóng, còn lượt viết
        writeOnce(u, q, attempt).andExpect(status().isAccepted());
        getJson("/api/attempts/" + attempt + "/video-reflex/" + q, u).andExpect(jsonPath("$.writePending").value(true)).andExpect(jsonPath("$.speakOpen").value(false));
        fake.override(WritingGrade.class, grade(40));
        assertEquals(1, runner.runDueNow());
        getJson("/api/attempts/" + attempt + "/video-reflex/" + q, u).andExpect(jsonPath("$.writeUsed").value(1)).andExpect(jsonPath("$.writePassed").value(false))
                .andExpect(jsonPath("$.speakOpen").value(false)).andExpect(jsonPath("$.score").doesNotExist());
        permit(u, q, attempt).andExpect(status().isConflict());

        // lần viết thứ hai đạt: mở bước Nói
        writeOnce(u, q, attempt).andExpect(status().isAccepted());
        fake.override(WritingGrade.class, grade(85));
        assertEquals(1, runner.runDueNow());
        getJson("/api/attempts/" + attempt + "/video-reflex/" + q, u).andExpect(jsonPath("$.writePassed").value(true)).andExpect(jsonPath("$.speakOpen").value(true));
        permit(u, q, attempt).andExpect(status().isCreated());
    }

    @Test
    void writeLimitOpensSpeakingAndBlocksFurtherSubmits() throws Exception {
        var u = student(10);
        long bank = bank(true);
        long q = reflexQuestion(bank, "VIDEO_SHORT", "{\"at\":45,\"prompt\":\"What did you do after school?\"}");
        long attempt = startAttempt(u, bank);
        fake.override(WritingGrade.class, grade(30));
        for (int i = 0; i < 3; i++) {
            writeOnce(u, q, attempt).andExpect(status().isAccepted());
            assertEquals(1, runner.runDueNow());
        }
        writeOnce(u, q, attempt).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("writing_limit_reached"));
        getJson("/api/attempts/" + attempt + "/video-reflex/" + q, u).andExpect(jsonPath("$.writeUsed").value(3)).andExpect(jsonPath("$.writePassed").value(false))
                .andExpect(jsonPath("$.speakOpen").value(true));
        permit(u, q, attempt).andExpect(status().isCreated());
    }

    @Test
    void attemptScoreComesFromSpeakingNotWriting() throws Exception {
        var u = student(10);
        long bank = bank(true);
        long q = reflexQuestion(bank, "VIDEO_SHORT", "{\"at\":45,\"prompt\":\"What did you do after school?\"}");
        long attempt = startAttempt(u, bank);
        writeOnce(u, q, attempt).andExpect(status().isAccepted());
        fake.override(WritingGrade.class, grade(100));
        assertEquals(1, runner.runDueNow());
        long sub = record(u, q, attempt);
        getJson("/api/attempts/" + attempt + "/ai-sessions", u).andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].kind").value("SPEAKING"))
                .andExpect(jsonPath("$.data[0].id").value(sub));

        postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers", List.of())).andExpect(status().isOk())
                .andExpect(jsonPath("$.maxScore").value(1)).andExpect(jsonPath("$.score").doesNotExist());
        fake.override(SpeakingGrade.class, new SpeakingGrade(7, 6.5, "Tốt.", List.of()));
        assertEquals(1, runner.runDueNow());
        double overall = Double.parseDouble(jdbc.queryForObject("select result from speaking_submissions where id = ?", String.class, sub)
                .replaceAll("(?s).*\"overall\"\\s*:\\s*([0-9.]+).*", "$1"));
        double expected = Math.round(Math.round(overall / 9.0 * 10000) / 10000.0 * 100) / 100.0;
        assertEquals(true, expected < 1.0);
        getJson("/api/attempts/" + attempt, u).andExpect(jsonPath("$.score").value(expected)); // không phải 1.0 của bài viết
    }

    @Test
    void writtenButNeverSpokenScoresZero() throws Exception {
        var u = student(10);
        long bank = bank(true);
        long q = reflexQuestion(bank, "VIDEO_SHORT", "{\"at\":45,\"prompt\":\"What did you do after school?\"}");
        long attempt = startAttempt(u, bank);
        writeOnce(u, q, attempt).andExpect(status().isAccepted());
        fake.override(WritingGrade.class, grade(100));
        assertEquals(1, runner.runDueNow());
        postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers", List.of())).andExpect(status().isOk())
                .andExpect(jsonPath("$.maxScore").value(1)).andExpect(jsonPath("$.score").value(0));
    }

    @Test
    void anotherStudentCannotUseMyAttempt() throws Exception {
        var u = student(10);
        var other = student(10);
        long bank = bank(true);
        long q = reflexQuestion(bank, "VIDEO_PICTURE", "{\"at\":12,\"prompt\":\"Describe the picture.\"}");
        long attempt = startAttempt(u, bank);
        permit(other, q, attempt).andExpect(status().isNotFound());
        getJson("/api/attempts/" + attempt + "/video-reflex/" + q, other).andExpect(status().isNotFound());
        guard.resetRateLimit(other.id());
        postJson("/api/writing/sessions", other, Map.of("questionId", q, "attemptId", attempt)).andExpect(status().isNotFound());
    }
}
