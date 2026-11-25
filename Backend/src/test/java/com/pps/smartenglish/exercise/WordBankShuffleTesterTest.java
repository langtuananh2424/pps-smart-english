package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.ai.AiTestSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Tester D-146: thứ tự hộp từ theo đúng luồng web (GET bộ trước, bắt đầu lượt sau), CMS giữ thứ tự soạn, test tất định. */
class WordBankShuffleTesterTest extends AiTestSupport {

    private static final List<String> WORDS = List.of("a1", "b2", "c3", "d4", "e5", "f6", "g7", "h8");

    private Account admin() throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = 'ADMIN'", u.id());
        return login(u.email(), PASSWORD);
    }

    private long wordBankQuestion(Account admin, long bank) throws Exception {
        var res = postJson("/api/admin/cms/banks/" + bank + "/questions", admin, Map.of("type", "WORD_BANK", "prompt", "Điền từ",
                "payload", Map.of("text", "{1} {2}", "blanks", List.of("1", "2"), "words", WORDS),
                "answerKey", Map.of("blanks", Map.of("1", "a1", "2", "b2")))).andExpect(status().isCreated());
        return json.readTree(res.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private List<String> words(JsonNode questions, long qid) {
        var out = new ArrayList<String>();
        for (JsonNode q : questions) {
            if (q.get("id").asLong() == qid) {
                q.get("payload").get("words").forEach(w -> out.add(w.asText()));
            }
        }
        return out;
    }

    private List<String> studentWords(long bank, Account u, long qid) throws Exception {
        var body = getJson("/api/banks/" + bank, u).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return words(json.readTree(body).get("questions"), qid);
    }

    /** Thứ tự mong đợi tính độc lập theo công thức D-146: không phụ thuộc may rủi. */
    private static List<String> expected(long seedAttempt, long qid) {
        var w = new ArrayList<>(WORDS);
        Collections.shuffle(w, new Random(seedAttempt * 1_000_003L + qid));
        return w;
    }

    @Test
    void seedFollowsFormulaDeterministically() throws Exception {
        var admin = admin();
        long bank = bank(true);
        long qid = wordBankQuestion(admin, bank);
        var u = student(0);
        assertEquals(expected(0, qid), studentWords(bank, u, qid), "chưa có lượt: seed 0");
        long a1 = idOf(postJson("/api/banks/" + bank + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
        assertEquals(expected(a1, qid), studentWords(bank, u, qid), "lượt mở: seed theo id lượt");
    }

    /**
     * D-146: trong cùng một lượt thứ tự hộp từ không đổi. Web tải bộ sau khi lượt đã mở, rồi vào lại lượt dở
     * (tải lại trang) thì GET vẫn trả đúng thứ tự đó.
     */
    @Test
    void sameAttemptKeepsOrderAcrossWebResume() throws Exception {
        var admin = admin();
        long bank = bank(true);
        long qid = wordBankQuestion(admin, bank);
        var u = student(0);
        postJson("/api/banks/" + bank + "/attempts", u, Map.of()).andExpect(status().isCreated());
        var shown = studentWords(bank, u, qid);    // web tải bộ sau khi lượt đã mở
        var shownOnResume = studentWords(bank, u, qid); // vào lại lượt dở
        assertEquals(shown, shownOnResume, "cùng một lượt nhưng thứ tự hộp từ đổi sau khi tải lại trang");
    }

    @Test
    void cmsKeepsAuthoredOrder() throws Exception {
        var admin = admin();
        long bank = bank(true);
        long qid = wordBankQuestion(admin, bank);
        var u = student(0);
        postJson("/api/banks/" + bank + "/attempts", u, Map.of()).andExpect(status().isCreated());
        var body = getJson("/api/admin/cms/banks/" + bank, admin).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(WORDS, words(json.readTree(body).get("questions"), qid));
    }
}
