package com.pps.smartenglish.ai;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.ai.dto.ScaffoldDraft;
import com.pps.smartenglish.ai.dto.ScaffoldFollowup;
import com.pps.smartenglish.ai.provider.FakeAiGradingProvider;
import com.pps.smartenglish.ai.dto.WritingGrade;
import com.pps.smartenglish.ai.dto.WritingGrade.Mark;
import com.pps.smartenglish.ai.provider.AiCallException;
import com.pps.smartenglish.ai.repository.AiJobRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class WritingFlowTest extends AiTestSupport {

    @Autowired AiJobRepository jobs;

    private static final String ESSAY = "I agree with online learning because it is flexible. However, students need discipline.";

    private String sessionUrl(long id, String action) {
        return "/api/writing/sessions/" + id + "/" + action;
    }

    private long submit(Account u, long sessionId, String text, String key) throws Exception {
        return idOf(postJson(sessionUrl(sessionId, "submit"), u, Map.of("finalText", text), key).andExpect(status().isAccepted()), "jobId");
    }

    @Test
    void submitDebitsExactlyOneQuotaAndIsIdempotent() throws Exception {
        var u = student(3);
        long session = newSession(u, writingQuestion(bank(true)));
        String key = UUID.randomUUID().toString();

        long jobId = submit(u, session, ESSAY, key);
        assertEquals(2, balance(u));
        assertEquals(1, count("select count(*) from ai_quota_ledger where user_id = ? and reason = 'GRADING' and delta = -1 and ref_id = ?", u.id(), jobId));
        assertEquals(1, count("select count(*) from ai_jobs where id = ? and charged = true and kind = 'WRITING_GRADE'", jobId));

        // gửi lại cùng khóa cùng nội dung: đúng phản hồi cũ, không trừ lượt, không tạo job mới
        postJson(sessionUrl(session, "submit"), u, Map.of("finalText", ESSAY), key).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(jobId)).andExpect(jsonPath("$.quotaRemaining").value(2));
        assertEquals(2, balance(u));
        assertEquals(1, count("select count(*) from ai_jobs where session_id = ?", session));

        postJson(sessionUrl(session, "submit"), u, Map.of("finalText", ESSAY + " More."), key).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("idempotency_key_reused"));
        // khóa khác trên session đã nộp: 409, không trừ thêm
        postJson(sessionUrl(session, "submit"), u, Map.of("finalText", ESSAY), UUID.randomUUID().toString())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("session_submitted"));
        assertEquals(2, balance(u));
        // thiếu khóa
        postJson(sessionUrl(newSession(u, writingQuestion(bank(true))), "submit"), u, Map.of("finalText", ESSAY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void concurrentSubmitsOnLastQuotaNeverGoNegative() throws Exception {
        addonProduct();
        var u = student(1);
        long q = writingQuestion(bank(true));
        long s1 = newSession(u, q);
        long s2 = newSession(u, q);
        var pool = Executors.newFixedThreadPool(2);
        List<Integer> statuses = new ArrayList<>();
        try {
            List<Callable<Integer>> calls = List.of(
                    () -> postJson(sessionUrl(s1, "submit"), u, Map.of("finalText", ESSAY), UUID.randomUUID().toString()).andReturn().getResponse().getStatus(),
                    () -> postJson(sessionUrl(s2, "submit"), u, Map.of("finalText", ESSAY), UUID.randomUUID().toString()).andReturn().getResponse().getStatus());
            for (var f : pool.invokeAll(calls)) {
                statuses.add(f.get());
            }
        } finally {
            pool.shutdown();
        }
        assertEquals(1, statuses.stream().filter(s -> s == 202).count(), "kết quả: " + statuses);
        assertEquals(1, statuses.stream().filter(s -> s == 402).count(), "kết quả: " + statuses);
        assertEquals(0, balance(u));
        assertEquals(1, count("select count(*) from ai_quota_ledger where user_id = ? and reason = 'GRADING'", u.id()));
    }

    @Test
    void outOfQuotaIs402WithAddonProducts() throws Exception {
        addonProduct();
        var u = student(0);
        long q = writingQuestion(bank(true));
        postJson("/api/writing/sessions", u, Map.of("questionId", q)).andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error.code").value("quota_exhausted"))
                .andExpect(jsonPath("$.error.details.products", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.error.details.products[0].type").value("AI_ADDON"));
        assertEquals(0, count("select count(*) from writing_sessions where user_id = ?", u.id()));
        getJson("/api/me/quota", u).andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    void scaffoldNeverDebitsAndKeepsBetweenTwoAndThreeFollowups() throws Exception {
        var u = student(5);
        long q = writingQuestion(bank(true));

        // model báo done sớm: bị bỏ qua cho tới khi đã đủ 2 câu hỏi đào sâu
        fake.override(ScaffoldFollowup.class, new ScaffoldFollowup("Tại sao?", true));
        long s = newSession(u, q);
        for (int i = 1; i <= 3; i++) {
            guard.resetRateLimit(u.id());
            postJson(sessionUrl(s, "answer"), u, Map.of("text", "Câu trả lời " + i)).andExpect(status().isAccepted());
            assertEquals(1, runner.runDueNow());
            assertEquals(i <= 2 ? i : 2, count("select step from writing_sessions where id = ?", s), "sau lượt " + i);
        }
        assertEquals("READY", jdbc.queryForObject("select status from writing_sessions where id = ?", String.class, s));
        assertEquals(5, balance(u));

        // model không bao giờ báo done: tới câu hỏi thứ 3 thì code tự đặt READY, không gọi AI nữa
        fake.reset();
        long s2 = newSession(u, q);
        for (int i = 1; i <= 3; i++) {
            guard.resetRateLimit(u.id());
            postJson(sessionUrl(s2, "answer"), u, Map.of("text", "Đáp " + i)).andExpect(status().isAccepted());
            runner.runDueNow();
        }
        assertEquals(3, count("select step from writing_sessions where id = ?", s2));
        int callsBefore = fake.calls();
        guard.resetRateLimit(u.id());
        postJson(sessionUrl(s2, "answer"), u, Map.of("text", "Đáp 4")).andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionStatus").value("READY")).andExpect(jsonPath("$.jobId").doesNotExist());
        assertEquals(callsBefore, fake.calls());
        postJson(sessionUrl(s2, "answer"), u, Map.of("text", "Đáp 5")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("session_not_active"));
        assertEquals(5, balance(u));
    }

    @Test
    void draftNeedsReadySessionAndStoresResultWithoutDebit() throws Exception {
        var u = student(2);
        long q = writingQuestion(bank(true));
        long s = newSession(u, q);
        postJson(sessionUrl(s, "draft"), u, Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("session_not_ready"));
        jdbc.update("update writing_sessions set status = 'READY' where id = ?", s);
        long jobId = idOf(postJson(sessionUrl(s, "draft"), u, Map.of()).andExpect(status().isAccepted()), "jobId");
        // đang chờ AI thì không nhận thêm bước
        postJson(sessionUrl(s, "draft"), u, Map.of()).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("session_busy"));
        runner.runDueNow();
        assertEquals(FakeAiGradingProvider.DRAFT, jdbc.queryForObject("select draft from writing_sessions where id = ?", String.class, s));
        assertEquals("DONE", jdbc.queryForObject("select status from ai_jobs where id = ?", String.class, jobId));
        assertEquals(2, balance(u));
    }

    @Test
    void truncatedDraftIsRetriedInsteadOfSaved() throws Exception {
        var u = student(2);
        long s = newSession(u, writingQuestion(bank(true)));
        jdbc.update("update writing_sessions set status = 'READY' where id = ?", s);
        long jobId = idOf(postJson(sessionUrl(s, "draft"), u, Map.of()).andExpect(status().isAccepted()), "jobId");

        fake.override(ScaffoldDraft.class, new ScaffoldDraft("Online learning is popular today. It is flexible and cheap.")); // dưới 50 từ
        assertEquals(1, runner.runDueNow());
        assertEquals("PENDING", jdbc.queryForObject("select status from ai_jobs where id = ?", String.class, jobId));
        assertEquals(1, count("select attempts from ai_jobs where id = ?", jobId));
        assertEquals(null, jdbc.queryForObject("select draft from writing_sessions where id = ?", String.class, s));

        fake.override(ScaffoldDraft.class, new ScaffoldDraft(FakeAiGradingProvider.DRAFT));
        jdbc.update("update ai_jobs set run_after = ? where id = ?", OffsetDateTime.parse("2000-01-01T00:00:00Z"), jobId);
        assertEquals(1, runner.runDueNow());
        assertEquals("DONE", jdbc.queryForObject("select status from ai_jobs where id = ?", String.class, jobId));
        assertEquals(FakeAiGradingProvider.DRAFT, jdbc.queryForObject("select draft from writing_sessions where id = ?", String.class, s));
        assertEquals(2, balance(u));
    }

    @Test
    void textLimitsAndAccessRules() throws Exception {
        var u = student(5);
        var other = student(5);
        long openQ = writingQuestion(bank(true));
        long lockedQ = writingQuestion(bank(false));
        long mcq = question(bank(true), "CONNECTIVE_MCQ", 1, "p", "{}", "{}");
        postJson("/api/writing/sessions", u, Map.of("questionId", lockedQ)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("bank_locked"));
        postJson("/api/writing/sessions", u, Map.of("questionId", mcq)).andExpect(status().isBadRequest());
        postJson("/api/writing/sessions", u, Map.of("questionId", 999999999L)).andExpect(status().isNotFound());
        var unverified = register(newEmail(), PASSWORD);
        postJson("/api/writing/sessions", unverified, Map.of("questionId", openQ)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("email_not_verified"));

        long s = newSession(u, openQ);
        String tooLong = "a".repeat(5001);
        postJson(sessionUrl(s, "answer"), u, Map.of("text", tooLong)).andExpect(status().isBadRequest());
        postJson(sessionUrl(s, "answer"), u, Map.of("text", "   ")).andExpect(status().isBadRequest());
        postJson(sessionUrl(s, "submit"), u, Map.of("finalText", tooLong), UUID.randomUUID().toString()).andExpect(status().isBadRequest());
        postJson(sessionUrl(s, "submit"), u, Map.of("finalText", ""), UUID.randomUUID().toString()).andExpect(status().isBadRequest());
        postJson(sessionUrl(s, "submit"), u, Map.of("finalText", "a".repeat(5000)), UUID.randomUUID().toString()).andExpect(status().isAccepted());

        // session của người khác coi như không tồn tại
        long s2 = newSession(u, openQ);
        guard.resetRateLimit(other.id());
        postJson(sessionUrl(s2, "answer"), other, Map.of("text", "x")).andExpect(status().isNotFound());
        postJson(sessionUrl(s2, "draft"), other, Map.of()).andExpect(status().isNotFound());
        postJson(sessionUrl(s2, "submit"), other, Map.of("finalText", "x"), UUID.randomUUID().toString()).andExpect(status().isNotFound());
        postJson("/api/writing/sessions", null, Map.of("questionId", openQ)).andExpect(status().isUnauthorized());
    }

    @Test
    void rateLimitIsFivePerMinuteAcrossAiEndpoints() throws Exception {
        var u = student(50);
        long q = writingQuestion(bank(true));
        guard.resetRateLimit(u.id());
        long s = idOf(postJson("/api/writing/sessions", u, Map.of("questionId", q)).andExpect(status().isCreated()), "sessionId");
        for (int i = 0; i < 4; i++) {
            postJson(sessionUrl(s, "answer"), u, Map.of("text", "x" + i)).andExpect(status().is(i == 0 ? 202 : 409));
        }
        postJson(sessionUrl(s, "answer"), u, Map.of("text", "x")).andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        assertEquals(50, balance(u));
    }

    @Test
    void overloadedQueueIs503BeforeAnyDebit() throws Exception {
        var u = student(2);
        long s = newSession(u, writingQuestion(bank(true)));
        var ids = new ArrayList<Object[]>();
        for (int i = 0; i < 201; i++) {
            ids.add(new Object[] {u.id()});
        }
        jdbc.batchUpdate("insert into ai_jobs (user_id, kind, status) values (?, 'SCAFFOLD_DRAFT', 'PENDING')", ids);
        try {
            postJson(sessionUrl(s, "submit"), u, Map.of("finalText", ESSAY), UUID.randomUUID().toString())
                    .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value("overloaded"))
                    .andExpect(jsonPath("$.error.retryable").value(true)).andExpect(header().exists("Retry-After"));
            assertEquals(2, balance(u));
            assertEquals("ACTIVE", jdbc.queryForObject("select status from writing_sessions where id = ?", String.class, s));
        } finally {
            jdbc.update("delete from ai_jobs where kind = 'SCAFFOLD_DRAFT' and session_id is null and user_id = ?", u.id());
        }
    }

    @Test
    void gradingResultHasCodeComputedScoreAndMachineCounts() throws Exception {
        var u = student(1);
        long q = writingQuestion(bank(true));
        long s = newSession(u, q);
        // 5 từ liên tiếp của đề bị chép lại: "online learning is better than"
        String text = "Online learning is better than a classroom for many people today.";
        long jobId = submit(u, s, text, UUID.randomUUID().toString());
        runner.runDueNow();

        // fake trả 83/77/71/64, scorePercent do code tính = trung bình 73.75 làm tròn xuống bội 5
        getJson("/api/ai/jobs/" + jobId, u).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.kind").value("WRITING_GRADE"))
                .andExpect(jsonPath("$.result.scorePercent").value(70))
                .andExpect(jsonPath("$.result.markedAnswer").value(text))
                .andExpect(jsonPath("$.result.wordCount").value(11))
                .andExpect(jsonPath("$.result.copiedWords").value(5))
                .andExpect(jsonPath("$.quotaRefunded").value(false));
        String prompt = fake.lastUserPrompt();
        assertTrue(prompt.contains("Khóa học: IELTS 3.0 → 5.0"), prompt);
        assertTrue(prompt.contains("Số từ yêu cầu: 100"), prompt);
        assertTrue(prompt.contains("Số từ máy đã đếm: 11"), prompt);
        assertTrue(prompt.contains("Số từ chép từ đề: 5"), prompt);
        assertTrue(prompt.contains("===\n" + text + "\n==="), prompt);
        assertEquals(1, count("select count(*) from ai_grading_token_usage where job_id = ?", jobId));
    }

    @Test
    void postCheckClampsScoresIgnoresModelTotalAndBuildsMarkedAnswerFromMarks() throws Exception {
        var u = student(2);
        long q = writingQuestion(bank(true));

        // điểm ngoài khoảng bị kẹp: 100, 0, 90, 85 => 68.75 => 65
        fake.override(WritingGrade.class, grade(150, -10, 90, 85, List.of()));
        long j1 = submit(u, newSession(u, q), ESSAY, UUID.randomUUID().toString());
        runner.runDueNow();
        getJson("/api/ai/jobs/" + j1, u).andExpect(jsonPath("$.result.scorePercent").value(65))
                .andExpect(jsonPath("$.result.taskResponse").value(100)).andExpect(jsonPath("$.result.coherence").value(0))
                .andExpect(jsonPath("$.result.markedAnswer").value(ESSAY)) // không có lỗi nào thì giữ nguyên bài gốc
                .andExpect(jsonPath("$.result.marks").doesNotExist());

        // model chỉ trả danh sách lỗi; code dựng markedAnswer từ bài gốc
        String marked = markedFor(u, q, ESSAY, List.of(new Mark("ok", "agree"), new Mark("gr1", "students need discipline")));
        assertEquals("I {{ok|agree}} with online learning because it is flexible. However, {{gr1|students need discipline}}.", marked);
    }

    @Test
    void repeatedQuotesAreMarkedInOrderAndExtraOnesAreDropped() throws Exception {
        var u = student(5);
        long q = writingQuestion(bank(true));
        String text = "I like it. I like it. I like it.";
        assertEquals("{{ok|I like it}}. {{gr1|I like it}}. {{wd1|I like it}}.",
                markedFor(u, q, text, List.of(new Mark("ok", "I like it"), new Mark("gr1", "I like it"), new Mark("wd1", "I like it"),
                        new Mark("sp1", "I like it")))); // lần thứ 4 không còn chỗ trống nên bị bỏ
        // thứ tự không theo bài: mục sau không thấy phía sau thì tìm từ đầu bài, vẫn không chồng lên chỗ đã đánh dấu
        assertEquals("alpha {{wd1|beta}} {{gr1|gamma}} alpha",
                markedFor(u, q, "alpha beta gamma alpha", List.of(new Mark("gr1", "gamma"), new Mark("wd1", "beta"))));
    }

    @Test
    void unknownQuotesAndCodesAreDroppedAndOverlapsKeepOne() throws Exception {
        var u = student(5);
        long q = writingQuestion(bank(true));
        String text = "The big dog barks loudly.";
        assertEquals(text, markedFor(u, q, text, List.of(new Mark("gr1", "not in the essay"), new Mark("zz9", "big dog"),
                new Mark("ok", ""), new Mark(null, "dog"))));
        assertEquals("The {{gr1|big dog barks}} loudly.",
                markedFor(u, q, text, List.of(new Mark("gr1", "big dog barks"), new Mark("wd1", "dog barks loudly"))));
        // khớp đúng ký tự, phân biệt hoa thường
        assertEquals(text, markedFor(u, q, text, List.of(new Mark("gr1", "BIG DOG"))));
    }

    @Test
    void newlinesAndDoubleSpacesOfTheOriginalSurvive() throws Exception {
        var u = student(5);
        long q = writingQuestion(bank(true));
        String text = "Line one  has  gaps.\n\nSecond   line here.\r\n  Tail with trailing space ";
        String marked = markedFor(u, q, text, List.of(new Mark("sp1", "has  gaps"), new Mark("pu1", "Second   line"),
                new Mark("wd2", "Tail with")));
        assertTrue(marked.contains("{{sp1|has  gaps}}") && marked.contains("{{pu1|Second   line}}") && marked.contains("{{wd2|Tail with}}"), marked);
        // bỏ token đi phải ra đúng bài gốc từng ký tự
        assertEquals(text, marked.replaceAll("\\{\\{[a-z]{2}\\d?\\|(.*?)\\}\\}", "$1"));
    }

    private WritingGrade grade(int tr, int cc, int lr, int gr, List<Mark> marks) {
        return new WritingGrade(tr, cc, lr, gr, marks, "fixed", new WritingGrade.Connectives(2, 1, 2, List.of(), "ok"),
                List.of(), null, "fb");
    }

    /** Nộp bài, chạy job với danh sách lỗi cho trước và trả markedAnswer trong kết quả. */
    private String markedFor(Account u, long q, String text, List<Mark> marks) throws Exception {
        fake.override(WritingGrade.class, grade(80, 80, 80, 80, marks));
        long job = submit(u, newSession(u, q), text, UUID.randomUUID().toString());
        runner.runDueNow();
        var body = getJson("/api/ai/jobs/" + job, u).andExpect(jsonPath("$.status").value("DONE"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).at("/result/markedAnswer").asText();
    }

    @Test
    void jobThatExhaustsRetriesFailsAndRefundsExactlyOneQuota() throws Exception {
        var u = student(1);
        long jobId = submit(u, newSession(u, writingQuestion(bank(true))), ESSAY, UUID.randomUUID().toString());
        assertEquals(0, balance(u));
        fake.failNext(10, new AiCallException(true, "Nhà cung cấp AI tạm thời không phản hồi", null, null));

        for (int attempt = 1; attempt <= 3; attempt++) {
            assertEquals(1, runner.runDueNow(), "lượt thử " + attempt);
            assertEquals(attempt, count("select attempts from ai_jobs where id = ?", jobId));
            if (attempt < 3) {
                assertEquals("PENDING", jdbc.queryForObject("select status from ai_jobs where id = ?", String.class, jobId));
                // chưa tới hạn thử lại thì worker không lấy
                assertEquals(0, runner.runDueNow());
                jdbc.update("update ai_jobs set run_after = ? where id = ?", OffsetDateTime.parse("2000-01-01T00:00:00Z"), jobId);
            }
        }
        assertEquals("FAILED", jdbc.queryForObject("select status from ai_jobs where id = ?", String.class, jobId));
        assertEquals(1, balance(u));
        assertEquals(1, count("select count(*) from ai_quota_ledger where user_id = ? and reason = 'REFUND' and ref_id = ?", u.id(), jobId));
        assertEquals(0, runner.runDueNow());
        assertEquals(1, balance(u));
        getJson("/api/ai/jobs/" + jobId, u).andExpect(jsonPath("$.status").value("FAILED")).andExpect(jsonPath("$.quotaRefunded").value(true))
                .andExpect(jsonPath("$.error").isNotEmpty()).andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    void permanentErrorFailsImmediatelyAndRefunds() throws Exception {
        var u = student(1);
        long jobId = submit(u, newSession(u, writingQuestion(bank(true))), ESSAY, UUID.randomUUID().toString());
        fake.failNext(1, new AiCallException(false, "Mô hình từ chối trả lời", null, null));
        assertEquals(1, runner.runDueNow());
        assertEquals("FAILED", jdbc.queryForObject("select status from ai_jobs where id = ?", String.class, jobId));
        assertEquals(1, count("select attempts from ai_jobs where id = ?", jobId));
        assertEquals(1, balance(u));
    }

    @Test
    void runningJobPastItsLeaseIsReclaimedAndExhaustedOnesFail() throws Exception {
        var u = student(2);
        long q = writingQuestion(bank(true));
        long reclaimed = submit(u, newSession(u, q), ESSAY, UUID.randomUUID().toString());
        jdbc.update("update ai_jobs set status = 'RUNNING', attempts = 1, locked_until = ? where id = ?",
                OffsetDateTime.parse("2000-01-01T00:00:00Z"), reclaimed);
        assertEquals(1, runner.runDueNow());
        assertEquals("DONE", jdbc.queryForObject("select status from ai_jobs where id = ?", String.class, reclaimed));
        assertEquals(2, count("select attempts from ai_jobs where id = ?", reclaimed));

        long dead = submit(u, newSession(u, q), ESSAY, UUID.randomUUID().toString());
        assertEquals(0, balance(u));
        jdbc.update("update ai_jobs set status = 'RUNNING', attempts = 3, locked_until = ? where id = ?",
                OffsetDateTime.parse("2000-01-01T00:00:00Z"), dead);
        assertEquals(0, runner.runDueNow()); // hết lần thử: không chạy tiếp, chuyển FAILED
        assertEquals("FAILED", jdbc.queryForObject("select status from ai_jobs where id = ?", String.class, dead));
        assertEquals(1, balance(u));
        assertEquals(1, count("select count(*) from ai_quota_ledger where reason = 'REFUND' and ref_id = ?", dead));
    }

    @Test
    void staleResultFromAnOlderAttemptIsIgnored() throws Exception {
        var u = student(1);
        long jobId = submit(u, newSession(u, writingQuestion(bank(true))), ESSAY, UUID.randomUUID().toString());
        // worker cũ giữ bản job attempts=1; lease hết hạn, worker mới đã lấy lại (attempts=2)
        jdbc.update("update ai_jobs set status = 'RUNNING', attempts = 1 where id = ?", jobId);
        var stale = jobs.findById(jobId).orElseThrow();
        jdbc.update("update ai_jobs set attempts = 2 where id = ?", jobId);
        var finisher = applicationFinisher();
        assertFalse(finisher.complete(stale, json.createObjectNode().put("late", true), j -> {
            throw new AssertionError("tác dụng phụ không được chạy với kết quả cũ");
        }));
        assertEquals("RUNNING", jdbc.queryForObject("select status from ai_jobs where id = ?", String.class, jobId));
        assertEquals(null, finisher.fail(stale, false, "muộn"));
        assertEquals(0, count("select count(*) from ai_quota_ledger where reason = 'REFUND' and ref_id = ?", jobId));
    }

    @Test
    void onlyTheOwnerReadsJobsAndStreams() throws Exception {
        var u = student(1);
        var other = student(1);
        long jobId = submit(u, newSession(u, writingQuestion(bank(true))), ESSAY, UUID.randomUUID().toString());
        getJson("/api/ai/jobs/" + jobId, other).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("job_not_found"));
        getJson("/api/ai/jobs/" + jobId + "/stream", other).andExpect(status().isNotFound());
        getJson("/api/ai/jobs/abc", u).andExpect(status().isNotFound());
        getJson("/api/ai/jobs/" + jobId, u).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.result").doesNotExist());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/ai/jobs/" + jobId)).andExpect(status().isUnauthorized());
    }

    @Test
    void streamOfFinishedJobSendsResultImmediately() throws Exception {
        var u = student(1);
        long jobId = submit(u, newSession(u, writingQuestion(bank(true))), ESSAY, UUID.randomUUID().toString());
        runner.runDueNow();
        var res = getJson("/api/ai/jobs/" + jobId + "/stream", u).andExpect(status().isOk()).andReturn().getResponse();
        assertTrue(res.getContentType().startsWith("text/event-stream"), res.getContentType());
        String body = res.getContentAsString();
        assertTrue(body.contains("event:progress"), body);
        assertTrue(body.contains("event:result"), body);
        assertTrue(body.contains("\"status\":\"DONE\"") && body.contains("scorePercent"), body);
        assertFalse(body.contains("event:error"), body);
    }

    @Test
    void streamOfFailedJobSendsError() throws Exception {
        var u = student(1);
        long jobId = submit(u, newSession(u, writingQuestion(bank(true))), ESSAY, UUID.randomUUID().toString());
        fake.failNext(1, new AiCallException(false, "từ chối", null, null));
        runner.runDueNow();
        getJson("/api/ai/jobs/" + jobId + "/stream", u).andExpect(status().isOk())
                .andExpect(content().string(containsString("event:error")))
                .andExpect(content().string(not(containsString("event:result"))));
    }

    @Autowired com.pps.smartenglish.ai.worker.AiJobFinisher finisher;

    private com.pps.smartenglish.ai.worker.AiJobFinisher applicationFinisher() {
        return finisher;
    }
}
