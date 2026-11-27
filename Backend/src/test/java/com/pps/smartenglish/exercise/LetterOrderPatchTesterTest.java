package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.pps.smartenglish.ai.AiTestSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * D-154 (tester): sau khi server sinh id ngẫu nhiên, sửa câu LETTER_ORDER mà tập chữ không đổi (chỉ đổi thứ tự hiển thị, hay sửa
 * đáp án) không được đổi id: câu trả lời đã nộp và bản nháp lượt đang mở lưu theo id, đổi id thì chấm lại/hiển thị kết quả hỏng.
 */
class LetterOrderPatchTesterTest extends AiTestSupport {

    private JsonNode stored(long qid) throws Exception {
        return json.readTree(jdbc.queryForObject("select payload from questions where id = ?", String.class, qid));
    }

    private static TreeSet<String> ids(JsonNode payload) {
        var out = new TreeSet<String>();
        payload.get("letters").forEach(l -> out.add(l.get("id").asText()));
        return out;
    }

    @Test
    void reorderingLettersKeepsTheirIds() throws Exception {
        var a = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = 'ADMIN'", a.id());
        var admin = login(a.email(), PASSWORD);
        long bank = bank(true);
        var letters = List.of(Map.of("id", "x1", "ch", "t"), Map.of("id", "x2", "ch", "a"), Map.of("id", "x3", "ch", "c"));
        var res = postJson("/api/admin/cms/banks/" + bank + "/questions", admin, Map.of("type", "LETTER_ORDER", "prompt", "Sắp xếp",
                "payload", Map.of("letters", letters), "answerKey", Map.of("word", "cat"))).andExpect(status().isCreated());
        long qid = json.readTree(res.andReturn().getResponse().getContentAsString()).get("id").asLong();
        var before = stored(qid);

        // CMS gửi lại đúng các khối đã đọc (id server) nhưng đổi thứ tự hiển thị
        var list = new ArrayList<JsonNode>();
        before.get("letters").forEach(list::add);
        Collections.reverse(list);
        ArrayNode reordered = json.createArrayNode().addAll(list);
        var body = json.createObjectNode();
        body.putObject("payload").set("letters", reordered);
        send(MockMvcRequestBuilders.patch("/api/admin/cms/questions/" + qid).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)), admin.accessToken()).andExpect(status().isOk());

        assertEquals(ids(before), ids(stored(qid)), "đổi thứ tự hiển thị làm đổi id khối chữ");
    }
}
