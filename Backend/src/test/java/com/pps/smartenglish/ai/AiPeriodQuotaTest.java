package com.pps.smartenglish.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.ai.domain.AiPeriod;
import com.pps.smartenglish.ai.domain.AiTask;
import com.pps.smartenglish.ai.provider.AiCallException;
import com.pps.smartenglish.ai.service.AiQuotaService;
import com.pps.smartenglish.support.MutableClockConfig;
import com.pps.smartenglish.support.MutableClockConfig.MutableClock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

/** FR-AI-04, D-31, D-32, D-47: lượt định kỳ theo khóa + pool, trọng số, kỳ tuần/tháng, hoàn đúng nguồn, chống race. */
@Import(MutableClockConfig.class)
class AiPeriodQuotaTest extends AiTestSupport {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String ESSAY = "I agree with online learning because it is flexible.";

    @Autowired MutableClock clock;
    @Autowired AiQuotaService quotaService;
    @Autowired TransactionTemplate tx;

    @BeforeEach
    void realWeights() {
        defaultWeights(); // chạy sau unitWeights() của lớp cha
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private long courseOf(long bankId) {
        return jdbc.queryForObject("select c.id from question_banks b join topics t on t.id = b.topic_id "
                + "join course_levels l on l.id = t.level_id join courses c on c.id = l.course_id where b.id = ?", Long.class, bankId);
    }

    private void enroll(Account u, long courseId, Instant expiresAt) {
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', ?, ?)",
                u.id(), courseId, OffsetDateTime.parse("2020-01-01T00:00:00Z"), expiresAt == null ? null : OffsetDateTime.ofInstant(expiresAt, java.time.ZoneOffset.UTC));
    }

    private void setCourse(long courseId, String period, int quota) {
        jdbc.update("update courses set ai_period = ?, ai_period_quota = ? where id = ?", period, quota, courseId);
    }

    private int used(Account u, long courseId) {
        var r = jdbc.queryForList("select used from ai_period_usage where user_id = ? and course_id = ? order by period_start desc", Integer.class, u.id(), courseId);
        return r.isEmpty() ? 0 : r.get(0);
    }

    private long writingTask1(long bankId) {
        return question(bankId, "WRITING", 2, "Mô tả biểu đồ", "{\"task\":\"Describe the chart.\",\"requiredWords\":150,\"ieltsTask\":\"TASK1\"}", "{}");
    }

    private org.springframework.test.web.servlet.ResultActions submit(Account u, long questionId) throws Exception {
        long s = newSession(u, questionId);
        return postJson("/api/writing/sessions/" + s + "/submit", u, Map.of("finalText", ESSAY), UUID.randomUUID().toString());
    }

    private record Setup(long bank, long course, long task2, long task1) {}

    private Setup setup() {
        long bank = bank(true);
        return new Setup(bank, courseOf(bank), writingQuestion(bank), writingTask1(bank));
    }

    @Test
    void periodicQuotaIsDebitedByTaskWeightAndExposedByApi() throws Exception {
        var u = student(5);
        var s = setup();
        enroll(u, s.course, null);

        submit(u, s.task2).andExpect(status().isAccepted()).andExpect(jsonPath("$.quotaRemaining").value(17 + 5));
        assertEquals(3, used(u, s.course));
        assertEquals(5, balance(u));
        submit(u, s.task1).andExpect(status().isAccepted());
        assertEquals(5, used(u, s.course)); // Task 1 = 2
        assertEquals(1, count("select count(*) from ai_quota_ledger where user_id = ? and reason = 'GRADING' and source = 'PERIODIC' and delta = -3 and course_id = ?", u.id(), s.course));
        assertEquals(0, count("select count(*) from ai_quota_ledger where user_id = ? and source = 'POOL'", u.id()));

        getJson("/api/me/quota", u).andExpect(status().isOk()).andExpect(jsonPath("$.pool").value(5))
                .andExpect(jsonPath("$.courses.length()").value(1))
                .andExpect(jsonPath("$.courses[0].courseId").value(s.course))
                .andExpect(jsonPath("$.courses[0].courseTitle").value("IELTS 3.0 → 5.0"))
                .andExpect(jsonPath("$.courses[0].period").value("WEEK"))
                .andExpect(jsonPath("$.courses[0].quota").value(20))
                .andExpect(jsonPath("$.courses[0].used").value(5))
                .andExpect(jsonPath("$.courses[0].remaining").value(15))
                .andExpect(jsonPath("$.courses[0].resetsAt").isNotEmpty());

        // đổi cấu hình giữa kỳ áp dụng ngay: còn lại = max(0, quota mới - used)
        setCourse(s.course, "WEEK", 2);
        getJson("/api/me/quota", u).andExpect(jsonPath("$.courses[0].remaining").value(0));
        setCourse(s.course, "WEEK", 8);
        getJson("/api/me/quota", u).andExpect(jsonPath("$.courses[0].remaining").value(3));
    }

