package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.ai.AiTestSupport;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** D-154: id khối chữ LETTER_ORDER do server sinh ngẫu nhiên và học viên nhận thứ tự đã xáo, nên id không lộ từ đúng. */
class LetterOrderIdLeakTest extends AiTestSupport {

    private static final String WORD = "keyboard";

    private List<JsonNode> lettersFor(long bank, Account u, long qid) throws Exception {
        var res = getJson("/api/banks/" + bank, u).andExpect(status().isOk());
        for (JsonNode q : json.readTree(res.andReturn().getResponse().getContentAsString()).get("questions")) {
            if (q.get("id").asLong() == qid) {
                var out = new ArrayList<JsonNode>();
                q.get("payload").get("letters").forEach(out::add);
                return out;
            }
        }
        throw new AssertionError("không thấy câu");
    }

    @Test
    void savedIdsAreNotSequentialAndLearnerOrderIsShuffled() throws Exception {
        var a = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = 'ADMIN'", a.id());
        var admin = login(a.email(), PASSWORD);
        long bank = bank(true);
        var sequential = new ArrayList<Map<String, String>>();
        for (int i = 0; i < WORD.length(); i++) {
            sequential.add(Map.of("id", "l" + (i + 1), "ch", String.valueOf(WORD.charAt(i))));
        }
        var res = postJson("/api/admin/cms/banks/" + bank + "/questions", admin, Map.of("type", "LETTER_ORDER", "prompt", "Sắp xếp",
                "payload", Map.of("letters", sequential), "answerKey", Map.of("word", WORD))).andExpect(status().isCreated());
        long qid = json.readTree(res.andReturn().getResponse().getContentAsString()).get("id").asLong();

        var u = student(0);
        postJson("/api/banks/" + bank + "/attempts", u, Map.of()).andExpect(status().isCreated());
        var letters = lettersFor(bank, u, qid);
        var ids = new HashSet<String>();
        letters.forEach(l -> ids.add(l.get("id").asText()));
        assertEquals(WORD.length(), ids.size(), "id không trùng");
        assertTrue(ids.stream().noneMatch(id -> id.matches("l\\d+")), "server đã thay id l1..lN do client gửi");
        var byId = new ArrayList<>(letters);
        byId.sort((x, y) -> x.get("id").asText().compareTo(y.get("id").asText()));
        var s = new StringBuilder();
        letters.forEach(l -> s.append(l.get("ch").asText()));
        var t = new StringBuilder();
        byId.forEach(l -> t.append(l.get("ch").asText()));
        assertNotEquals(WORD, s.toString(), "thứ tự trả cho học viên đã xáo");
        assertNotEquals(WORD, t.toString(), "xếp theo id cũng không ra từ đúng");
    }
}
