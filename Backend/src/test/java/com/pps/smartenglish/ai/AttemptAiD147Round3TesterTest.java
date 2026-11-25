package com.pps.smartenglish.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pps.smartenglish.ai.domain.AiJobKind;
import com.pps.smartenglish.ai.domain.AiTask;
import com.pps.smartenglish.ai.dto.WritingGrade;
import com.pps.smartenglish.ai.provider.AiCallException;
import com.pps.smartenglish.ai.repository.AiJobRepository;
import com.pps.smartenglish.ai.service.AiJobService;
import com.pps.smartenglish.ai.service.AttemptLinks;
import com.pps.smartenglish.ai.speaking.FakeSpeakingEngineClient;
import com.pps.smartenglish.ai.worker.AiJobClaimer;
import com.pps.smartenglish.ai.worker.AiJobFinisher;
import com.pps.smartenglish.exercise.dto.SubmitRequest;
import com.pps.smartenglish.exercise.repository.StudentAttemptRepository;
import com.pps.smartenglish.exercise.service.AttemptService;
import com.pps.smartenglish.exercise.service.AttemptTimeoutJob;
import com.pps.smartenglish.exercise.service.SubmissionService;
import com.pps.smartenglish.support.FakeStorageConfig.FakeStorage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.support.TransactionTemplate;

/** Tester D-147 vòng 3: thứ tự khóa trên mọi đường, đường lách quy tắc một lượt mở, điểm tạm ở các API khác, bài giao không trừ lượt. */
class AttemptAiD147Round3TesterTest extends AiTestSupport {

    private static final String ESSAY = "I agree with online learning because it is flexible. However, students need discipline.";
    private static final String CUE = "{\"part\":1,\"prompt\":\"Describe a trip.\"}";
    private static final OffsetDateTime PAST = OffsetDateTime.parse("2000-01-01T00:00:00Z");

    @Autowired TransactionTemplate tx;
    @Autowired AttemptLinks links;
    @Autowired AiJobService aiJobs;
    @Autowired AiJobRepository jobRepo;
    @Autowired AiJobClaimer claimer;
    @Autowired AiJobFinisher finisher;
    @Autowired SubmissionService submissions;
    @Autowired AttemptService attemptService;
    @Autowired StudentAttemptRepository attemptRepo;
    @Autowired AttemptTimeoutJob timeoutJob;
    @Autowired FakeSpeakingEngineClient engine;
    @Autowired FakeStorage storage;
    @Autowired ObjectMapper mapper;

    @BeforeEach
    void resetEngine() {
        engine.reset();
    }

    /** Job treo do test đua để lại (vd. job Nói giả submissionId -1) không được lọt sang lớp test sau qua runner.runDueNow. */
    @org.junit.jupiter.api.AfterEach
    void dropLeftoverJobs() {
        jdbc.update("update ai_jobs set status = 'FAILED', locked_until = null where status in ('PENDING', 'RUNNING')");
    }

    // ---------- helpers ----------

    private static WritingGrade grade(int pct) {
        return new WritingGrade(pct, pct, pct, pct, List.of(), "fixed text", new WritingGrade.Connectives(2, 1, 2, List.of(), "ok"), List.of(), null, "fb");
    }

    private ResultActions start(Account u, long bank) throws Exception {
        return postJson("/api/banks/" + bank + "/attempts", u, Map.of());
    }

    private long startAttempt(Account u, long bank) throws Exception {
        return idOf(start(u, bank).andExpect(status().isCreated()), "id");
    }

    private long openWriting(Account u, long q, Long attempt) throws Exception {
        guard.resetRateLimit(u.id());
        var body = new HashMap<String, Object>(Map.of("questionId", q));
        if (attempt != null) {
            body.put("attemptId", attempt);
        }
        return idOf(postJson("/api/writing/sessions", u, body).andExpect(status().isCreated()), "sessionId");
    }

    private ResultActions submitWriting(Account u, long session) throws Exception {
        guard.resetRateLimit(u.id());
        return postJson("/api/writing/sessions/" + session + "/submit", u, Map.of("finalText", ESSAY), UUID.randomUUID().toString());
    }

    private ResultActions submitAttempt(Account u, long attempt) throws Exception {
        return postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers", List.of()));
    }

