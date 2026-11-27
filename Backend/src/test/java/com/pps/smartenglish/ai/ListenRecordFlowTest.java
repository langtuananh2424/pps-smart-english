package com.pps.smartenglish.ai;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** D-154 mục 4, D-145: câu LISTEN_RECORD chấm như Nói qua phiên gắn lượt và tính 1 vào điểm tối đa. */
class ListenRecordFlowTest extends AiTestSupport {

    @Test
    void submissionAttachesToAttemptAndCountsInMaxScore() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = question(bank, "LISTEN_RECORD", 1, "Nghe rồi nói lại.", "{\"prompt\":\"Repeat what you heard.\",\"maxSeconds\":30}", "{}");
        long attempt = idOf(postJson("/api/banks/" + bank + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");

        guard.resetRateLimit(u.id());
        getJson("/api/speaking/questions/" + q + "?attemptId=" + attempt, u).andExpect(status().isOk())
                .andExpect(jsonPath("$.maxSeconds").value(30)).andExpect(jsonPath("$.prompt").value("Repeat what you heard."));
        long sub = idOf(postJson("/api/speaking/submissions", u, Map.of("questionId", q, "attemptId", attempt)).andExpect(status().isCreated()), "submissionId");

        getJson("/api/attempts/" + attempt + "/ai-sessions", u).andExpect(jsonPath("$.data[0].kind").value("SPEAKING"))
                .andExpect(jsonPath("$.data[0].id").value(sub)).andExpect(jsonPath("$.data[0].questionId").value(q));
        postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers", java.util.List.of())).andExpect(status().isOk())
                .andExpect(jsonPath("$.maxScore").value(1));
    }

    @Test
    void anotherStudentCannotUseMyAttempt() throws Exception {
        var u = student(5);
        var other = student(5);
        long bank = bank(true);
        long q = question(bank, "LISTEN_RECORD", 1, "Nghe rồi nói lại.", "{\"prompt\":\"Repeat.\"}", "{}");
        long attempt = idOf(postJson("/api/banks/" + bank + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
        guard.resetRateLimit(other.id());
        postJson("/api/speaking/submissions", other, Map.of("questionId", q, "attemptId", attempt)).andExpect(status().isNotFound());
    }
}
