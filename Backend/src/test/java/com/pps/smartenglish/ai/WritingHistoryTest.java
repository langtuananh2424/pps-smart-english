package com.pps.smartenglish.ai;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** D-82 (chặn câu bộ thi) và D-84 (xem lại phiên, danh sách phiên của tôi). */
class WritingHistoryTest extends AiTestSupport {

    private static final String ESSAY = "I agree with online learning because it is flexible. However, students need discipline.";

    private String url(long id) {
        return "/api/writing/sessions/" + id;
    }

    @Test
    void examBankQuestionsAreNotPracticable() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long wq = writingQuestion(bank);
        long sq = question(bank, "SPEAKING", 2, "Describe a trip.", "{\"part\":1,\"prompt\":\"Describe a trip.\"}", "{}");
        jdbc.update("update question_banks set mode = 'EXAM' where id = ?", bank);
        guard.resetRateLimit(u.id());
        postJson("/api/writing/sessions", u, Map.of("questionId", wq)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("exam_question_not_practicable"));
        postJson("/api/speaking/submissions", u, Map.of("questionId", sq)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("exam_question_not_practicable"));
        org.junit.jupiter.api.Assertions.assertEquals(0, count("select count(*) from writing_sessions where user_id = ?", u.id()));
    }

    @Test
    void sessionViewIsOwnerOnlyAndReflectsProgress() throws Exception {
        var u = student(5);
        var other = student(5);
        long q = writingQuestion(bank(true));
        long s = newSession(u, q);

        getJson(url(s), other).andExpect(status().isNotFound());
        getJson(url(s), u).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.question.id").value(q)).andExpect(jsonPath("$.turns", hasSize(1)))
                .andExpect(jsonPath("$.turns[0].role").value("CAM")).andExpect(jsonPath("$.pendingJobId").doesNotExist());

        guard.resetRateLimit(u.id());
        long job = idOf(postJson(url(s) + "/answer", u, Map.of("text", "Vì linh hoạt")).andExpect(status().isAccepted()), "jobId");
        getJson(url(s), u).andExpect(jsonPath("$.turns", hasSize(2))).andExpect(jsonPath("$.turns[1].role").value("STUDENT"))
                .andExpect(jsonPath("$.pendingJobId").value(job));
        runner.runDueNow();
        getJson(url(s), u).andExpect(jsonPath("$.pendingJobId").doesNotExist());

        long submitted = idOf(postJson(url(s) + "/submit", u, Map.of("finalText", ESSAY), UUID.randomUUID().toString())
                .andExpect(status().isAccepted()), "jobId");
        getJson(url(s), u).andExpect(jsonPath("$.status").value("SUBMITTED")).andExpect(jsonPath("$.gradeJobId").value(submitted))
                .andExpect(jsonPath("$.finalText").value(ESSAY)).andExpect(jsonPath("$.grade").doesNotExist());
        runner.runDueNow();
        getJson(url(s), u).andExpect(jsonPath("$.grade.scorePercent").isNumber());
        getJson(url(999999999L), u).andExpect(status().isNotFound());
    }

    @Test
    void myListIsScopedPagedAndNewestFirst() throws Exception {
        var u = student(9);
        var other = student(9);
        long q1 = writingQuestion(bank(true));
        long q2 = writingQuestion(bank(true));
        long a = newSession(u, q1);
        long b = newSession(u, q1);
        long c = newSession(u, q2);
        newSession(other, q1);
        postJson(url(a) + "/submit", u, Map.of("finalText", ESSAY), UUID.randomUUID().toString()).andExpect(status().isAccepted());
        runner.runDueNow();

        getJson("/api/me/writing?questionId=" + q1, u).andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].sessionId").value(b)).andExpect(jsonPath("$.data[1].sessionId").value(a))
                .andExpect(jsonPath("$.data[1].status").value("SUBMITTED")).andExpect(jsonPath("$.data[1].scorePercent").isNumber())
                .andExpect(jsonPath("$.data[0].scorePercent").doesNotExist());
        getJson("/api/me/writing", u).andExpect(jsonPath("$.data", hasSize(3))).andExpect(jsonPath("$.data[0].sessionId").value(c));

        var first = getJson("/api/me/writing?limit=2", u).andExpect(jsonPath("$.data", hasSize(2)));
        String next = json.readTree(first.andReturn().getResponse().getContentAsString()).get("nextCursor").asText();
        getJson("/api/me/writing?limit=2&cursor=" + next, u).andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].sessionId").value(a)).andExpect(jsonPath("$.nextCursor").doesNotExist());
        getJson("/api/me/writing?questionId=abc", u).andExpect(status().isBadRequest());
    }
}