    private static byte[] wav(double seconds) {
        int data = (int) (seconds * 32000);
        var b = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + data).put("WAVE".getBytes()).put("fmt ".getBytes()).putInt(16).putShort((short) 1)
                .putShort((short) 1).putInt(16000).putInt(32000).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes()).putInt(data);
        return b.array();
    }

    /** Tạo bản nói (gắn lượt nếu attempt khác null) và tải audio lên; chưa xác nhận. */
    private long uploadSpeaking(Account u, long q, Long attempt) throws Exception {
        guard.resetRateLimit(u.id());
        var body = new HashMap<String, Object>(Map.of("questionId", q));
        if (attempt != null) {
            body.put("attemptId", attempt);
        }
        long id = idOf(postJson("/api/speaking/submissions", u, body).andExpect(status().isCreated()), "submissionId");
        storage.put(jdbc.queryForObject("select object_key from speaking_submissions where id = ?", String.class, id), wav(20));
        return id;
    }

    private ResultActions completeSpeaking(Account u, long submission) throws Exception {
        guard.resetRateLimit(u.id());
        return postJson("/api/speaking/submissions/" + submission + "/complete", u, Map.of());
    }

    private ResultActions call(HttpMethod method, String url, Account who) throws Exception {
        return send(MockMvcRequestBuilders.request(method, url).contentType(MediaType.APPLICATION_JSON).content("{}"), who.accessToken());
    }

    private int refunds(Account u) {
        return count("select count(*) from ai_quota_ledger where user_id = ? and reason = 'REFUND'", u.id());
    }

    private String jobStatus(long attempt) {
        return jdbc.queryForObject("select status from ai_jobs where attempt_id = ?", String.class, attempt);
    }

    /** Lớp + bài giao dạng bộ bài có 1 câu Viết và 1 câu Nói; học viên đã ghi danh khóa và vào lớp. */
    private record Asg(long classId, long bank, long writingQ, long speakingQ, long assignmentId, Account teacher, Account student) {}

    private Asg assignment(int quota) throws Exception {
        long c = insert("courses", Map.of("slug", "asg-" + UUID.randomUUID(), "title", "Khóa giao bài", "status", "PUBLISHED",
                "delivery_mode", "CLASS_BASED", "sequential", false));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED", "sort_order", 0));
        long t = insert("topics", Map.of("level_id", lv, "title", "Unit A", "is_free_trial", false, "status", "PUBLISHED", "sort_order", 0));
        long bank = insert("question_banks", Map.of("topic_id", t, "title", "Bộ giao", "is_trial", false, "status", "PUBLISHED", "mode", "PRACTICE"));
        long wq = writingQuestion(bank);
        long sq = question(bank, "SPEAKING", 2, "Describe a trip.", CUE, "{}");
        long cls = insert("classes", Map.of("course_id", c, "name", "Lớp " + UUID.randomUUID().toString().substring(0, 4),
                "starts_on", LocalDate.now().minusDays(10), "ends_on", LocalDate.now().plusDays(60), "mode", "ONLINE", "capacity", 20));
        var teacher = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = 'TEACHER'", teacher.id());
        teacher = login(teacher.email(), PASSWORD);
        jdbc.update("insert into class_staff (class_id, user_id, role, assigned_from) values (?, ?, 'TEACHER', ?)", cls, teacher.id(), LocalDate.now().minusDays(10));
        var s = student(quota);
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', TIMESTAMP '2020-01-01 00:00:00', TIMESTAMP '2099-01-01 00:00:00')",
                s.id(), c);
        jdbc.update("insert into class_members (class_id, course_id, user_id, status, live_key, joined_at) values (?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP)",
                cls, c, s.id(), c + ":" + s.id());
        var body = Map.of("bankId", bank, "dueAt", Instant.now().plus(48, ChronoUnit.HOURS).toString());
        long asg = idOf(send(MockMvcRequestBuilders.post("/api/classes/" + cls + "/assignments").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)), teacher.accessToken()).andExpect(status().isCreated()), "id");
        return new Asg(cls, bank, wq, sq, asg, teacher, s);
    }

    private ResultActions startAssigned(Asg a) throws Exception {
        return call(HttpMethod.POST, "/api/classes/" + a.classId() + "/assignments/" + a.assignmentId() + "/start", a.student());
    }

    // ---------- 1. Một lượt mở mỗi bộ: các đường lách ----------

    /** Bài giao và lượt tự học cùng bộ: đã có lượt tự học mở thì bắt đầu bài giao cũng phải 409 attempt_in_progress (D-147 mục 10). */
    @Test
    void assignedStartWhileSelfStudyAttemptOpenOnSameBankIsRejected() throws Exception {
        var a = assignment(5);
        long self = startAttempt(a.student(), a.bank());
        startAssigned(a).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("attempt_in_progress"))
                .andExpect(jsonPath("$.error.details.attemptId").value(self));
        assertEquals(1, count("select count(*) from student_exercise_attempts where user_id = ? and submitted_at is null", a.student().id()));
    }

    /** Chiều ngược lại: bài giao đang mở thì bắt đầu lượt tự học cùng bộ bị chặn (đã đúng). */
    @Test
    void selfStudyStartWhileAssignedAttemptOpenIsRejected() throws Exception {
        var a = assignment(5);
        long asgAttempt = idOf(startAssigned(a).andExpect(status().isOk()), "id");
        start(a.student(), a.bank()).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("attempt_in_progress"))
                .andExpect(jsonPath("$.error.details.attemptId").value(asgAttempt));
    }

    /** Hai tab bấm Bắt đầu cùng lúc: đúng một lượt được tạo, các request còn lại 409 kèm id lượt đó. */
    @Test
    void concurrentStartsCreateExactlyOneAttempt() throws Exception {
        var u = student(5);
        long bank = bank(true);
        writingQuestion(bank);
        var pool = Executors.newFixedThreadPool(4);
        var go = new CountDownLatch(1);
        try {
            List<Future<Integer>> fs = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                fs.add(pool.submit((Callable<Integer>) () -> {
                    go.await(10, TimeUnit.SECONDS);
                    return start(u, bank).andReturn().getResponse().getStatus();
                }));
            }
            go.countDown();
            int created = 0;
            int conflict = 0;
            for (var f : fs) {
                int s = f.get(30, TimeUnit.SECONDS);
                created += s == 201 ? 1 : 0;
                conflict += s == 409 ? 1 : 0;
            }
            assertEquals(1, created);
            assertEquals(3, conflict);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, count("select count(*) from student_exercise_attempts where user_id = ?", u.id()));
    }

    // ---------- 2. Trải nghiệm học viên ----------

    /** Làm lại: nộp xong thì bắt đầu được lượt mới. */
    @Test
    void retryAfterSubmitCreatesNewAttempt() throws Exception {
        var u = student(5);
        long bank = bank(true);
        writingQuestion(bank);
        long first = startAttempt(u, bank);
        submitAttempt(u, first).andExpect(status().isOk());
        long second = startAttempt(u, bank);
        assertTrue(second != first);
    }

    /**
     * Lượt có giờ hết giờ: trước khi job tự nộp chạy, bắt đầu lượt mới trả 409 kèm attemptId (web vào lại, nộp sẽ dùng bản nháp);
     * sau khi máy chủ tự nộp thì bắt đầu được lượt mới.
     */
    @Test
    void timedOutAttemptAutoSubmittedThenNewAttemptAllowed() throws Exception {
        var u = student(5);
        long bank = bank(true);
        writingQuestion(bank);
        jdbc.update("update question_banks set time_limit_sec = 60 where id = ?", bank);
        long first = startAttempt(u, bank);
        jdbc.update("update student_exercise_attempts set started_at = ?, resumed_at = ? where id = ?", PAST, PAST, first);
        start(u, bank).andExpect(status().isConflict()).andExpect(jsonPath("$.error.details.attemptId").value(first));
        assertTrue(timeoutJob.tick() >= 1);
        assertTrue(jdbc.queryForObject("select submitted_at is not null from student_exercise_attempts where id = ?", Boolean.class, first));
        startAttempt(u, bank);
    }

    /**
     * GHI NHẬN: bộ không hẹn giờ bỏ dở thì không có cách bỏ lượt; người dùng chỉ vào lại được lượt cũ (409 có attemptId) và phải nộp
     * nó. Không kẹt hẳn, nhưng màn "Làm lại" phải dẫn về lượt đang mở.
     */
    @Test
    void abandonedUntimedAttemptMustBeResumedNotRestarted() throws Exception {
        var u = student(5);
        long bank = bank(true);
        writingQuestion(bank);
        long first = startAttempt(u, bank);
        start(u, bank).andExpect(status().isConflict()).andExpect(jsonPath("$.error.details.attemptId").value(first));
        getJson("/api/attempts/" + first, u).andExpect(status().isOk());
        submitAttempt(u, first).andExpect(status().isOk());
        startAttempt(u, bank);
    }

    // ---------- 3. Phiên luyện riêng tạo trước khi mở lượt ----------

    /** Viết: phiên luyện riêng tạo trước, nộp chấm sau khi mở lượt: mọi đường đọc đều giấu điểm cho tới khi lượt nộp. */
    @Test
    void practiceWritingCreatedBeforeAttemptIsSealedOnEveryReadPath() throws Exception {
        var u = student(10);
        long bank = bank(true);
        long q = writingQuestion(bank);
        long practice = openWriting(u, q, null);
        long attempt = startAttempt(u, bank);
        submitWriting(u, practice).andExpect(status().isAccepted());
        fake.override(WritingGrade.class, grade(77));
        assertEquals(1, runner.runDueNow());
        long job = count("select grade_job_id from writing_sessions where id = ?", practice);
        assertEquals("DONE", jdbc.queryForObject("select status from ai_jobs where id = ?", String.class, job));

        getJson("/api/writing/sessions/" + practice, u).andExpect(status().isOk()).andExpect(jsonPath("$.grade").doesNotExist());
        getJson("/api/me/writing", u).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].scorePercent").doesNotExist());
        getJson("/api/ai/jobs/" + job, u).andExpect(status().isOk()).andExpect(jsonPath("$.output").doesNotExist());
        String sse = getJson("/api/ai/jobs/" + job + "/stream", u).andReturn().getResponse().getContentAsString();
        assertFalse(sse.contains("fixed text"), sse);

        submitAttempt(u, attempt).andExpect(status().isOk());
        getJson("/api/writing/sessions/" + practice, u).andExpect(jsonPath("$.grade").exists());
    }

    /** Nói: bản luyện riêng tải lên trước, xác nhận sau khi mở lượt: kết quả giấu; xem đề luyện riêng thì 409. */
    @Test
    void practiceSpeakingCreatedBeforeAttemptIsSealedAndQuestionBlocked() throws Exception {
        var u = student(10);
        long bank = bank(true);
        long q = question(bank, "SPEAKING", 1, "Describe a trip.", CUE, "{}");
        long practice = uploadSpeaking(u, q, null);
        long attempt = startAttempt(u, bank);
        completeSpeaking(u, practice).andExpect(status().isAccepted());
        assertEquals(1, runner.runDueNow());
        assertEquals("GRADED", jdbc.queryForObject("select status from speaking_submissions where id = ?", String.class, practice));

        getJson("/api/speaking/submissions/" + practice, u).andExpect(status().isOk()).andExpect(jsonPath("$.result").doesNotExist());
        getJson("/api/me/speaking", u).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].result").doesNotExist());
        getJson("/api/speaking/questions/" + q, u).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("question_in_open_attempt"))
                .andExpect(jsonPath("$.error.details.attemptId").value(attempt));
        getJson("/api/speaking/questions/" + q + "?attemptId=" + attempt, u).andExpect(status().isOk());

        submitAttempt(u, attempt).andExpect(status().isOk());
        getJson("/api/speaking/submissions/" + practice, u).andExpect(jsonPath("$.result").exists());
    }

    // ---------- 4. Bài giao dạng bộ bài: không trừ lượt, không hoàn oan ----------

    /** Nói trong lượt bài giao: hết lượt AI vẫn xác nhận được, không trừ; UNCLEAR khi lượt còn mở thì không có dòng REFUND. */
    @Test
    void assignedSpeakingIsFreeAndUnclearDoesNotRefund() throws Exception {
        var a = assignment(0);
        long attempt = idOf(startAssigned(a).andExpect(status().isOk()), "id");
        long sub = uploadSpeaking(a.student(), a.speakingQ(), attempt);
        completeSpeaking(a.student(), sub).andExpect(status().isAccepted());
        assertEquals(0, count("select count(*) from ai_jobs where attempt_id = ? and charged = true", attempt));
        engine.setTranscript(FakeSpeakingEngineClient.spoken(0, 0.1, 0.9));
        assertEquals(1, runner.runDueNow());
        assertEquals("UNCLEAR", jdbc.queryForObject("select status from speaking_submissions where id = ?", String.class, sub));
        assertEquals(0, balance(a.student()));
        assertEquals(0, refunds(a.student()));
        submitAttempt(a.student(), attempt).andExpect(status().isOk());
        getJson("/api/attempts/" + attempt, a.student()).andExpect(jsonPath("$.results[?(@.questionId == " + a.speakingQ() + ")].aiRefunded").value(false));
    }

    /** Viết trong lượt bài giao lỗi hẳn khi lượt còn mở: FAILED, không có dòng REFUND, số dư không đổi. */
    @Test
    void assignedWritingFailureWhileOpenDoesNotRefund() throws Exception {
        var a = assignment(3);
        long attempt = idOf(startAssigned(a).andExpect(status().isOk()), "id");
        submitWriting(a.student(), openWriting(a.student(), a.writingQ(), attempt)).andExpect(status().isAccepted());
        assertEquals(3, balance(a.student()));
        fake.failNext(1, new AiCallException(false, "từ chối", null, null));
        assertEquals(1, runner.runDueNow());
        assertEquals("FAILED", jobStatus(attempt));
        assertEquals(3, balance(a.student()));
        assertEquals(0, refunds(a.student()));
    }

    // ---------- 5. Điểm tạm ở các API khác ----------

    /** Giáo viên xem hồ sơ năng lực học viên (D-139): lượt còn job AI cũng không được tính, như bản của học viên. */
    @Test
    void teacherLearningProfileExcludesAttemptStillGrading() throws Exception {
        var a = assignment(5);
        long attempt = idOf(startAssigned(a).andExpect(status().isOk()), "id");
        submitWriting(a.student(), openWriting(a.student(), a.writingQ(), attempt)).andExpect(status().isAccepted());
        submitAttempt(a.student(), attempt).andExpect(status().isOk());
        var url = "/api/classes/" + a.classId() + "/students/" + a.student().id() + "/learning-profile";
        var body = json.readTree(getJson(url, a.teacher()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(0, body.path("total").asInt(0), body.toString());
    }

    /**
     * LỖI: bài kiểm tra của khóa (bộ EXAM) có câu Viết đang chấm: GET /api/attempts/{id} giấu điểm nhưng
     * /api/courses/{id}/exam và /api/courses/{id}/tests vẫn trả lastResult.score (ExamService.lastResult không xét job đang chờ).
     */
    @Test
    void courseExamStatusHidesProvisionalScoreWhileGrading() throws Exception {
        var u = student(10);
        long c = insert("courses", Map.of("slug", "exam-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED"));
        long t = insert("topics", Map.of("level_id", lv, "title", "Topic", "is_free_trial", false, "status", "PUBLISHED"));
        long l1 = insert("lessons", Map.of("topic_id", t, "type", "CONNECTIVE_VIDEO", "title", "B1", "status", "PUBLISHED"));
        long bank = insert("question_banks", Map.of("topic_id", t, "title", "Thi", "is_trial", false, "status", "PUBLISHED", "mode", "EXAM"));
        long q = writingQuestion(bank);
        jdbc.update("update courses set exam_bank_id = ?, exam_unlock_percent = 50 where id = ?", bank, c);
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', TIMESTAMP '2020-01-01 00:00:00', TIMESTAMP '2099-01-01 00:00:00')",
                u.id(), c);
        jdbc.update("insert into lesson_progress (user_id, lesson_id, watched_sec, max_position_sec, completed_at) values (?, ?, 10, 10, CURRENT_TIMESTAMP)", u.id(), l1);
        long attempt = startAttempt(u, bank);
        submitWriting(u, openWriting(u, q, attempt)).andExpect(status().isAccepted());
        submitAttempt(u, attempt).andExpect(status().isOk()).andExpect(jsonPath("$.score").doesNotExist());
        getJson("/api/courses/" + c + "/exam", u).andExpect(status().isOk()).andExpect(jsonPath("$.lastResult.score").doesNotExist());
        getJson("/api/courses/" + c + "/tests", u).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.kind == 'FINAL')].lastResult.score").isEmpty());
    }

    // ---------- 6. Thứ tự khóa trên các đường còn lại ----------

    /**
     * Job Viết lỗi hẳn trong lúc nộp lượt đang giữ khóa dòng lượt: không deadlock; kết thúc ở trạng thái nhất quán (hoàn đúng
     * một lần hoặc chưa hoàn vì được chấm lại; câu không bao giờ mất điểm lẫn lý do).
     */
    @Test
    void jobFailureDuringAttemptSubmitDoesNotDeadlock() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = writingQuestion(bank);
        long attempt = startAttempt(u, bank);
        submitWriting(u, openWriting(u, q, attempt)).andExpect(status().isAccepted());
        fake.failNext(1, new AiCallException(false, "từ chối", null, null));
        var aLocked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> a = pool.submit(() -> tx.executeWithoutResult(t -> {
                submissions.submit(u.id(), attempt, new SubmitRequest(List.of()));
                aLocked.countDown();
                try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }));
            assertTrue(aLocked.await(10, TimeUnit.SECONDS));
            Future<Integer> b = pool.submit(() -> runner.runDueNow());
            Thread.sleep(500);
            assertFalse(b.isDone(), "job lỗi phải chờ khóa dòng lượt");
            release.countDown();
            a.get(30, TimeUnit.SECONDS);
            assertEquals(1, b.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        String st = jobStatus(attempt);
        if ("FAILED".equals(st)) {
            assertEquals(1, refunds(u));
            assertEquals("0", jdbc.queryForObject("select cast(credit as varchar) from student_answers where attempt_id = ?", String.class, attempt)
                    .replaceAll("\\.0+$", ""));
        } else {
            assertEquals("PENDING", st);
            assertEquals(0, refunds(u));
        }
    }

    /**
     * LỖI (Thấp): job lỗi hẳn đúng lúc lượt đang được nộp (chưa commit). AiAutoRetry.applies đọc "lượt còn mở" TRƯỚC khi khóa dòng
     * lượt nên kết luận không chấm lại; khi có khóa thì lượt đã nộp. D-147 mục 5 yêu cầu chấm lại một lần cho lỗi sau khi nộp.
     */
    @Test
    void jobFailingWhileAttemptIsBeingSubmittedStillGetsAutoRetry() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = writingQuestion(bank);
        long attempt = startAttempt(u, bank);
        submitWriting(u, openWriting(u, q, attempt)).andExpect(status().isAccepted());
        fake.failNext(1, new AiCallException(false, "từ chối", null, null));
        var aLocked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> a = pool.submit(() -> tx.executeWithoutResult(t -> {
                submissions.submit(u.id(), attempt, new SubmitRequest(List.of()));
                aLocked.countDown();
                try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }));
            assertTrue(aLocked.await(10, TimeUnit.SECONDS));
            Future<Integer> b = pool.submit(() -> runner.runDueNow());
            Thread.sleep(500);
            release.countDown();
            a.get(30, TimeUnit.SECONDS);
            b.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals("PENDING", jobStatus(attempt), "lỗi xảy ra khi lượt đã nộp phải được chấm lại một lần");
        assertEquals(0, refunds(u));
    }

    /** Nói UNCLEAR (hoàn lượt) song song với xác nhận câu Nói khác của cùng lượt (trừ lượt): không deadlock, hoàn đúng một lần. */
    @Test
    void unclearRefundAndConcurrentSpeakingChargeDoNotDeadlock() throws Exception {
        var u = student(10);
        long bank = bank(true);
        long q1 = question(bank, "SPEAKING", 1, "Describe a trip.", CUE, "{}");
        long q2 = question(bank, "SPEAKING", 2, "Describe a friend.", CUE, "{}");
        long attempt = startAttempt(u, bank);
        long s1 = uploadSpeaking(u, q1, attempt);
        completeSpeaking(u, s1).andExpect(status().isAccepted());
        engine.setTranscript(FakeSpeakingEngineClient.spoken(0, 0.1, 0.9));
        int before = balance(u);
        var aLocked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> a = pool.submit(() -> tx.executeWithoutResult(t -> {
                links.requireOpenLocked(attempt);
                aLocked.countDown();
                try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                aiJobs.enqueueCharged(u.id(), AiJobKind.SPEAKING_GRADE, mapper.createObjectNode().put("submissionId", -1), null, attempt,
                        AiTask.SPEAKING_PART, null);
            }));
            assertTrue(aLocked.await(10, TimeUnit.SECONDS));
            Future<Integer> b = pool.submit(() -> runner.runDueNow());
            Thread.sleep(500);
            assertFalse(b.isDone(), "UNCLEAR phải chờ khóa dòng lượt");
            release.countDown();
            a.get(30, TimeUnit.SECONDS);
            assertEquals(1, b.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, refunds(u));
        assertEquals("UNCLEAR", jdbc.queryForObject("select status from speaking_submissions where id = ?", String.class, s1));
        assertTrue(balance(u) <= before, "đã trừ cho câu thứ hai, hoàn cho câu thứ nhất");
        assertTrue(q2 > 0);
    }

    /** Bắt đầu lượt (khóa dòng users) song song với job lỗi hoàn lượt (khóa lượt rồi quota): không deadlock. */
    @Test
    void startHoldingUserLockAndRefundingJobDoNotDeadlock() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = writingQuestion(bank);
        long other = bank(true);
        writingQuestion(other);
        long attempt = startAttempt(u, bank);
        submitWriting(u, openWriting(u, q, attempt)).andExpect(status().isAccepted());
        fake.failNext(1, new AiCallException(false, "từ chối", null, null));
        var aLocked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Future<Long> a = pool.submit(() -> tx.execute(t -> {
                attemptRepo.lockUser(u.id());
                aLocked.countDown();
                try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return attemptService.start(other, u.id()).id();
            }));
            assertTrue(aLocked.await(10, TimeUnit.SECONDS));
            Future<Integer> b = pool.submit(() -> runner.runDueNow());
            Thread.sleep(500);
            release.countDown();
            a.get(30, TimeUnit.SECONDS);
            assertEquals(1, b.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals("FAILED", jobStatus(attempt));
        assertEquals(1, refunds(u));
    }

    /**
     * LỖI (Thấp): AiJobClaimer.claim giữ khóa dòng job (FOR UPDATE SKIP LOCKED) rồi gọi finisher.fail cho job hết lease, fail khóa
     * dòng lượt: thứ tự job -> lượt. Worker còn sống của cùng job (gọi AI quá lease 90 s) lỗi hẳn đi đường lượt -> job
     * (lockAttempt rồi markFailed). Hai thứ tự ngược nhau trên cùng job: deadlock, một bên bị hủy.
     */
    @Test
    void claimerFailingStaleJobAndWorkerFailingSameJobDoNotDeadlock() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long q = writingQuestion(bank);
        long attempt = startAttempt(u, bank);
        submitWriting(u, openWriting(u, q, attempt)).andExpect(status().isAccepted());
        jdbc.update("update ai_jobs set status = 'RUNNING', attempts = 3, locked_until = ? where attempt_id = ?", PAST, attempt);
        long jobId = count("select id from ai_jobs where attempt_id = ?", attempt);
        var worker = jobRepo.findById(jobId).orElseThrow();
        var aLocked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        Throwable aErr = null;
        Throwable bErr = null;
        try {
            Future<?> a = pool.submit(() -> tx.executeWithoutResult(t -> { // worker: lượt trước, rồi markFailed (dòng job)
                links.lockAttempt(attempt);
                aLocked.countDown();
                try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                finisher.fail(worker, false, "từ chối");
            }));
            assertTrue(aLocked.await(10, TimeUnit.SECONDS));
            Future<?> b = pool.submit(() -> claimer.claim(10)); // khóa dòng job rồi chờ dòng lượt
            Thread.sleep(500);
            release.countDown();
            try { a.get(30, TimeUnit.SECONDS); } catch (Exception e) { aErr = e; }
            try { b.get(30, TimeUnit.SECONDS); } catch (Exception e) { bErr = e; }
        } finally {
            pool.shutdownNow();
        }
        assertNull(aErr, "worker: " + aErr);
        assertNull(bErr, "claimer: " + bErr);
        assertEquals(1, refunds(u));
    }
}
