package com.pps.smartenglish.classroom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.ai.AiTestSupport;
import com.pps.smartenglish.ai.speaking.FakeSpeakingEngineClient;
import com.pps.smartenglish.classroom.repository.AssignmentRepository;
import com.pps.smartenglish.support.FakeStorageConfig.FakeStorage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tester đợt D-141 (#61): giáo viên đọc bài nộp Viết/Nói (FR-ASG-03, NFR-SEC-05, D-43) và khóa nộp đồng thời bài Viết được giao.
 * Bổ sung cho AssignmentAiApiTest: id trùng số giữa hai bảng, nhân sự hết hạn/bị gỡ, chưa đăng nhập, khóa thực sự giữ và không chặn bài khác.
 */
class AssignmentSubmissionReadTest extends AiTestSupport {

    private static final String ESSAY = "I agree with online learning because it is flexible. However, students need discipline.";

    @Autowired FakeStorage storage;
    @Autowired FakeSpeakingEngineClient engine;
    @Autowired AssignmentRepository assignments;
    @Autowired PlatformTransactionManager txManager;

    private final ExecutorService pool = Executors.newFixedThreadPool(4);

    private record Fx(long course, long classId, long qA, long sA, Account teacher) {}

    @BeforeEach
    void reset() {
        engine.reset();
        storage.objects.clear();
    }

    @AfterEach
    void stop() {
        pool.shutdownNow();
    }

    private Account withRole(String role) throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = ?", u.id(), role);
        return login(u.email(), PASSWORD);
    }

    private ResultActions call(HttpMethod method, String url, String token, Object body) throws Exception {
        var req = MockMvcRequestBuilders.request(method, url);
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return send(req, token);
    }

    private Fx fixture() throws Exception {
        long c = insert("courses", Map.of("slug", "asgread-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED",
                "delivery_mode", "CLASS_BASED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED", "sort_order", 0));
        long ta = insert("topics", Map.of("level_id", lv, "title", "Unit A", "is_free_trial", false, "status", "PUBLISHED", "sort_order", 0));
        long bank = insert("question_banks", Map.of("topic_id", ta, "title", "Bộ " + UUID.randomUUID().toString().substring(0, 4),
                "is_trial", false, "status", "PUBLISHED", "mode", "PRACTICE"));
        long qA = writingQuestion(bank);
        long sA = question(bank, "SPEAKING", 2, "Describe a trip.", "{\"part\":1,\"prompt\":\"Describe a trip.\"}", "{}");
        long cls = insert("classes", Map.of("course_id", c, "name", "Lớp " + UUID.randomUUID().toString().substring(0, 4),
                "starts_on", today().minusDays(10), "ends_on", today().plusDays(60), "mode", "ONLINE", "capacity", 20));
        return new Fx(c, cls, qA, sA, staffOf(cls, "TEACHER", today().minusDays(10), null));
    }

    /** Ngày theo múi giờ của phân hệ lớp (ClassService.VN), không theo múi giờ máy chạy test: tránh lệch ở biên ngày. */
    private static LocalDate today() {
        return LocalDate.now(com.pps.smartenglish.classroom.service.ClassService.VN);
    }

    private Account staffOf(long classId, String role, LocalDate from, LocalDate to) throws Exception {
        var t = withRole("TEACHER");
        jdbc.update("insert into class_staff (class_id, user_id, role, assigned_from, assigned_to) values (?, ?, ?, ?, ?)",
                classId, t.id(), role, from, to);
        return t;
    }

    private Account member(Fx f) throws Exception {
        var s = student(3);
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', TIMESTAMP '2020-01-01 00:00:00', TIMESTAMP '2099-01-01 00:00:00')",
                s.id(), f.course());
        jdbc.update("insert into class_members (class_id, course_id, user_id, status, live_key, joined_at) values (?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP)",
                f.classId(), f.course(), s.id(), f.course() + ":" + s.id());
        return s;
    }

    private String base(Fx f) {
        return "/api/classes/" + f.classId() + "/assignments";
    }

    private long assignOk(Fx f, long questionId, int dueHours) throws Exception {
        return idOf(call(HttpMethod.POST, base(f), f.teacher().accessToken(), Map.of("questionId", questionId,
                "dueAt", Instant.now().plus(dueHours, ChronoUnit.HOURS).toString())).andExpect(status().isCreated()), "id");
    }

    private ResultActions startWriting(Account s, long q, Long asg) throws Exception {
        guard.resetRateLimit(s.id());
        var body = new HashMap<String, Object>();
        body.put("questionId", q);
        body.put("assignmentId", asg);
        return postJson("/api/writing/sessions", s, body);
    }

    private ResultActions submitWriting(Account s, long session) throws Exception {
        guard.resetRateLimit(s.id());
        return postJson("/api/writing/sessions/" + session + "/submit", s, Map.of("finalText", ESSAY), UUID.randomUUID().toString());
    }

    private long writeAssigned(Account s, long q, long asg) throws Exception {
        long session = idOf(startWriting(s, q, asg).andExpect(status().isCreated()), "sessionId");
        submitWriting(s, session).andExpect(status().isAccepted());
        return session;
    }

    private static byte[] wav(double seconds) {
        int rate = 32000;
        int data = (int) (seconds * rate);
        var b = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + data).put("WAVE".getBytes()).put("fmt ".getBytes()).putInt(16).putShort((short) 1)
                .putShort((short) 1).putInt(rate / 2).putInt(rate).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes()).putInt(data);
        return b.array();
    }

    private long speakAssigned(Account s, long q, long asg) throws Exception {
        guard.resetRateLimit(s.id());
        var body = new HashMap<String, Object>();
        body.put("questionId", q);
        body.put("assignmentId", asg);
        long id = idOf(postJson("/api/speaking/submissions", s, body).andExpect(status().isCreated()), "submissionId");
        storage.put(jdbc.queryForObject("select object_key from speaking_submissions where id = ?", String.class, id), wav(20));
        guard.resetRateLimit(s.id());
        postJson("/api/speaking/submissions/" + id + "/complete", s, Map.of()).andExpect(status().isAccepted());
        return id;
    }

    private ResultActions read(Fx f, String token, long asg, long id) throws Exception {
        return call(HttpMethod.GET, base(f) + "/" + asg + "/submissions/" + id, token, null);
    }

    /** Ép id tiếp theo của writing_sessions và speaking_submissions bằng nhau để thử nhầm bảng khi id trùng số. */
    private long alignNextIds() {
        long n = Math.max(jdbc.queryForObject("select coalesce(max(id), 0) from writing_sessions", Long.class),
                jdbc.queryForObject("select coalesce(max(id), 0) from speaking_submissions", Long.class)) + 1000;
        jdbc.execute("alter table writing_sessions alter column id restart with " + n);
        jdbc.execute("alter table speaking_submissions alter column id restart with " + n);
        return n;
    }

    @Test
    void sameNumericIdInBothTablesResolvesByAssignmentType() throws Exception {
        var f = fixture();
        var writer = member(f);
        var speaker = member(f);
        long asgW = assignOk(f, f.qA(), 48);
        long asgS = assignOk(f, f.sA(), 48);
        long n = alignNextIds();
        long session = writeAssigned(writer, f.qA(), asgW);
        long speak = speakAssigned(speaker, f.sA(), asgS);
        assertEquals(n, session);
        assertEquals(n, speak);
        runner.runDueNow();
        runner.runDueNow();

        // cùng số id: bài Viết luôn ra phiên viết của người viết, bài Nói ra lượt nói của người nói, không lẫn chéo
        read(f, f.teacher().accessToken(), asgW, n).andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(writer.id()))
                .andExpect(jsonPath("$.writing.finalText").value(ESSAY)).andExpect(jsonPath("$.speaking").doesNotExist())
                .andExpect(jsonPath("$.audioDeleted").doesNotExist());
        read(f, f.teacher().accessToken(), asgS, n).andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(speaker.id()))
                .andExpect(jsonPath("$.speaking.audioUrl").isNotEmpty()).andExpect(jsonPath("$.writing").doesNotExist());
    }

    @Test
    void expiredRevokedOrNotYetStartedStaffAndAnonymousCannotRead() throws Exception {
        var f = fixture();
        var s = member(f);
        long asgS = assignOk(f, f.sA(), 48);
        long speak = speakAssigned(s, f.sA(), asgS);
        runner.runDueNow();

        var assistant = staffOf(f.classId(), "ASSISTANT", today().minusDays(5), null);
        read(f, assistant.accessToken(), asgS, speak).andExpect(status().isOk()).andExpect(jsonPath("$.speaking.audioUrl").isNotEmpty());

        var expired = staffOf(f.classId(), "ASSISTANT", today().minusDays(20), today().minusDays(1));
        read(f, expired.accessToken(), asgS, speak).andExpect(status().isNotFound()).andExpect(jsonPath("$.speaking").doesNotExist());
        var future = staffOf(f.classId(), "ASSISTANT", today().plusDays(1), null);
        read(f, future.accessToken(), asgS, speak).andExpect(status().isNotFound());
        var revoked = staffOf(f.classId(), "TEACHER", today().minusDays(5), null);
        jdbc.update("update class_staff set revoked_at = CURRENT_TIMESTAMP where user_id = ?", revoked.id());
        read(f, revoked.accessToken(), asgS, speak).andExpect(status().isNotFound());
        // học viên cùng lớp đọc bài của bạn
        read(f, member(f).accessToken(), asgS, speak).andExpect(status().isNotFound()).andExpect(jsonPath("$.speaking").doesNotExist());
        // chưa đăng nhập
        mvc.perform(MockMvcRequestBuilders.get(base(f) + "/" + asgS + "/submissions/" + speak)).andExpect(status().isUnauthorized());
    }

    @Test
    void parallelSubmitsOfTwoOpenSessionsGradeOnce() throws Exception {
        var f = fixture();
        var s = member(f);
        long asg = assignOk(f, f.qA(), 48);
        long first = idOf(startWriting(s, f.qA(), asg).andExpect(status().isCreated()), "sessionId");
        jdbc.update("insert into writing_sessions (user_id, question_id, step, turns, status, assignment_id) "
                + "select user_id, question_id, step, turns, 'ACTIVE', assignment_id from writing_sessions where id = ?", first);
        long second = jdbc.queryForObject("select max(id) from writing_sessions where assignment_id = ?", Long.class, asg);
        guard.resetRateLimit(s.id());

        var gate = new CountDownLatch(1);
        List<Future<Integer>> calls = new ArrayList<>();
        for (long id : List.of(first, second)) {
            calls.add(pool.submit(() -> {
                gate.await();
                return postJson("/api/writing/sessions/" + id + "/submit", s, Map.of("finalText", ESSAY), UUID.randomUUID().toString())
                        .andReturn().getResponse().getStatus();
            }));
        }
        gate.countDown();
        List<Integer> codes = new ArrayList<>();
        for (var c : calls) {
            codes.add(c.get(30, TimeUnit.SECONDS));
        }
        codes.sort(null);
        assertEquals(List.of(202, 409), codes, "một bài giao chỉ được một lần nộp được chấm");
        assertEquals(1, count("select count(*) from ai_jobs where kind = 'WRITING_GRADE' and user_id = ?", s.id()));
        assertEquals(1, count("select count(*) from writing_sessions where assignment_id = ? and status = 'SUBMITTED'", asg));
    }

    @Test
    void parallelSubmitsOfDifferentStudentsOnOneAssignmentAllSucceed() throws Exception {
        var f = fixture();
        long asg = assignOk(f, f.qA(), 48);
        List<Account> students = new ArrayList<>();
        List<Long> sessions = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            var s = member(f);
            students.add(s);
            sessions.add(idOf(startWriting(s, f.qA(), asg).andExpect(status().isCreated()), "sessionId"));
            guard.resetRateLimit(s.id());
        }
        var gate = new CountDownLatch(1);
        List<Future<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            var s = students.get(i);
            long id = sessions.get(i);
            calls.add(pool.submit(() -> {
                gate.await();
                return postJson("/api/writing/sessions/" + id + "/submit", s, Map.of("finalText", ESSAY), UUID.randomUUID().toString())
                        .andReturn().getResponse().getStatus();
            }));
        }
        gate.countDown();
        for (var c : calls) {
            assertEquals(202, c.get(30, TimeUnit.SECONDS), "khóa theo bài giao chỉ xếp hàng, không làm hỏng nộp của người khác");
        }
        assertEquals(4, count("select count(*) from writing_sessions where assignment_id = ? and status = 'SUBMITTED'", asg));
    }

    @Test
    void assignmentLockIsHeldUntilCommitAndDoesNotBlockOtherAssignments() throws Exception {
        var f = fixture();
        var s = member(f);
        long locked = assignOk(f, f.qA(), 48);
        long free = assignOk(f, f.qA(), 72);
        guard.resetRateLimit(s.id());

        var held = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        Future<?> holder = pool.submit(() -> new TransactionTemplate(txManager).executeWithoutResult(t -> {
            assignments.lockById(locked);
            held.countDown();
            try {
                release.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        try {
            assertEquals(true, held.await(10, TimeUnit.SECONDS));
            // bài giao khác: không phải chờ
            Future<Integer> other = pool.submit(() -> startWriting(s, f.qA(), free).andReturn().getResponse().getStatus());
            assertEquals(201, other.get(5, TimeUnit.SECONDS));
            // cùng bài giao: chờ đến khi bên giữ khóa commit
            Future<Integer> waiting = pool.submit(() -> {
                var body = new HashMap<String, Object>();
                body.put("questionId", f.qA());
                body.put("assignmentId", locked);
                return postJson("/api/writing/sessions", s, body).andReturn().getResponse().getStatus();
            });
            assertThrows(TimeoutException.class, () -> waiting.get(300, TimeUnit.MILLISECONDS), "phải chờ khóa của bài giao");
            assertFalse(waiting.isDone());
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
            assertEquals(201, waiting.get(10, TimeUnit.SECONDS));
        } finally {
            release.countDown();
        }
        assertEquals(1, count("select count(*) from writing_sessions where assignment_id = ? and user_id = ?", locked, s.id()));
    }

    @Test
    void studentOwnReadPathsAreUnchanged() throws Exception {
        var f = fixture();
        var s = member(f);
        var peer = member(f);
        long asgW = assignOk(f, f.qA(), 48);
        long asgS = assignOk(f, f.sA(), 48);
        long session = writeAssigned(s, f.qA(), asgW);
        long speak = speakAssigned(s, f.sA(), asgS);
        runner.runDueNow();
        runner.runDueNow();
        getJson("/api/writing/sessions/" + session, s).andExpect(status().isOk()).andExpect(jsonPath("$.finalText").value(ESSAY));
        getJson("/api/speaking/submissions/" + speak, s).andExpect(status().isOk()).andExpect(jsonPath("$.audioUrl").isNotEmpty());
        getJson("/api/writing/sessions/" + session, peer).andExpect(status().isNotFound());
        getJson("/api/speaking/submissions/" + speak, peer).andExpect(status().isNotFound());
    }
}
