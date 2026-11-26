package com.pps.smartenglish.retention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.ai.AiTestSupport;
import com.pps.smartenglish.retention.service.NotificationService;
import com.pps.smartenglish.shared.event.AttemptGradingSettled;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

/** Tester D-149: đồng thời, phân quyền theo bản ghi, nội dung payload của EXERCISE_GRADED. */
class NotificationExerciseGradedTesterTest extends AiTestSupport {

    private static final String ESSAY = "I agree with online learning because it is flexible. However, students need discipline.";

    @Autowired NotificationService service;
    @Autowired ApplicationEventPublisher publisher;

    private long startAttempt(Account u, long bank) throws Exception {
        return idOf(postJson("/api/banks/" + bank + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
    }

    private void gradeWriting(Account u, long q, long attempt) throws Exception {
        guard.resetRateLimit(u.id());
        long s = idOf(postJson("/api/writing/sessions", u, Map.of("questionId", q, "attemptId", attempt)).andExpect(status().isCreated()), "sessionId");
        guard.resetRateLimit(u.id());
        postJson("/api/writing/sessions/" + s + "/submit", u, Map.of("finalText", ESSAY), UUID.randomUUID().toString())
                .andExpect(status().isAccepted());
    }

    private void submit(Account u, long attempt) throws Exception {
        postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers", List.of())).andExpect(status().isOk());
    }

    private int graded(Account u) {
        return count("select count(*) from notifications where user_id = ? and type = 'EXERCISE_GRADED'", u.id());
    }

    /** Lượt đã nộp, hai job Viết vẫn chờ. */
    private long submittedWithTwoPendingJobs(Account u, long bank) throws Exception {
        long w1 = question(bank, "WRITING", 1, "Q1", "{\"task\":\"Task one\",\"requiredWords\":100}", "{}");
        long w2 = question(bank, "WRITING", 2, "Q2", "{\"task\":\"Task two\",\"requiredWords\":100}", "{}");
        long attempt = startAttempt(u, bank);
        gradeWriting(u, w1, attempt);
        gradeWriting(u, w2, attempt);
        submit(u, attempt);
        return attempt;
    }

    /** P0 đồng thời: nhiều luồng cùng báo "lượt đã xong" (hai job cuối xong cùng lúc, nhiều instance) chỉ tạo một thông báo. */
    @Test
    void concurrentSettledCallsCreateExactlyOneNotification() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long attempt = submittedWithTwoPendingJobs(u, bank);
        assertEquals(2, runner.runDueNow());
        jdbc.update("delete from notifications where user_id = ?", u.id()); // về trạng thái "đã xong, chưa thông báo"

        int n = 8;
        var pool = Executors.newFixedThreadPool(n);
        try {
            var gate = new CountDownLatch(1);
            List<Future<?>> calls = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                calls.add(pool.submit(() -> {
                    gate.await();
                    publisher.publishEvent(new AttemptGradingSettled(attempt, u.id()));
                    return null;
                }));
            }
            gate.countDown();
            for (var f : calls) {
                f.get(); // listener nuốt lỗi trùng: không luồng nào được ném
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, graded(u));
    }

    /** Hai job cuối chạy song song trên executor thật của worker: đúng một thông báo. */
    @Test
    void twoLastJobsOnWorkerThreadsNotifyOnce() throws Exception {
        var u = student(5);
        long bank = bank(true);
        submittedWithTwoPendingJobs(u, bank);
        assertEquals(2, runner.tick());
        for (int i = 0; i < 200 && count("select count(*) from ai_jobs where user_id = ? and status in ('PENDING', 'RUNNING')", u.id()) > 0; i++) {
            Thread.sleep(50);
        }
        for (int i = 0; i < 40 && graded(u) == 0; i++) {
            Thread.sleep(50); // listener chạy sau commit trên luồng worker
        }
        Thread.sleep(200);
        assertEquals(1, graded(u));
    }

    /** IDOR: sự kiện mang userId không khớp chủ lượt thì không ai nhận thông báo (đường dẫn kết quả không lọt sang người khác). */
    @Test
    void settledEventForAnotherUsersAttemptNotifiesNobody() throws Exception {
        var owner = student(5);
        var other = student(5);
        long bank = bank(true);
        long attempt = submittedWithTwoPendingJobs(owner, bank);
        assertEquals(2, runner.runDueNow());
        jdbc.update("delete from notifications where user_id = ?", owner.id());
        service.exerciseGraded(other.id(), attempt);
        publisher.publishEvent(new AttemptGradingSettled(attempt, other.id()));
        assertEquals(0, graded(other));
        assertEquals(0, graded(owner));
    }

    /** Route kết quả theo mã lượt: người khác đọc lượt qua API thì 404, chủ lượt thấy điểm khi đã chấm xong. */
    @Test
    void attemptResultOfAnotherUserIsNotFound() throws Exception {
        var owner = student(5);
        var other = student(5);
        long bank = bank(true);
        long attempt = submittedWithTwoPendingJobs(owner, bank);
        getJson("/api/attempts/" + attempt, owner).andExpect(status().isOk()).andExpect(jsonPath("$.score").doesNotExist());
        assertEquals(2, runner.runDueNow());
        getJson("/api/attempts/" + attempt, other).andExpect(status().isNotFound());
        getJson("/api/attempts/" + attempt, owner).andExpect(status().isOk()).andExpect(jsonPath("$.pendingJobs").isEmpty());
    }

    /** Tên bộ do biên tập viên nhập đi nguyên văn vào title (web tự escape); payload chỉ có title/body/url, không có bài làm. */
    @Test
    void payloadCarriesRawTitleAndNoAnswerContent() throws Exception {
        var u = student(5);
        long bank = bank(true);
        String evil = "<img src=x onerror=alert(1)> \"quoted\" & co";
        jdbc.update("update question_banks set title = ? where id = ?", evil, bank);
        long attempt = submittedWithTwoPendingJobs(u, bank);
        assertEquals(2, runner.runDueNow());
        String raw = jdbc.queryForObject("select payload from notifications where user_id = ? and type = 'EXERCISE_GRADED'", String.class, u.id());
        var p = json.readTree(raw);
        assertEquals("Bài \"" + evil + "\" đã chấm xong", p.get("title").asText());
        assertEquals("/bai-tap/" + bank + "/ket-qua/" + attempt, p.get("url").asText());
        List<String> keys = new ArrayList<>();
        p.fieldNames().forEachRemaining(keys::add);
        assertEquals(List.of("title", "body", "url"), keys);
        assertFalse(raw.contains("online learning"));
        assertTrue(p.get("url").asText().startsWith("/"));
    }
}
