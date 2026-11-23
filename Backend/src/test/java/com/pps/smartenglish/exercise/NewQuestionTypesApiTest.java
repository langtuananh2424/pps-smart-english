package com.pps.smartenglish.exercise;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.ai.AiTestSupport;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** FR-EX-04/05: tạo 5 kiểu chấm mới qua CMS, làm bài, nộp, chấm tự động; đáp án không lộ trước khi nộp. */
class NewQuestionTypesApiTest extends AiTestSupport {

    private static final String CMS = "/api/admin/cms";

    private Account admin() throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = 'ADMIN'", u.id());
        return login(u.email(), PASSWORD);
    }

    private JsonNode create(Account admin, long bank, Map<String, Object> body) throws Exception {
        var res = postJson(CMS + "/banks/" + bank + "/questions", admin, body).andExpect(status().isCreated());
        return json.readTree(res.andReturn().getResponse().getContentAsString());
    }

    private static Map<String, Object> choice(String label, boolean correct) {
        return Map.of("label", label, "content", "Đáp án " + label, "isCorrect", correct);
    }

    private BigDecimal credit(long attempt, long q) {
        return jdbc.queryForObject("select credit from student_answers where attempt_id = ? and question_id = ?", BigDecimal.class, attempt, q);
    }

    @Test
    void fiveNewTypesGradeEndToEndWithoutLeakingKeys() throws Exception {
        var admin = admin();
        long bank = bank(true);

        var multi = create(admin, bank, Map.of("type", "CHOICE_MULTI", "prompt", "Chọn hai đáp án đúng",
                "choices", List.of(choice("A", true), choice("B", true), choice("C", false))));
        long multiId = multi.get("id").asLong();
        long a = multi.get("choices").get(0).get("id").asLong();
        long b = multi.get("choices").get(1).get("id").asLong();
        long c = multi.get("choices").get(2).get("id").asLong();

        long tf = create(admin, bank, Map.of("type", "TRUE_FALSE", "prompt", "The sky is green.",
                "payload", Map.of("options", List.of("TRUE", "FALSE", "NOT_GIVEN")), "answerKey", Map.of("answer", "FALSE"))).get("id").asLong();
        long fill = create(admin, bank, Map.of("type", "FILL_TEXT", "prompt", "A cold dessert",
                "payload", Map.of("maxWords", 2), "answerKey", Map.of("accepted", List.of("ice cream", "gelato")))).get("id").asLong();
        long words = create(admin, bank, Map.of("type", "WORD_BANK", "prompt", "Điền từ",
                "payload", Map.of("text", "The {1} chased the {2}.", "blanks", List.of("1", "2"), "words", List.of("cat", "mouse", "dog")),
                "answerKey", Map.of("blanks", Map.of("1", "cat", "2", "mouse")))).get("id").asLong();
        long match = create(admin, bank, Map.of("type", "MATCH_OPTIONS", "prompt", "Nối",
                "payload", Map.of("items", List.of(Map.of("id", "q1", "text", "x"), Map.of("id", "q2", "text", "y")),
                        "options", List.of(Map.of("id", "A", "text", "one"), Map.of("id", "B", "text", "two"))),
                "answerKey", Map.of("matches", Map.of("q1", "A", "q2", "A")))).get("id").asLong();

        var u = student(0);
        // đề cho học viên: có lựa chọn của CHOICE_MULTI nhưng không có đáp án ở bất kỳ kiểu nào
        getJson("/api/banks/" + bank, u).andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].choices.length()").value(3))
                .andExpect(jsonPath("$.questions[0].choices[0].isCorrect").doesNotExist())
                .andExpect(content().string(not(containsString("accepted"))))
                .andExpect(content().string(not(containsString("matches"))))
                .andExpect(content().string(not(containsString("answerKey"))))
                .andExpect(content().string(not(containsString("isCorrect"))))
                .andExpect(content().string(not(containsString("ice cream"))));
        long attempt = idOf(postJson("/api/banks/" + bank + "/attempts", u, Map.of()).andExpect(status().isCreated())
                .andExpect(content().string(not(containsString("accepted")))), "id");

        postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers", List.of(
                Map.of("questionId", multiId, "answer", Map.of("choiceIds", List.of(a, c))), // 1 đúng, 1 thừa: 1/2
                Map.of("questionId", tf, "answer", Map.of("value", "false")),
                Map.of("questionId", fill, "answer", Map.of("text", "  ICE   Cream ")),
                Map.of("questionId", words, "answer", Map.of("blanks", Map.of("1", "cat", "2", "dog"))), // sai một chỗ: PARTIAL được 0.5
                Map.of("questionId", match, "answer", Map.of("matches", Map.of("q1", "A", "q2", "A")))))).andExpect(status().isOk());

        assertEquals(0, new BigDecimal("0.5").compareTo(credit(attempt, multiId)));
        assertEquals(0, BigDecimal.ONE.compareTo(credit(attempt, tf)));
        assertEquals(0, BigDecimal.ONE.compareTo(credit(attempt, fill)));
        assertEquals(0, new BigDecimal("0.5").compareTo(credit(attempt, words))); // D-140: chấm từng phần, 1/2 chỗ đúng
        assertEquals(0, BigDecimal.ONE.compareTo(credit(attempt, match)));

        // sau khi nộp mới có đáp án đúng
        getJson("/api/attempts/" + attempt, u).andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].isCorrect").value(false))
                .andExpect(jsonPath("$.results[0].correctAnswer.choiceIds[0]").value(a))
                .andExpect(jsonPath("$.results[0].correctAnswer.choiceIds[1]").value(b))
                .andExpect(jsonPath("$.results[1].isCorrect").value(true))
                .andExpect(jsonPath("$.results[3].isCorrect").value(false))
                .andExpect(jsonPath("$.results[3].correctAnswer.blanks['2']").value("mouse"));
    }

    @Test
    void cmsRejectsBadPayloadsOfNewTypes() throws Exception {
        var admin = admin();
        long bank = bank(true);
        for (Map<String, Object> bad : List.<Map<String, Object>>of(
                Map.of("type", "TRUE_FALSE", "prompt", "p", "payload", Map.of("options", List.of("TRUE", "NO")), "answerKey", Map.of("answer", "TRUE")),
                Map.of("type", "FILL_TEXT", "prompt", "p", "answerKey", Map.of("accepted", List.of())),
                Map.of("type", "WORD_BANK", "prompt", "p", "payload", Map.of("text", "x", "blanks", List.of("1"), "words", List.of("a")),
                        "answerKey", Map.of("blanks", Map.of("1", "a"))),
                Map.of("type", "MATCH_OPTIONS", "prompt", "p"),
                Map.of("type", "CHOICE_MULTI", "prompt", "p", "choices", List.of(choice("A", true), choice("B", true))),
                // choices chỉ dành cho trắc nghiệm
                Map.of("type", "FILL_TEXT", "prompt", "p", "answerKey", Map.of("accepted", List.of("x")), "choices", List.of(choice("A", true))))) {
            postJson(CMS + "/banks/" + bank + "/questions", admin, bad).andExpect(status().isBadRequest());
        }
    }
}