    @Test
    void shortfallComesFromPoolAndInsufficientTotalChargesNothing() throws Exception {
        var u = student(1);
        var s = setup();
        enroll(u, s.course, null);
        setCourse(s.course, "WEEK", 2);

        long session = newSession(u, s.task2); // 2 + 1 = 3 đủ cho Task 2
        submit(u, s.task2).andExpect(status().isAccepted()); // phiên thứ hai dùng hết: 2 định kỳ + 1 pool
        assertEquals(2, used(u, s.course));
        assertEquals(0, balance(u));
        long job = jdbc.queryForObject("select max(id) from ai_jobs where user_id = ?", Long.class, u.id());
        assertEquals(2, count("select charged_periodic from ai_jobs where id = ?", job));
        assertEquals(1, count("select charged_pool from ai_jobs where id = ?", job));
        assertEquals(1, count("select count(*) from ai_quota_ledger where ref_id = ? and reason = 'GRADING' and source = 'POOL' and delta = -1", job));

        // hết sạch: phiên mới bị chặn ngay, nộp phiên cũ cũng 402 và không để lại job
        int jobs = count("select count(*) from ai_jobs where user_id = ?", u.id());
        postJson("/api/writing/sessions", u, Map.of("questionId", s.task2)).andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error.code").value("quota_exhausted"));
        postJson("/api/writing/sessions/" + session + "/submit", u, Map.of("finalText", ESSAY), UUID.randomUUID().toString())
                .andExpect(status().isPaymentRequired()).andExpect(jsonPath("$.error.code").value("quota_exhausted"));
        assertEquals(jobs, count("select count(*) from ai_jobs where user_id = ?", u.id()));
        assertEquals(2, used(u, s.course));
        assertEquals(0, balance(u));
    }

    @Test
    void insufficientTotalDoesNotPartiallyDebit() throws Exception {
        var u = student(1);
        var s = setup();
        enroll(u, s.course, null);
        setCourse(s.course, "WEEK", 3);
        long session = newSession(u, s.task2);
        setCourse(s.course, "WEEK", 1); // 1 + pool 1 = 2 < 3
        postJson("/api/writing/sessions/" + session + "/submit", u, Map.of("finalText", ESSAY), UUID.randomUUID().toString())
                .andExpect(status().isPaymentRequired());
        assertEquals(0, used(u, s.course));
        assertEquals(1, balance(u));
        assertEquals(0, count("select count(*) from ai_quota_ledger where user_id = ?", u.id()));
        assertEquals(0, count("select count(*) from ai_jobs where user_id = ?", u.id()));
    }

    @Test
    void weekResetsOnMondayVietnamTime() throws Exception {
        var u = student(0);
        var s = setup();
        enroll(u, s.course, null);
        setCourse(s.course, "WEEK", 3);
        LocalDate today = LocalDate.now(VN);
        var nextMonday = AiPeriod.WEEK.next(AiPeriod.WEEK.start(today)).atStartOfDay(VN).toInstant();

        clock.set(nextMonday.minusSeconds(3600)); // Chủ nhật 23:00 giờ VN
        submit(u, s.task2).andExpect(status().isAccepted());
        postJson("/api/writing/sessions", u, Map.of("questionId", s.task2)).andExpect(status().isPaymentRequired());

        clock.set(nextMonday); // 00:00 thứ Hai: kỳ mới, đủ lại
        getJson("/api/me/quota", u).andExpect(jsonPath("$.courses[0].used").value(0)).andExpect(jsonPath("$.courses[0].remaining").value(3));
        submit(u, s.task2).andExpect(status().isAccepted());
        assertEquals(2, count("select count(*) from ai_period_usage where user_id = ? and used = 3", u.id()));
    }

    @Test
    void monthPeriodResetsOnFirstOfMonth() throws Exception {
        var u = student(0);
        var s = setup();
        enroll(u, s.course, null);
        setCourse(s.course, "MONTH", 3);
        LocalDate today = LocalDate.now(VN);
        var nextMonth = AiPeriod.MONTH.next(AiPeriod.MONTH.start(today));

        clock.set(nextMonth.atStartOfDay(VN).toInstant().minusSeconds(3600));
        submit(u, s.task2).andExpect(status().isAccepted());
        assertEquals(1, count("select count(*) from ai_period_usage where user_id = ? and period_start = ?", u.id(), AiPeriod.MONTH.start(today)));
        postJson("/api/writing/sessions", u, Map.of("questionId", s.task2)).andExpect(status().isPaymentRequired());

        clock.set(nextMonth.atStartOfDay(VN).toInstant());
        getJson("/api/me/quota", u).andExpect(jsonPath("$.courses[0].period").value("MONTH")).andExpect(jsonPath("$.courses[0].remaining").value(3));
        submit(u, s.task2).andExpect(status().isAccepted());
        assertEquals(1, count("select count(*) from ai_period_usage where user_id = ? and period_start = ?", u.id(), nextMonth));
    }

