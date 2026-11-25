package com.pps.smartenglish.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.ai.dto.WritingGrade;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** D-147: mỗi bộ chỉ một lượt mở, không luyện riêng câu đang nằm trong lượt mở, phiên luyện riêng cũ bị giấu kết quả. */
class OneOpenAttemptTest extends AiTestSupport {

    private static final String ESSAY = "I agree with online learning because it is flexible. However, students need discipline.";
    private static final String CUE = "{\"part\":1,\"prompt\":\"Describe a trip.\"}";

    private static WritingGrade grade(int pct) {
        return new WritingGrade(pct, pct, pct, pct, List.of(), "fixed text", new WritingGrade.Connectives(2, 1, 2, List.of(), "ok"), List.of(), null, "fb");
    }

    private long practiceSession(Account u, long q) throws Exception {
        guard.resetRateLimit(u.id());
        return idOf(postJson("/api/writing/sessions", u, Map.of("questionId", q)).andExpect(status().isCreated()), "sessionId");
    }

    private void submitPractice(Account u, long session) throws Exception {
        guard.resetRateLimit(u.id());
        postJson("/api/writing/sessions/" + session + "/submit", u, Map.of("finalText", ESSAY), UUID.randomUUID().toString()).andExpect(status().isAccepted());
    }

    @Test
    void concurrentStartsCreateOnlyOneAttempt() throws Exception {
        var u = student(5);
        long bank = bank(true);
        var pool = Executors.newFixedThreadPool(4);
        try {
            var gate = new CountDownLatch(1);
            List<Future<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                Callable<Integer> c = () -> {
                    gate.await();
                    return postJson("/api/banks/" + bank + "/attempts", u, Map.of()).andReturn().getResponse().getStatus();
                };
                calls.add(pool.submit(c));
            }
            gate.countDown();
            int created = 0;
            for (var f : calls) {
                int code = f.get();
                assertTrue(code == 201 || code == 409, "mã " + code);
                created += code == 201 ? 1 : 0;
            }
            assertEquals(1, created);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, count("select count(*) from student_exercise_attempts where user_id = ?", u.id()));
    }

    @Test
    void practiceSessionCreatedBeforeTheAttemptHasItsResultSealedWhileOpen() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = writingQuestion(bank);
        long practice = practiceSession(u, q);
        submitPractice(u, practice);
        fake.override(WritingGrade.class, grade(50));
        assertEquals(1, runner.runDueNow());
        long job = count("select grade_job_id from writing_sessions where id = ?", practice);
        getJson("/api/writing/sessions/" + practice, u).andExpect(jsonPath("$.grade.scorePercent").value(50)); // chưa có lượt mở

        long attempt = idOf(postJson("/api/banks/" + bank + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
        getJson("/api/writing/sessions/" + practice, u).andExpect(jsonPath("$.grade").doesNotExist());
        getJson("/api/ai/jobs/" + job, u).andExpect(jsonPath("$.result").doesNotExist());
        getJson("/api/me/writing", u).andExpect(jsonPath("$.data[0].scorePercent").doesNotExist());

        postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers", List.of())).andExpect(status().isOk());
        getJson("/api/writing/sessions/" + practice, u).andExpect(jsonPath("$.grade.scorePercent").value(50));
    }

    @Test
    void speakingPracticeBlockedWhileAttemptOpenAndAllowedAfterSubmit() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long sq = question(bank, "SPEAKING", 1, "Describe a trip.", CUE, "{}");
        long attempt = idOf(postJson("/api/banks/" + bank + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
        guard.resetRateLimit(u.id());
        postJson("/api/speaking/submissions", u, Map.of("questionId", sq)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("question_in_open_attempt")).andExpect(jsonPath("$.error.details.attemptId").value(attempt))
                .andExpect(jsonPath("$.error.details.bankId").value(bank));
        getJson("/api/speaking/questions/" + sq, u).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("question_in_open_attempt"));

        postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers", List.of())).andExpect(status().isOk());
        getJson("/api/speaking/questions/" + sq, u).andExpect(status().isOk());
        guard.resetRateLimit(u.id());
        postJson("/api/speaking/submissions", u, Map.of("questionId", sq)).andExpect(status().isCreated());
    }

    @Test
    void practiceWritingAllowedAgainAfterAttemptSubmitted() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = writingQuestion(bank);
        long attempt = idOf(postJson("/api/banks/" + bank + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
        guard.resetRateLimit(u.id());
        postJson("/api/writing/sessions", u, Map.of("questionId", q)).andExpect(status().isConflict());
        postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers", List.of())).andExpect(status().isOk());
        practiceSession(u, q);
        // làm lại: sau khi lượt cũ đã nộp thì mở được lượt mới
        postJson("/api/banks/" + bank + "/attempts", u, Map.of()).andExpect(status().isCreated());
    }
}
