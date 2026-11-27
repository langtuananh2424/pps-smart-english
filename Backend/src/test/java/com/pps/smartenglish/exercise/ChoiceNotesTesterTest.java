package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Tester FR-EX-03 choiceNotes (P0 lộ đáp án): lượt EXAM chưa nộp, IDOR lượt người khác, danh sách lượt của tôi. */
class ChoiceNotesTesterTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";
    private static final String NOTE_A = "Ghi chu rieng phuong an A";
    private static final String NOTE_B = "Ghi chu rieng phuong an B";

    private record Bank(long id, long q, long right, long wrong) {}

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private Bank bank(String mode) {
        long c = insert("courses", Map.of("slug", "cnt-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED"));
        long t = insert("topics", Map.of("level_id", lv, "title", "Topic", "is_free_trial", true, "status", "PUBLISHED"));
        long bank = insert("question_banks", Map.of("topic_id", t, "title", "Bộ", "is_trial", false, "status", "PUBLISHED", "mode", mode));
        long q = insert("questions", Map.of("bank_id", bank, "type", "CONNECTIVE_MCQ", "prompt", "Đề", "sort_order", 1));
        long right = insert("question_choices", Map.of("question_id", q, "label", "B", "content", "Đúng", "is_correct", true, "explanation", NOTE_B));
        long wrong = insert("question_choices", Map.of("question_id", q, "label", "A", "content", "Sai", "is_correct", false, "explanation", NOTE_A));
        return new Bank(bank, q, right, wrong);
    }

    private ResultActions post(String url, String token, Object body) throws Exception {
        return send(MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)), token);
    }

    private String text(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString();
    }

    private static boolean leaks(String body) {
        return body.contains(NOTE_A) || body.contains(NOTE_B) || body.contains("choiceNotes");
    }

    @Test
    void openExamAttemptNeverLeaksNotes() throws Exception {
        var b = bank("EXAM");
        var u0 = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at) select ?, c.id, 'ACTIVE', "
                + "TIMESTAMP '2020-01-01 00:00:00' from question_banks b join topics t on t.id = b.topic_id join course_levels l on l.id = t.level_id "
                + "join courses c on c.id = l.course_id where b.id = ?", u0.id(), b.id());
        String u = u0.accessToken();
        assertFalse(leaks(text(send(get("/api/banks/" + b.id()), u))), "GET bank");
        long id = json.readTree(text(post("/api/banks/" + b.id() + "/attempts", u, Map.of()).andExpect(status().isCreated()))).get("id").asLong();
        for (String url : List.of("/api/attempts/" + id, "/api/attempts/" + id + "/progress", "/api/attempts/" + id + "/hints", "/api/me/attempts")) {
            String body = text(send(get(url), u));
            assertFalse(leaks(body), url + " lộ giải thích phương án: " + body);
        }
        for (int i = 0; i < 3; i++) {
            String body = text(post("/api/attempts/" + id + "/questions/" + b.q() + "/check", u, Map.of("answer", Map.of("choiceId", b.wrong()))));
            assertFalse(leaks(body), "check ở lượt EXAM: " + body);
        }
        post("/api/attempts/" + id + "/submit", u, Map.of("answers", List.of(Map.of("questionId", b.q(), "answer", Map.of("choiceId", b.wrong())))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results[0].choiceNotes.length()").value(2));
    }

    @Test
    void anotherUserCannotReadNotesOfSubmittedAttempt() throws Exception {
        var b = bank("PRACTICE");
        String owner = register(newEmail(), PASSWORD).accessToken();
        String other = register(newEmail(), PASSWORD).accessToken();
        long id = json.readTree(text(post("/api/banks/" + b.id() + "/attempts", owner, Map.of()).andExpect(status().isCreated()))).get("id").asLong();
        post("/api/attempts/" + id + "/submit", owner, Map.of("answers", List.of())).andExpect(status().isOk());
        for (String url : List.of("/api/attempts/" + id, "/api/attempts/" + id + "/progress")) {
            var r = send(get(url), other).andExpect(status().isNotFound());
            assertFalse(leaks(text(r)), url);
        }
        assertFalse(leaks(text(send(get("/api/me/attempts"), other))));
    }
}
