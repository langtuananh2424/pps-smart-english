package com.pps.smartenglish.ai;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.ai.dto.RewriteGrades;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** Câu viết lại: luật miễn phí trước, phần còn lại gộp vào một job một lượt (Q-06, Q-30). */
@org.springframework.context.annotation.Import(com.pps.smartenglish.support.EventCollectorConfig.class)
class RewriteGradingTest extends AiTestSupport {

    @org.springframework.beans.factory.annotation.Autowired
    com.pps.smartenglish.support.EventCollectorConfig.EventCollector collector;

    private static final String ALTHOUGH_KEY = "{\"acceptedAnswers\":[\"Although the test was difficult, most students passed it.\"]}";

    /** Bank 4 câu: q[0] có câu mẫu, q[1] cần từ however, q[2] và q[3] chỉ AI chấm được. */
    private record Bank(long id, long[] q) {}

    private Bank rewriteBank() {
        long b = bank(true);
        long[] q = new long[4];
        q[0] = question(b, "CONNECTIVE_REWRITE", 1, "Viết lại với although",
                "{\"sentence\":\"The test was difficult. Most students passed it.\",\"requiredWord\":\"although\"}", ALTHOUGH_KEY);
        q[1] = question(b, "CONNECTIVE_REWRITE", 2, "Viết lại với however",
                "{\"sentence\":\"It rained. We went out.\",\"requiredWord\":\"however\"}", "{}");
        q[2] = question(b, "GRAMMAR_REWRITE", 3, "Viết lại bằng bị động",
                "{\"sentence\":\"People say exercise helps.\",\"requiredElement\":\"PASSIVE\"}", "{}");
        q[3] = question(b, "GRAMMAR_REWRITE", 4, "Viết lại bằng bị động 2",
                "{\"sentence\":\"They built a bridge.\",\"requiredElement\":\"PASSIVE\"}", "{}");
        return new Bank(b, q);
    }

