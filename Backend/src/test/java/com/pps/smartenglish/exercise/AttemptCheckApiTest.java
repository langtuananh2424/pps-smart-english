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

/** FR-EX-04, D-150: kiểm tra từng câu với thử lại kèm gợi ý (mặc định 1 lần thử lại, tức 2 lần kiểm tra). */
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
                .andExpect(jsonPath("$.checksLeft").value(1)).andExpect(jsonPath("$.correctAnswer").doesNotExist());
        check(id, b.mcq(), u, b.right()).andExpect(status().isOk()).andExpect(jsonPath("$.correct").value(true));

        send(get("/api/attempts/" + id + "/progress"), u).andExpect(jsonPath("$.retry.maxRetries").value(1))
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
        check(id, b.mcq(), u, b.wrong()).andExpect(jsonPath("$.checksLeft").value(1));
        check(id, b.mcq(), u, b.wrong()).andExpect(status().isOk()).andExpect(jsonPath("$.checksLeft").value(0));
        check(id, b.mcq(), u, b.right()).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("retry_exhausted"));
        assertWrongCount(id, b.mcq(), 2);

        // Điểm theo câu trả lời cuối khi nộp, không theo lần kiểm tra
        post("/api/attempts/" + id + "/submit", u, Map.of("answers", List.of(Map.of("questionId", b.mcq(), "answer", Map.of("choiceId", b.right())))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(1));
        check(id, b.mcq(), u, b.right()).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("attempt_already_submitted"));
    }

    private void assertWrongCount(long attempt, long question, int expected) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, jdbc.queryForObject(
                "select wrong_count from attempt_question_checks where attempt_id = ? and question_id = ?", Integer.class, attempt, question));
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
        jdbc.update("update question_banks set mode = 'EXAM' where id = ?", b.id());
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
