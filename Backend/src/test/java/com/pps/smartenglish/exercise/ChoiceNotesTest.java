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

/** FR-EX-03: giải thích từng phương án chỉ ở nơi đã lộ đáp án (kết quả đã nộp, câu khóa sai), không ở bộ câu hỏi hay progress còn lượt. */
class ChoiceNotesTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";
    private static final String NOTE_A = "A sai vì lý do riêng A";
    private static final String NOTE_B = "B đúng vì lý do riêng B";

    private record Bank(long id, long q, long right, long wrong) {}

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private Bank bank() {
        long c = insert("courses", Map.of("slug", "cn-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED"));
        long t = insert("topics", Map.of("level_id", lv, "title", "Topic", "is_free_trial", true, "status", "PUBLISHED"));
        long bank = insert("question_banks", Map.of("topic_id", t, "title", "Bộ", "is_trial", false, "status", "PUBLISHED", "mode", "PRACTICE"));
        long q = insert("questions", Map.of("bank_id", bank, "type", "CONNECTIVE_MCQ", "prompt", "Đề", "sort_order", 1));
        long right = insert("question_choices", Map.of("question_id", q, "label", "B", "content", "Đúng", "is_correct", true, "explanation", NOTE_B));
        long wrong = insert("question_choices", Map.of("question_id", q, "label", "A", "content", "Sai", "is_correct", false, "explanation", NOTE_A));
        return new Bank(bank, q, right, wrong);
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

    private ResultActions check(long attempt, Bank b, String token, long choiceId) throws Exception {
        return post("/api/attempts/" + attempt + "/questions/" + b.q() + "/check", token, Map.of("answer", Map.of("choiceId", choiceId)));
    }

    @Test
    void notesOnlyAppearWhereAnswerIsRevealed() throws Exception {
        var b = bank();
        var u = register(newEmail(), PASSWORD).accessToken();
        String bankBody = send(get("/api/banks/" + b.id()), u).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(bankBody.contains(NOTE_A) || bankBody.contains(NOTE_B), "GET bank lộ giải thích phương án");
        long id = start(b, u);
        check(id, b, u, b.wrong()).andExpect(jsonPath("$.choiceNotes").doesNotExist());
        check(id, b, u, b.wrong()).andExpect(jsonPath("$.choiceNotes").doesNotExist());
        // khóa sai: có giải thích từng phương án, cả ở check lẫn progress
        check(id, b, u, b.wrong()).andExpect(jsonPath("$.choiceNotes.length()").value(2))
                .andExpect(jsonPath("$.choiceNotes[0].explanation").value(NOTE_A)).andExpect(jsonPath("$.choiceNotes[1].correct").value(true));
        send(get("/api/attempts/" + id + "/progress"), u).andExpect(jsonPath("$.retry.checks[0].choiceNotes.length()").value(2));

        post("/api/attempts/" + id + "/submit", u, Map.of("answers", List.of(Map.of("questionId", b.q(), "answer", Map.of("choiceId", b.wrong()))))
        ).andExpect(status().isOk()).andExpect(jsonPath("$.results[0].choiceNotes.length()").value(2));
    }

    @Test
    void openAttemptProgressHasNoNotes() throws Exception {
        var b = bank();
        var u = register(newEmail(), PASSWORD).accessToken();
        long id = start(b, u);
        check(id, b, u, b.wrong());
        String body = send(get("/api/attempts/" + id + "/progress"), u).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(body.contains(NOTE_A) || body.contains(NOTE_B), "progress còn lượt lộ giải thích phương án: " + body);
    }
}