    @Test
    void withoutActiveEnrollmentOnlyPoolIsUsed() throws Exception {
        var u = student(3);
        var s = setup(); // không ghi danh (học thử)
        submit(u, s.task2).andExpect(status().isAccepted()).andExpect(jsonPath("$.quotaRemaining").value(0));
        assertEquals(0, balance(u));
        assertEquals(0, count("select count(*) from ai_period_usage where user_id = ?", u.id()));
        getJson("/api/me/quota", u).andExpect(jsonPath("$.pool").value(0)).andExpect(jsonPath("$.courses.length()").value(0));

        // ghi danh đã hết hạn cũng coi như không có
        var expired = student(0);
        enroll(expired, s.course, Instant.now().minusSeconds(3600));
        postJson("/api/writing/sessions", expired, Map.of("questionId", s.task2)).andExpect(status().isPaymentRequired());
        getJson("/api/me/quota", expired).andExpect(jsonPath("$.courses.length()").value(0));
    }

    @Test
    void failedJobRefundsToTheSourceThatWasDebited() throws Exception {
        var u = student(1);
        var s = setup();
        enroll(u, s.course, null);
        setCourse(s.course, "WEEK", 2);
        long jobId = idOf(submit(u, s.task2).andExpect(status().isAccepted()), "jobId"); // 2 định kỳ + 1 pool
        assertEquals(2, used(u, s.course));
        assertEquals(0, balance(u));

        fake.failNext(10, new AiCallException(false, "Lỗi chấm", null, null));
        assertEquals(1, runner.runDueNow());
        assertEquals("FAILED", jdbc.queryForObject("select status from ai_jobs where id = ?", String.class, jobId));
        assertEquals(0, used(u, s.course));
        assertEquals(1, balance(u));
        assertEquals(1, count("select count(*) from ai_quota_ledger where ref_id = ? and reason = 'REFUND' and source = 'PERIODIC' and delta = 2", jobId));
        assertEquals(1, count("select count(*) from ai_quota_ledger where ref_id = ? and reason = 'REFUND' and source = 'POOL' and delta = 1", jobId));
        assertEquals(0, runner.runDueNow());
        assertEquals(1, balance(u));
    }

    @Test
    void refundAfterPeriodChangeLowersTheOldPeriodUsage() throws Exception {
        var u = student(0);
        var s = setup();
        enroll(u, s.course, null);
        setCourse(s.course, "WEEK", 3);
        LocalDate today = LocalDate.now(VN);
        var nextMonday = AiPeriod.WEEK.next(AiPeriod.WEEK.start(today)).atStartOfDay(VN).toInstant();
        clock.set(nextMonday.minusSeconds(60));
        long jobId = idOf(submit(u, s.task2).andExpect(status().isAccepted()), "jobId");
        clock.set(nextMonday.plusSeconds(60)); // job lỗi sau khi sang tuần mới
        fake.failNext(10, new AiCallException(false, "Lỗi chấm", null, null));
        assertEquals(1, runner.runDueNow());
        assertEquals("FAILED", jdbc.queryForObject("select status from ai_jobs where id = ?", String.class, jobId));
        assertEquals(0, count("select coalesce(sum(used), 0) from ai_period_usage where user_id = ?", u.id()));
        getJson("/api/me/quota", u).andExpect(jsonPath("$.courses[0].remaining").value(3));
    }

