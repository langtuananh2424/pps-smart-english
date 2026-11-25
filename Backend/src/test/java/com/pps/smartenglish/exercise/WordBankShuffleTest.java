package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.ai.AiTestSupport;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** D-146: hộp từ WORD_BANK xáo tất định theo (lượt, câu); chấm theo giá trị từ nên không đổi. */
class WordBankShuffleTest extends AiTestSupport {

    private static final List<String> WORDS = List.of("a1", "b2", "c3", "d4", "e5", "f6", "g7", "h8");

    private Account admin() throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = 'ADMIN'", u.id());
        return login(u.email(), PASSWORD);
    }

    private List<String> wordsOf(long bank, Account u, long qid) throws Exception {
        var res = getJson("/api/banks/" + bank, u).andExpect(status().isOk());
        JsonNode qs = json.readTree(res.andReturn().getResponse().getContentAsString()).get("questions");
        var out = new ArrayList<String>();
        for (JsonNode q : qs) {
            if (q.get("id").asLong() == qid) {
                q.get("payload").get("words").forEach(w -> out.add(w.asText()));
            }
        }
        return out;
    }

    private static List<String> expected(long attemptId, long qid) {
        var w = new ArrayList<>(WORDS);
        Collections.shuffle(w, new Random(attemptId * 1_000_003L + qid));
        return w;
    }

    @Test
    void orderStableWithinAttemptDiffersAcrossAttemptsAndGradesByValue() throws Exception {
        var admin = admin();
        long bank = bank(true);
        var res = postJson("/api/admin/cms/banks/" + bank + "/questions", admin, Map.of("type", "WORD_BANK", "prompt", "Điền từ",
                "payload", Map.of("text", "{1} {2}", "blanks", List.of("1", "2"), "words", WORDS),
                "answerKey", Map.of("blanks", Map.of("1", "a1", "2", "b2")))).andExpect(status().isCreated());
        long qid = json.readTree(res.andReturn().getResponse().getContentAsString()).get("id").asLong();
        var u = student(0);

        long first = idOf(postJson("/api/banks/" + bank + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
        var order1 = wordsOf(bank, u, qid);
        assertEquals(order1, wordsOf(bank, u, qid), "cùng lượt, nhiều lần GET thì thứ tự không đổi");
        assertEquals(new HashSet<>(WORDS), new HashSet<>(order1));
        assertEquals(expected(first, qid), order1, "thứ tự theo công thức seed D-146");

        postJson("/api/attempts/" + first + "/submit", u, Map.of("answers", List.of(
                Map.of("questionId", qid, "answer", Map.of("blanks", Map.of("1", "a1", "2", "b2")))))).andExpect(status().isOk());
        assertEquals(0, BigDecimal.ONE.compareTo(jdbc.queryForObject(
                "select credit from student_answers where attempt_id = ? and question_id = ?", BigDecimal.class, first, qid)));

        long second = idOf(postJson("/api/banks/" + bank + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
        assertEquals(expected(second, qid), wordsOf(bank, u, qid), "lượt mới: seed theo id lượt mới");
    }
}