    private long start(Account u, long bankId) throws Exception {
        return idOf(postJson("/api/banks/" + bankId + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
    }

    private Map<String, Object> text(long questionId, String text) {
        return Map.of("questionId", questionId, "answer", Map.of("text", text));
    }

    private ResultActions submit(Account u, long attemptId, List<Map<String, Object>> answers) throws Exception {
        return postJson("/api/attempts/" + attemptId + "/submit", u, Map.of("answers", answers));
    }

    @Test
    void ruleGradingIsFreeAndWorksWithoutQuota() throws Exception {
        var u = student(0);
        var b = rewriteBank();
        long attempt = start(u, b.id);
        // q0 khớp câu mẫu (khác hoa thường, dấu cuối); q1 thiếu từ bắt buộc; q2 bỏ trống; q3 không gửi
        submit(u, attempt, List.of(text(b.q[0], "  ALTHOUGH the test   was difficult, most students passed it!  "),
                text(b.q[1], "It rained so we went out."), Map.of("questionId", b.q[2]))).andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(1)).andExpect(jsonPath("$.maxScore").value(4))
                .andExpect(jsonPath("$.pendingJobs", hasSize(0)))
                .andExpect(jsonPath("$.aiQuotaRequired").doesNotExist())
                .andExpect(jsonPath("$.results[0].isCorrect").value(true))
                .andExpect(jsonPath("$.results[1].isCorrect").value(false))
                .andExpect(jsonPath("$.results[1].feedback").value("Câu chưa dùng từ nối bắt buộc: however."))
                .andExpect(jsonPath("$.results[2].isCorrect").value(false))
                .andExpect(jsonPath("$.results[3].isCorrect").value(false));
        assertEquals(0, count("select count(*) from ai_jobs where attempt_id = ?", attempt));
        assertEquals(4, count("select count(*) from student_answer_grading g join student_answers a on a.id = g.answer_id"
                + " where a.attempt_id = ? and g.grader = 'RULE'", attempt));
        assertEquals(0, balance(u));
    }

    @Test
    void manyQuestionsShareOneJobAndOneQuota() throws Exception {
        var u = student(2);
        var b = rewriteBank();
        long attempt = start(u, b.id);
        var res = submit(u, attempt, List.of(text(b.q[0], "Although the test was difficult, most students passed it."),
                text(b.q[1], "It rained. However, we went out."), text(b.q[2], "Exercise is said to help."),
                text(b.q[3], "A WRONG bridge was built.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").doesNotExist()).andExpect(jsonPath("$.maxScore").value(4)) // D-147: không điểm tạm
                .andExpect(jsonPath("$.results").doesNotExist()); // D-147: đang chấm thì chưa có đáp án từng câu
        long jobId = json.readTree(res.andReturn().getResponse().getContentAsString()).at("/pendingJobs/0").asLong();
        assertEquals(1, balance(u)); // 3 câu qua AI nhưng chỉ 1 lượt
        assertEquals(1, count("select count(*) from ai_jobs where attempt_id = ?", attempt));
        assertEquals(1, count("select count(*) from ai_quota_ledger where user_id = ? and reason = 'GRADING' and ref_id = ?", u.id(), jobId));

        // trước khi chấm xong, GET vẫn báo job đang chờ và điểm chưa đổi
        getJson("/api/attempts/" + attempt, u).andExpect(jsonPath("$.pendingJobs[0]").value(jobId)).andExpect(jsonPath("$.score").doesNotExist());

        runner.runDueNow();
        getJson("/api/attempts/" + attempt, u).andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingJobs", hasSize(0)))
                .andExpect(jsonPath("$.score").value(3)).andExpect(jsonPath("$.maxScore").value(4))
                .andExpect(jsonPath("$.results[1].isCorrect").value(true))
                .andExpect(jsonPath("$.results[1].feedback").value("Tốt."))
                .andExpect(jsonPath("$.results[1].correctAnswer.text").value("It rained. However, we went out."))
                .andExpect(jsonPath("$.results[2].isCorrect").value(true))
                .andExpect(jsonPath("$.results[3].isCorrect").value(false));
        assertEquals(3, count("select count(*) from student_answer_grading g join student_answers a on a.id = g.answer_id"
                + " where a.attempt_id = ? and g.grader = 'AI'", attempt));
        assertEquals(1, count("select count(*) from student_answer_grading g join student_answers a on a.id = g.answer_id"
                + " where a.attempt_id = ? and g.grader = 'RULE'", attempt));
        // chạy lại không cộng điểm lần hai
        assertEquals(0, runner.runDueNow());
        getJson("/api/attempts/" + attempt, u).andExpect(jsonPath("$.score").value(3));
        getJson("/api/ai/jobs/" + jobId, u).andExpect(jsonPath("$.status").value("DONE"));
    }

    @Test
    void overlongAnswerIsWrongWithoutJobOrCharge() throws Exception {
        var u = student(2);
        var b = rewriteBank();
        long attempt = start(u, b.id);
        submit(u, attempt, List.of(text(b.q[2], "a".repeat(1001)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.results[2].isCorrect").value(false))
                .andExpect(jsonPath("$.results[2].feedback").value("Câu trả lời quá dài."))
                .andExpect(jsonPath("$.pendingJobs", hasSize(0)));
        assertEquals(0, count("select count(*) from ai_jobs where attempt_id = ?", attempt));
        assertEquals(2, balance(u));
    }

    @Test
    void outOfQuotaStillSubmitsAndAsksForAddon() throws Exception {
        addonProduct();
        var u = student(0);
        var b = rewriteBank();
        long attempt = start(u, b.id);
        submit(u, attempt, List.of(text(b.q[0], "Although the test was difficult, most students passed it."),
                text(b.q[1], "It rained. However, we went out."), text(b.q[2], "Exercise is said to help.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(1)).andExpect(jsonPath("$.maxScore").value(4)) // D-147: câu hết lượt tính 0, điểm chốt luôn
                .andExpect(jsonPath("$.aiQuotaRequired").value(true))
                .andExpect(jsonPath("$.products[0].type").value("AI_ADDON"))
                .andExpect(jsonPath("$.pendingJobs", hasSize(0)))
                .andExpect(jsonPath("$.results[1].isCorrect").value(false))
                .andExpect(jsonPath("$.results[1].aiFailure").value("hết lượt chấm, câu tính 0 điểm"))
                .andExpect(jsonPath("$.results[2].isCorrect").value(false));
        assertEquals(0, count("select count(*) from ai_jobs where attempt_id = ?", attempt));
        assertEquals(1, count("select count(*) from student_exercise_attempts where id = ? and submitted_at is not null", attempt));
        assertEquals(0, balance(u));
        // ExerciseScored đúng một lần: XP và streak của lần nộp không bị cộng đôi
        assertEquals(1, collector.exerciseScoredFor(attempt));
        assertEquals(1, count("select count(*) from xp_events where user_id = ? and source = 'EXERCISE'", u.id()));
        // không có job nên câu viết lại không được treo ở trạng thái chờ
        assertEquals(0, count("select count(*) from student_answers where attempt_id = ? and is_correct is null", attempt));
        assertEquals(2, count("select count(*) from student_answers where attempt_id = ? and ai_fail_reason is not null", attempt));
    }

    @Test
    void rateLimitedSubmitRollsBackTheWholeAttempt() throws Exception {
        var u = student(2);
        var b = rewriteBank();
        long attempt = start(u, b.id);
        for (int i = 0; i < 5; i++) {
            guard.rateLimit(u.id());
        }
        submit(u, attempt, List.of(text(b.q[1], "It rained. However, we went out."))).andExpect(status().isTooManyRequests());
        assertEquals(0, count("select count(*) from student_exercise_attempts where id = ? and submitted_at is not null", attempt));
        assertEquals(0, count("select count(*) from student_answers where attempt_id = ?", attempt));
        assertEquals(2, balance(u));
        assertEquals(0, count("select count(*) from ai_jobs where attempt_id = ?", attempt));

        guard.resetRateLimit(u.id());
        submit(u, attempt, List.of(text(b.q[1], "It rained. However, we went out."))).andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingJobs", hasSize(1)));
        assertEquals(1, balance(u));
    }

    @Test
    void outputWithWrongIdsIsRetriedThenFailsAndRefunds() throws Exception {
        var u = student(1);
        var b = rewriteBank();
        long attempt = start(u, b.id);
        submit(u, attempt, List.of(text(b.q[1], "It rained. However, we went out."), text(b.q[2], "Exercise is said to help.")))
                .andExpect(status().isOk());
        assertEquals(0, balance(u));
        // thiếu một id và thừa một id lạ
        fake.override(RewriteGrades.class, new RewriteGrades(List.of(new RewriteGrades.Item(b.q[1], true, "ok", "x"),
                new RewriteGrades.Item(987654321L, true, "ok", "x"))));
        for (int i = 1; i <= 6; i++) { // 3 lần thử, chấm lại tự động một lần (D-147), 3 lần thử nữa
            assertEquals(1, runner.runDueNow());
            jdbc.update("update ai_jobs set run_after = ? where attempt_id = ?", OffsetDateTime.parse("2000-01-01T00:00:00Z"), attempt);
        }
        assertEquals("FAILED", jdbc.queryForObject("select status from ai_jobs where attempt_id = ?", String.class, attempt));
        assertEquals(1, balance(u));
        // D-147: q1, q2 tính 0 kèm lý do và đã hoàn lượt; q0, q3 bỏ trống bị luật chấm sai từ lúc nộp; kết quả không còn chờ
        assertEquals(0, count("select count(*) from student_answers where attempt_id = ? and is_correct is null", attempt));
        assertEquals(2, count("select count(*) from student_answers where attempt_id = ? and ai_fail_reason = 'Cam chưa chấm được câu này' and ai_fail_refunded", attempt));
        getJson("/api/attempts/" + attempt, u).andExpect(jsonPath("$.score").value(0)).andExpect(jsonPath("$.pendingJobs", hasSize(0)))
                .andExpect(jsonPath("$.results[1].aiFailure").value("Cam chưa chấm được câu này"));
    }
}
