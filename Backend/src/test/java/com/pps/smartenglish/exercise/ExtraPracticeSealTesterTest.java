package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.ai.AiTestSupport;
import com.pps.smartenglish.ai.speaking.FakeSpeakingEngineClient;
import com.pps.smartenglish.support.FakeStorageConfig.FakeStorage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Tester D-152 vòng 2: AttemptResultGate giấu kết quả Nói của lượt đã nộp khi lượt Luyện thêm mở chứa cùng câu, mở lại sau khi nộp. */
class ExtraPracticeSealTesterTest extends AiTestSupport {

    private static final String CUE = "{\"part\":1,\"prompt\":\"Describe a trip.\"}";

    @Autowired FakeSpeakingEngineClient engine;
    @Autowired FakeStorage storage;

    @BeforeEach
    void resetEngine() {
        engine.reset();
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
    void speakingResultOfSubmittedAttemptIsSealedWhileAnExtraAttemptHoldsTheQuestion() throws Exception {
        var u = student(5);
        long course = insert("courses", Map.of("slug", "seal-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        long level = insert("course_levels", Map.of("course_id", course, "code", "A", "title", "Level A", "status", "PUBLISHED"));
        long topic = insert("topics", Map.of("level_id", level, "title", "Nói", "status", "PUBLISHED"));
        long bank = insert("question_banks", Map.of("topic_id", topic, "title", "Bộ", "is_trial", false, "status", "PUBLISHED", "mode", "PRACTICE"));
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', ?, null)",
                u.id(), course, OffsetDateTime.parse("2020-01-01T00:00:00Z"));
        long q = question(bank, "SPEAKING", 1, "Describe a trip.", CUE, "{}");

        // lượt thường: nói, nộp, chấm xong thì thấy kết quả
        long normal = idOf(postJson("/api/banks/" + bank + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
        guard.resetRateLimit(u.id());
        long sub = idOf(postJson("/api/speaking/submissions", u, Map.of("questionId", q, "attemptId", normal)).andExpect(status().isCreated()), "submissionId");
        storage.put(jdbc.queryForObject("select object_key from speaking_submissions where id = ?", String.class, sub), wav(20));
        guard.resetRateLimit(u.id());
        postJson("/api/speaking/submissions/" + sub + "/complete", u, Map.of()).andExpect(status().isAccepted());
        postJson("/api/attempts/" + normal + "/submit", u, Map.of("answers", List.of())).andExpect(status().isOk());
        assertEquals(1, runner.runDueNow());
        getJson("/api/speaking/submissions/" + sub, u).andExpect(status().isOk()).andExpect(jsonPath("$.result.overall").exists());

        // Topic đã hoàn thành nên dùng Ôn lại. Lượt Luyện thêm dạng Nói chứa cùng câu đang mở: kết quả cũ bị giấu (không cho nghe/đọc lại nhận xét rồi làm lại)
        long extra = idOf(postJson("/api/me/courses/" + course + "/extra-practice/attempts", u, Map.of("scope", "REVIEW", "topicId", topic, "kind", "SPEAKING"))
                .andExpect(status().isCreated()), "id");
        assertEquals(List.of(q), jdbc.queryForList("select question_id from attempt_questions where attempt_id = ?", Long.class, extra));
        getJson("/api/speaking/submissions/" + sub, u).andExpect(status().isOk()).andExpect(jsonPath("$.result").doesNotExist());
        getJson("/api/me/speaking", u).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].result").doesNotExist());
        // điểm lượt thường đã nộp không bị đụng
        getJson("/api/attempts/" + normal, u).andExpect(status().isOk()).andExpect(jsonPath("$.score").exists());

        // nộp lượt Luyện thêm thì kết quả cũ hiện lại
        postJson("/api/attempts/" + extra + "/submit", u, Map.of("answers", List.of())).andExpect(status().isOk());
        getJson("/api/speaking/submissions/" + sub, u).andExpect(jsonPath("$.result.overall").exists());
        getJson("/api/me/speaking", u).andExpect(jsonPath("$.data[0].result").exists());
    }
}