    @Test
    void concurrentChargesNeverExceedPeriodicPlusPool() throws Exception {
        var u = student(4);
        var s = setup();
        enroll(u, s.course, null);
        setCourse(s.course, "WEEK", 2); // tổng khả dụng 2 + 4 = 6 = đúng hai lần Task 2 (3)
        var pool = Executors.newFixedThreadPool(8);
        try {
            var results = new ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int i = 0; i < 8; i++) {
                long fakeJob = 9_000_000L + i + Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000) * 10;
                results.add(pool.submit(() -> tx.execute(st -> quotaService.charge(u.id(), fakeJob, s.course, AiTask.WRITING_TASK2).isPresent())));
            }
            int ok = 0;
            for (var f : results) {
                ok += f.get() ? 1 : 0;
            }
            assertEquals(2, ok);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(2, used(u, s.course));
        assertEquals(0, balance(u));
    }

    @Test
    void adminEditsCourseQuotaAndWeights() throws Exception {
        var admin = admin("ADMIN");
        var s = setup();
        var patch = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/admin/cms/courses/" + s.course)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON);
        send(patch.content(json.writeValueAsString(Map.of("aiPeriod", "MONTH", "aiPeriodQuota", 40))), admin.accessToken())
                .andExpect(status().isOk()).andExpect(jsonPath("$.aiPeriod").value("MONTH")).andExpect(jsonPath("$.aiPeriodQuota").value(40));
        assertEquals(40, count("select ai_period_quota from courses where id = ?", s.course));
        assertEquals(1, count("select count(*) from audit_logs where target_type = 'COURSE' and target_id = ? and action = 'UPDATE'", String.valueOf(s.course)));
        send(patch.content(json.writeValueAsString(Map.of("aiPeriod", "DAY"))), admin.accessToken()).andExpect(status().isBadRequest());
        send(patch.content(json.writeValueAsString(Map.of("aiPeriodQuota", -1))), admin.accessToken()).andExpect(status().isBadRequest());

        var put = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/ai-weights")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON);
        try {
            send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/admin/ai-weights"), admin.accessToken())
                    .andExpect(status().isOk()).andExpect(jsonPath("$.weights.REWRITE_SET").value(1))
                    .andExpect(jsonPath("$.weights.WRITING_TASK1").value(2)).andExpect(jsonPath("$.weights.WRITING_TASK2").value(3))
                    .andExpect(jsonPath("$.weights.SPEAKING_PART").value(2));
            send(put.content(json.writeValueAsString(Map.of("weights", Map.of("WRITING_TASK2", 4)))), admin.accessToken())
                    .andExpect(status().isOk()).andExpect(jsonPath("$.weights.WRITING_TASK2").value(4));
            assertEquals(4, count("select weight from ai_task_weights where code = 'WRITING_TASK2'"));
            send(put.content(json.writeValueAsString(Map.of("weights", Map.of("WRITING_TASK2", 0)))), admin.accessToken()).andExpect(status().isBadRequest());
            send(put.content(json.writeValueAsString(Map.of("weights", Map.of("WRITING_TASK2", 21)))), admin.accessToken()).andExpect(status().isBadRequest());
            send(put.content(json.writeValueAsString(Map.of("weights", Map.of("NOPE", 2)))), admin.accessToken()).andExpect(status().isBadRequest());
            assertEquals(4, count("select weight from ai_task_weights where code = 'WRITING_TASK2'"));
            assertTrue(count("select count(*) from audit_logs where target_type = 'AI_TASK_WEIGHTS'") >= 1);
        } finally {
            jdbc.update("update ai_task_weights set weight = 3 where code = 'WRITING_TASK2'");
        }
    }

    @Test
    void adminEndpointsNeedPermissions() throws Exception {
        var stu = student(0);
        var editor = admin("EDITOR"); // có cms.content nhưng không có product.manage
        var s = setup();
        var put = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/ai-weights")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"weights\":{\"REWRITE_SET\":2}}");
        for (var who : List.of(stu, editor)) {
            send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/admin/ai-weights"), who.accessToken()).andExpect(status().isForbidden());
            send(put, who.accessToken()).andExpect(status().isForbidden());
        }
        assertEquals(1, count("select weight from ai_task_weights where code = 'REWRITE_SET'"));
        var patch = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/admin/cms/courses/" + s.course)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"aiPeriodQuota\":5}");
        send(patch, stu.accessToken()).andExpect(status().isForbidden());
    }

    @Test
    void writingPayloadAcceptsOptionalIeltsTaskOnly() throws Exception {
        var admin = admin("ADMIN");
        long bank = bank(true);
        var post = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/admin/cms/banks/" + bank + "/questions")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON);
        for (var task : List.of("TASK1", "TASK2")) {
            send(post.content(json.writeValueAsString(Map.of("type", "WRITING", "prompt", "Viết", "payload",
                    Map.of("task", "Describe", "requiredWords", 150, "ieltsTask", task)))), admin.accessToken()).andExpect(status().isCreated());
        }
        send(post.content(json.writeValueAsString(Map.of("type", "WRITING", "prompt", "Viết", "payload",
                Map.of("task", "Describe", "requiredWords", 150)))), admin.accessToken()).andExpect(status().isCreated());
        send(post.content(json.writeValueAsString(Map.of("type", "WRITING", "prompt", "Viết", "payload",
                Map.of("task", "Describe", "requiredWords", 150, "ieltsTask", "TASK3")))), admin.accessToken()).andExpect(status().isBadRequest());
    }

    private Account admin(String role) throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = ?", u.id(), role);
        return login(u.email(), PASSWORD);
    }
}
