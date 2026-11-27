package com.pps.smartenglish.exercise;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.support.ApiTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** FR-EX-04, D-150: kiểm tra từng câu với thử lại kèm gợi ý (D-163: mặc định 2 lần thử lại, tức 3 lần kiểm tra). */
class AttemptCheckApiTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";

    private record Bank(long id, long mcq, long right, long wrong, long writing) {}

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private long question(long bank, String type, int sort) {
        var kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("insert into questions (bank_id, type, prompt, payload, answer_key, sort_order)"
                    + " values (?, ?, 'Đề', '{}' format json, '{}' format json, ?)", new String[] {"id"});
            ps.setLong(1, bank);
            ps.setString(2, type);
            ps.setInt(3, sort);
            return ps;
        }, kh);
        return kh.getKey().longValue();
    }

    /** Bộ gồm một câu trắc nghiệm (tự chấm) và một câu Viết (chấm AI). */
    private Bank bank() {
        long c = insert("courses", Map.of("slug", "ck-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED"));
        long t = insert("topics", Map.of("level_id", lv, "title", "Topic", "is_free_trial", true, "status", "PUBLISHED"));
        long bank = insert("question_banks", Map.of("topic_id", t, "title", "Bộ", "is_trial", false, "status", "PUBLISHED", "mode", "PRACTICE"));
        long q = question(bank, "CONNECTIVE_MCQ", 1);
        long right = insert("question_choices", Map.of("question_id", q, "label", "B", "content", "Đúng", "is_correct", true));
        long wrong = insert("question_choices", Map.of("question_id", q, "label", "A", "content", "Sai", "is_correct", false));
        jdbc.update("update questions set explanation = 'Vì B đúng' where id = ?", q);
        return new Bank(bank, q, right, wrong, question(bank, "WRITING", 2));
    }

    private ResultActions post(String url, String token, Object body) throws Exception {
        return send(MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)), token);
    }

    private long start(Bank b, String token) throws Exception {
        String body = post("/api/banks/" + b.id() + "/attempts", token, Map.of()).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private ResultActions check(long attempt, long question, String token, long choiceId) throws Exception {
        return post("/api/attempts/" + attempt + "/questions/" + question + "/check", token, Map.of("answer", Map.of("choiceId", choiceId)));
    }

    @Test
    void wrongThenRightIsRecordedAndDraftSaved() throws Exception {
        var b = bank();
        var u = register(newEmail(), PASSWORD).accessToken();
        long id = start(b, u);
        check(id, b.mcq(), u, b.wrong()).andExpect(status().isOk()).andExpect(jsonPath("$.correct").value(false))
                .andExpect(jsonPath("$.checksLeft").value(2)).andExpect(jsonPath("$.wrongCount").value(1))
                .andExpect(jsonPath("$.correctAnswer").doesNotExist()).andExpect(jsonPath("$.explanation").doesNotExist());
        check(id, b.mcq(), u, b.right()).andExpect(status().isOk()).andExpect(jsonPath("$.correct").value(true))
                .andExpect(jsonPath("$.wrongCount").value(1)).andExpect(jsonPath("$.correctAnswer").doesNotExist())
                .andExpect(jsonPath("$.explanation").doesNotExist());

        send(get("/api/attempts/" + id + "/progress"), u).andExpect(jsonPath("$.retry.maxRetries").value(2))
                .andExpect(jsonPath("$.retry.hintAfterWrong").value(2))
                .andExpect(jsonPath("$.retry.checks[0].correctAnswer").doesNotExist())
                .andExpect(jsonPath("$.retry.checks[0].questionId").value(b.mcq()))
                .andExpect(jsonPath("$.retry.checks[0].correct").value(true))
                .andExpect(jsonPath("$.answers[0].answer.choiceId").value(b.right()));
        check(id, b.mcq(), u, b.right()).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("retry_exhausted"));
    }

    @Test
    void exhaustedQuestionLocksAndFinalAnswerDecidesScore() throws Exception {
        var b = bank();
        var u = register(newEmail(), PASSWORD).accessToken();
        long id = start(b, u);
        check(id, b.mcq(), u, b.wrong()).andExpect(jsonPath("$.checksLeft").value(2));
        check(id, b.mcq(), u, b.wrong()).andExpect(jsonPath("$.checksLeft").value(1)).andExpect(jsonPath("$.wrongCount").value(2))
                .andExpect(jsonPath("$.correctAnswer").doesNotExist());
        // D-163: hết lượt mà vẫn sai thì trả đáp án đúng và giải thích, cả ở check lẫn progress (tải lại trang)
        check(id, b.mcq(), u, b.wrong()).andExpect(status().isOk()).andExpect(jsonPath("$.checksLeft").value(0))
                .andExpect(jsonPath("$.wrongCount").value(3)).andExpect(jsonPath("$.correctAnswer.choiceId").value(b.right()))
                .andExpect(jsonPath("$.explanation").value("Vì B đúng"));
        send(get("/api/attempts/" + id + "/progress"), u).andExpect(jsonPath("$.retry.checks[0].correctAnswer.choiceId").value(b.right()))
                .andExpect(jsonPath("$.retry.checks[0].explanation").value("Vì B đúng"));
        check(id, b.mcq(), u, b.right()).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("retry_exhausted"));
        assertWrongCount(id, b.mcq(), 3);

        // Câu đã khóa: nháp và nộp bài bỏ đáp án khác, điểm theo câu trả lời của lần kiểm tra cuối (sai)
        var other = List.of(Map.of("questionId", b.mcq(), "answer", Map.of("choiceId", b.right())));
        send(MockMvcRequestBuilders.put("/api/attempts/" + id + "/draft").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("answers", other))), u).andExpect(status().isOk())
                .andExpect(jsonPath("$.answers[0].answer.choiceId").value(b.wrong()));
        post("/api/attempts/" + id + "/submit", u, Map.of("answers", other))
                .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(0));
        check(id, b.mcq(), u, b.right()).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("attempt_already_submitted"));
    }

    private void assertWrongCount(long attempt, long question, int expected) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, jdbc.queryForObject(
                "select wrong_count from attempt_question_checks where attempt_id = ? and question_id = ?", Integer.class, attempt, question));
    }

    @Test
    void solvedQuestionKeepsTheCorrectAnswerWhenSubmitSendsAnother() throws Exception {
        var b = bank();
        var u = register(newEmail(), PASSWORD).accessToken();
        long id = start(b, u);
        check(id, b.mcq(), u, b.right()).andExpect(jsonPath("$.correct").value(true));
        post("/api/attempts/" + id + "/submit", u, Map.of("answers", List.of(Map.of("questionId", b.mcq(), "answer", Map.of("choiceId", b.wrong())))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(1));
    }

    /** Hai kiểm tra sai song song khi chỉ còn một lần: đúng một lần được tính, lần kia retry_exhausted. */
    @Test
    void parallelChecksNeverExceedTheLimit() throws Exception {
        var b = bank();
        var u = register(newEmail(), PASSWORD).accessToken();
        long id = start(b, u);
        check(id, b.mcq(), u, b.wrong()).andExpect(jsonPath("$.checksLeft").value(2));
        check(id, b.mcq(), u, b.wrong()).andExpect(jsonPath("$.checksLeft").value(1));
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var go = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<Integer>> results = new java.util.ArrayList<>();
        for (int i = 0; i < 2; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return check(id, b.mcq(), u, b.wrong()).andReturn().getResponse().getStatus();
            }));
        }
        go.countDown();
        var codes = new java.util.ArrayList<Integer>();
        for (var f : results) {
            codes.add(f.get(60, java.util.concurrent.TimeUnit.SECONDS));
        }
        pool.shutdown();
        org.junit.jupiter.api.Assertions.assertEquals(List.of(200, 409), codes.stream().sorted().toList());
        assertWrongCount(id, b.mcq(), 3);
    }

    /** Bài giáo viên giao của bộ PRACTICE đi cùng đường lượt, có thử lại. */
    @Test
    void assignedPracticeAttemptHasRetry() throws Exception {
        var b = bank();
        long course = jdbc.queryForObject("select c.id from question_banks qb join topics t on t.id = qb.topic_id "
                + "join course_levels l on l.id = t.level_id join courses c on c.id = l.course_id where qb.id = ?", Long.class, b.id());
        jdbc.update("update courses set delivery_mode = 'CLASS_BASED', sequential = false where id = ?", course);
        long cls = insert("classes", Map.of("course_id", course, "name", "Lớp " + UUID.randomUUID().toString().substring(0, 4),
                "starts_on", java.time.LocalDate.now().minusDays(10), "ends_on", java.time.LocalDate.now().plusDays(60), "mode", "ONLINE", "capacity", 20));
        var teacher = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = 'TEACHER'", teacher.id());
        var teacherToken = login(teacher.email(), PASSWORD).accessToken();
        jdbc.update("insert into class_staff (class_id, user_id, role, assigned_from) values (?, ?, 'TEACHER', ?)", cls, teacher.id(), java.time.LocalDate.now().minusDays(10));
        var s = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', "
                + "TIMESTAMP '2020-01-01 00:00:00', TIMESTAMP '2099-01-01 00:00:00')", s.id(), course);
        jdbc.update("insert into class_members (class_id, course_id, user_id, status, live_key, joined_at) values (?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP)",
                cls, course, s.id(), course + ":" + s.id());
        String made = post("/api/classes/" + cls + "/assignments", teacherToken,
                Map.of("bankId", b.id(), "dueAt", java.time.Instant.now().plus(48, java.time.temporal.ChronoUnit.HOURS).toString()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long asg = json.readTree(made).get("id").asLong();
        String started = post("/api/classes/" + cls + "/assignments/" + asg + "/start", s.accessToken(), Map.of())
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(started).get("id").asLong();
        check(id, b.mcq(), s.accessToken(), b.wrong()).andExpect(status().isOk()).andExpect(jsonPath("$.checksLeft").value(2));
        send(get("/api/attempts/" + id + "/progress"), s.accessToken()).andExpect(jsonPath("$.retry.maxRetries").value(2));
    }

    @Test
    void examBankAndForcedAttemptAreBlocked() throws Exception {
        var b = bank();
        var u = register(newEmail(), PASSWORD).accessToken();
        long id = start(b, u);
        jdbc.update("update student_exercise_attempts set result_mode = 'COUNT_ONLY' where id = ?", id);
        check(id, b.mcq(), u, b.right()).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("retry_not_available"));
        send(get("/api/attempts/" + id + "/progress"), u).andExpect(jsonPath("$.retry").doesNotExist());

        jdbc.update("update student_exercise_attempts set result_mode = 'FULL' where id = ?", id);
        jdbc.update("update student_exercise_attempts set mode = 'EXAM' where id = ?", id); // D-151: chế độ của lượt, không của bộ
        check(id, b.mcq(), u, b.right()).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("retry_not_available"));
        send(get("/api/attempts/" + id + "/progress"), u).andExpect(jsonPath("$.retry").doesNotExist());
    }

    @Test
    void aiQuestionAndForeignQuestionAreRejected() throws Exception {
        var b = bank();
        var other = bank();
        var u = register(newEmail(), PASSWORD).accessToken();
        long id = start(b, u);
        check(id, b.writing(), u, 1).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("question_not_checkable"));
        check(id, other.mcq(), u, other.right()).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("question_not_found"));
        post("/api/attempts/" + id + "/questions/" + b.mcq() + "/check", u, Map.of()).andExpect(status().isBadRequest());
    }

    @Test
    void onlyTheOwnerCanCheck() throws Exception {
        var b = bank();
        var owner = register(newEmail(), PASSWORD).accessToken();
        var stranger = register(newEmail(), PASSWORD).accessToken();
        long id = start(b, owner);
        check(id, b.mcq(), stranger, b.right()).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("attempt_not_found"));
        check(999_999, b.mcq(), owner, b.right()).andExpect(status().isNotFound());
        assertWrongCount0(id);
    }

    private void assertWrongCount0(long attempt) {
        org.junit.jupiter.api.Assertions.assertEquals(0, jdbc.queryForObject(
                "select count(*) from attempt_question_checks where attempt_id = ?", Integer.class, attempt));
    }
}
