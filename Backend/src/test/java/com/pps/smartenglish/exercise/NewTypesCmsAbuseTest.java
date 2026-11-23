package com.pps.smartenglish.exercise;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.ai.AiTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tester #67: payload kiểu mới độc hại hoặc quá lớn gửi qua CMS; phân quyền CMS cho kiểu mới. */
class NewTypesCmsAbuseTest extends AiTestSupport {

    private static final String CMS = "/api/admin/cms";

    private Account withRole(String role) throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = ?", u.id(), role);
        return login(u.email(), PASSWORD);
    }

    private static Map<String, Object> fill(Object accepted) {
        return Map.of("type", "FILL_TEXT", "prompt", "p", "answerKey", Map.of("accepted", accepted));
    }

    @Test
    void nonEditorsCannotCreateNewTypes() throws Exception {
        long bank = bank(true);
        postJson(CMS + "/banks/" + bank + "/questions", student(0), fill(List.of("x"))).andExpect(status().isForbidden());
        postJson(CMS + "/banks/" + bank + "/questions", withRole("TEACHER"), fill(List.of("x"))).andExpect(status().isForbidden());
    }

    @Test
    void unknownFieldsAndWrongShapesAre400Not500() throws Exception {
        var admin = withRole("ADMIN");
        long bank = bank(true);
        for (Map<String, Object> bad : List.<Map<String, Object>>of(
                Map.of("type", "FILL_TEXT", "prompt", "p", "payload", Map.of("maxWords", 0), "answerKey", Map.of("accepted", List.of("x"))),
                Map.of("type", "FILL_TEXT", "prompt", "p", "payload", Map.of("maxWords", "2"), "answerKey", Map.of("accepted", List.of("x"))),
                Map.of("type", "FILL_TEXT", "prompt", "p", "payload", Map.of("maxWords", 1), "answerKey", Map.of("accepted", List.of("two words"))),
                Map.of("type", "FILL_TEXT", "prompt", "p", "answerKey", Map.of("accepted", List.of("x"), "regex", ".*")),
                Map.of("type", "FILL_TEXT", "prompt", "p", "answerKey", Map.of("accepted", "x")),
                Map.of("type", "TRUE_FALSE", "prompt", "p", "payload", Map.of("options", List.of("FALSE", "TRUE")), "answerKey", Map.of("answer", "TRUE")),
                Map.of("type", "TRUE_FALSE", "prompt", "p", "payload", Map.of("options", List.of("TRUE", "FALSE")), "answerKey", Map.of("answer", "NOT_GIVEN")),
                Map.of("type", "CHOICE_MULTI", "prompt", "p", "payload", Map.of("scoring", "SOME"),
                        "choices", List.of(Map.of("label", "A", "content", "a", "isCorrect", true), Map.of("label", "B", "content", "b", "isCorrect", false))),
                Map.of("type", "CHOICE_MULTI", "prompt", "p", "answerKey", Map.of("choiceIds", List.of(1)),
                        "choices", List.of(Map.of("label", "A", "content", "a", "isCorrect", true), Map.of("label", "B", "content", "b", "isCorrect", false))),
                Map.of("type", "WORD_BANK", "prompt", "p", "payload", Map.of("text", "{1} {1}", "blanks", List.of("1"), "words", List.of("a", "A")),
                        "answerKey", Map.of("blanks", Map.of("1", "a"))),
                Map.of("type", "WORD_BANK", "prompt", "p", "payload", Map.of("text", "{1} {2}", "blanks", List.of("1", "2"), "words", List.of("a", "b"),
                        "allowReuse", "yes"), "answerKey", Map.of("blanks", Map.of("1", "a", "2", "a"))),
                Map.of("type", "WORD_BANK", "prompt", "p", "payload", Map.of("text", "{1} {2}", "blanks", List.of("1", "2"), "words", List.of("a", "b")),
                        "answerKey", Map.of("blanks", Map.of("1", "a", "2", "a"))),
                Map.of("type", "MATCH_OPTIONS", "prompt", "p", "payload", Map.of("items", List.of(Map.of("id", "i", "text", "t")),
                        "options", List.of(Map.of("id", "A", "text", "a"), Map.of("id", "B", "text", "b"))),
                        "answerKey", Map.of("matches", Map.of("i", "Z"))),
                Map.of("type", "MATCH_OPTIONS", "prompt", "p", "payload", Map.of("items", List.of(Map.of("id", "i", "text", "t", "html", "<b>")),
                        "options", List.of(Map.of("id", "A", "text", "a"), Map.of("id", "B", "text", "b"))),
                        "answerKey", Map.of("matches", Map.of("i", "A"))))) {
            postJson(CMS + "/banks/" + bank + "/questions", admin, bad).andExpect(status().isBadRequest())
                    .andExpect(content().string(not(containsString("Exception"))));
        }
    }

    /**
     * Prompt có giới hạn 5000 ký tự, lựa chọn 500 ký tự (CmsQuestionService), nhưng chữ trong payload kiểu mới
     * (accepted, words, items/options text) không có giới hạn nào. Payload này gửi nguyên cho mọi học viên mở bộ câu.
     */
    @Test
    void oversizedNewTypePayloadIsRejected() throws Exception {
        var admin = withRole("ADMIN");
        long bank = bank(true);
        String huge = "a".repeat(200_000);
        postJson(CMS + "/banks/" + bank + "/questions", admin, Map.of("type", "MATCH_OPTIONS", "prompt", "p",
                "payload", Map.of("items", List.of(Map.of("id", "i", "text", huge)),
                        "options", List.of(Map.of("id", "A", "text", "a"), Map.of("id", "B", "text", "b"))),
                "answerKey", Map.of("matches", Map.of("i", "A")))).andExpect(status().isBadRequest());

        List<String> many = new ArrayList<>();
        for (int i = 0; i < 20_000; i++) {
            many.add("w" + i);
        }
        postJson(CMS + "/banks/" + bank + "/questions", admin, Map.of("type", "WORD_BANK", "prompt", "p",
                "payload", Map.of("text", "{1}", "blanks", List.of("1"), "words", many),
                "answerKey", Map.of("blanks", Map.of("1", "w1")))).andExpect(status().isBadRequest());
    }
}
