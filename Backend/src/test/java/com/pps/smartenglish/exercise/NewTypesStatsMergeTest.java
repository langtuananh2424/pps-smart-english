package com.pps.smartenglish.exercise;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.ai.AiTestSupport;
import com.pps.smartenglish.exercise.service.RegradeRunner;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Tester: điểm ghép #67 (5 kiểu chấm mới) với #68 (breakdown, hồ sơ năng lực, answer_version) và chấm lại.
 * Câu kiểu mới phải đi qua thống kê đúng, chấm lại phải dùng payload (maxWords, options) như lúc nộp.
 */
class NewTypesStatsMergeTest extends AiTestSupport {

    private static final String CMS = "/api/admin/cms";

    @Autowired RegradeRunner regrader;

    @BeforeEach
    void cleanRegrade() {
        jdbc.update("update regrade_items set status = 'CANCELLED' where status in ('PENDING', 'AI_WAITING')");
        jdbc.update("update regrade_runs set status = 'DONE' where status = 'RUNNING'");
    }

    private Account admin() throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = 'ADMIN'", u.id());
        return login(u.email(), PASSWORD);
    }

    private JsonNode create(Account admin, long bank, Map<String, Object> body) throws Exception {
        return body(postJson(CMS + "/banks/" + bank + "/questions", admin, body).andExpect(status().isCreated()));
    }

    private ResultActions edit(Account admin, long q, Map<String, Object> body) throws Exception {
        return send(patch(CMS + "/questions/" + q).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)),
                admin.accessToken());
    }

    private void regrade(Account admin, long q, int version) throws Exception {
        send(MockMvcRequestBuilders.post("/api/admin/questions/" + q + "/regrade").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("answerVersion", version))), admin.accessToken()).andExpect(status().isAccepted());
        regrader.tick();
    }

    private JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private long submit(Account u, long bank, List<Map<String, Object>> answers) throws Exception {
        long id = idOf(postJson("/api/banks/" + bank + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
        postJson("/api/attempts/" + id + "/submit", u, Map.of("answers", answers)).andExpect(status().isOk());
        return id;
    }

    private static JsonNode metric(JsonNode list, String key) {
        for (JsonNode n : list) {
            if (key.equals(n.get("key").asText())) {
                return n;
            }
        }
        throw new AssertionError("Không thấy " + key + " trong " + list);
    }

    private Boolean correct(long attempt, long q) {
        return jdbc.queryForObject("select is_correct from student_answers where attempt_id = ? and question_id = ?", Boolean.class, attempt, q);
    }

    private int version(long attempt, long q) {
        return count("select answer_version from student_answers where attempt_id = ? and question_id = ?", attempt, q);
    }

    private static Map<String, Object> choice(String label, boolean correct) {
        return Map.of("label", label, "content", "Đáp án " + label, "isCorrect", correct);
    }

    @Test
    void newTypesFlowIntoBreakdownAndProfile() throws Exception {
        var admin = admin();
        long bank = bank(true);
        var multi = create(admin, bank, Map.of("type", "CHOICE_MULTI", "prompt", "Chọn hai",
                "choices", List.of(choice("A", true), choice("B", true), choice("C", false))));
        long multiId = multi.get("id").asLong();
        long a = multi.at("/choices/0/id").asLong();
        long c = multi.at("/choices/2/id").asLong();
        long tf = create(admin, bank, Map.of("type", "TRUE_FALSE", "prompt", "Sky is green",
                "payload", Map.of("options", List.of("TRUE", "FALSE", "NOT_GIVEN")), "answerKey", Map.of("answer", "FALSE"))).get("id").asLong();
        long fill = create(admin, bank, Map.of("type", "FILL_TEXT", "prompt", "Dessert", "payload", Map.of("maxWords", 2),
                "answerKey", Map.of("accepted", List.of("ice cream")))).get("id").asLong();

        var u = student(0);
        long att = submit(u, bank, List.of(
                Map.of("questionId", multiId, "answer", Map.of("choiceIds", List.of(a, c)), "timeSpentMs", 3000),
                Map.of("questionId", tf, "answer", Map.of("value", "FALSE"), "timeSpentMs", 1000),
                Map.of("questionId", fill, "answer", Map.of("text", "cold ice cream"))));

        var r = body(getJson("/api/attempts/" + att, u).andExpect(status().isOk()));
        var bd = r.get("breakdown");
        assertEquals(1, bd.get("correct").asInt()); // chỉ TRUE_FALSE đúng trọn
        assertEquals(2, bd.get("wrong").asInt()); // CHOICE_MULTI được 0.5 vẫn là "chưa đúng", FILL_TEXT vượt maxWords
        var m = metric(bd.get("byType"), "CHOICE_MULTI");
        assertEquals(0, new BigDecimal("0.5").compareTo(m.get("credit").decimalValue()));
        assertEquals(0, m.get("accuracy").asInt());
        assertEquals(1, metric(bd.get("byType"), "TRUE_FALSE").get("correct").asInt());
        assertEquals(0, metric(bd.get("byType"), "FILL_TEXT").get("correct").asInt());
        assertEquals(2, bd.get("wrongQuestions").size());
        // tổng credit của breakdown khớp điểm lượt
        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode n : bd.get("byType")) {
            sum = sum.add(n.get("credit").decimalValue());
        }
        assertEquals(0, sum.compareTo(r.get("score").decimalValue()));
        assertEquals(3000, count("select time_spent_ms from student_answers where attempt_id = ? and question_id = ?", att, multiId));

        var p = body(getJson("/api/me/learning-profile", u).andExpect(status().isOk()));
        assertEquals(3, p.get("answered").asInt());
        assertEquals(3, p.get("byType").size());
    }

    @Test
    void regradeOfTrueFalseUsesPayloadOptionsAndBumpsAnswerVersion() throws Exception {
        var admin = admin();
        long bank = bank(true);
        Map<String, Object> payload = Map.of("options", List.of("YES", "NO", "NOT_GIVEN"));
        long tf = create(admin, bank, Map.of("type", "TRUE_FALSE", "prompt", "Claim", "payload", payload,
                "answerKey", Map.of("answer", "NO"))).get("id").asLong();
        var u = student(0);
        long att = submit(u, bank, List.of(Map.of("questionId", tf, "answer", Map.of("value", "yes"))));
        assertEquals(Boolean.FALSE, correct(att, tf));
        assertEquals(1, body(getJson("/api/attempts/" + att, u)).at("/breakdown/wrongQuestions").size());

        edit(admin, tf, Map.of("answerKey", Map.of("answer", "YES"))).andExpect(status().isOk());
        regrade(admin, tf, 2);

        assertEquals(Boolean.TRUE, correct(att, tf)); // chấm lại phải thấy payload.options
        assertEquals(2, version(att, tf));
        var bd = body(getJson("/api/attempts/" + att, u)).get("breakdown");
        assertEquals(1, bd.get("correct").asInt());
        assertEquals(0, bd.get("wrongQuestions").size());
    }

    @Test
    void regradeOfFillTextStillEnforcesMaxWordsAndRecountsWrongs() throws Exception {
        var admin = admin();
        long bank = bank(true);
        long fill = create(admin, bank, Map.of("type", "FILL_TEXT", "prompt", "Dessert", "payload", Map.of("maxWords", 1),
                "answerKey", Map.of("accepted", List.of("gelato")))).get("id").asLong();
        var u = student(0);
        long a1 = submit(u, bank, List.of(Map.of("questionId", fill, "answer", Map.of("text", "the sorbet"))));
        long a2 = submit(u, bank, List.of(Map.of("questionId", fill, "answer", Map.of("text", "Sorbet"))));

        edit(admin, fill, Map.of("answerKey", Map.of("accepted", List.of("gelato", "sorbet")))).andExpect(status().isOk());
        regrade(admin, fill, 2);

        assertEquals(Boolean.FALSE, correct(a1, fill)); // hai từ: vẫn sai vì maxWords = 1
        assertEquals(Boolean.TRUE, correct(a2, fill));
        assertEquals(2, version(a1, fill));
        // lần sai của a1 đã chấm lại theo phiên bản 2 nên vẫn được tính
        var bd = body(getJson("/api/attempts/" + a1, u)).get("breakdown");
        assertEquals(1, bd.at("/wrongQuestions/0/wrongCount").asInt());
    }

    @Test
    void progressAndDraftNeverExposeKeysOfNewTypes() throws Exception {
        var admin = admin();
        long bank = bank(true);
        long match = create(admin, bank, Map.of("type", "MATCH_OPTIONS", "prompt", "Nối",
                "payload", Map.of("items", List.of(Map.of("id", "i1", "text", "x")),
                        "options", List.of(Map.of("id", "OPT_SECRET_A", "text", "one"), Map.of("id", "OPT_B", "text", "two"))),
                "answerKey", Map.of("matches", Map.of("i1", "OPT_SECRET_A")))).get("id").asLong();
        long fill = create(admin, bank, Map.of("type", "FILL_TEXT", "prompt", "Dessert",
                "answerKey", Map.of("accepted", List.of("ACCEPTED_SECRET")))).get("id").asLong();
        var u = student(0);
        long id = idOf(postJson("/api/banks/" + bank + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
        send(put("/api/attempts/" + id + "/draft").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                Map.of("answers", List.of(Map.of("questionId", match, "answer", Map.of("matches", Map.of("i1", "OPT_B")), "timeSpentMs", 10))))),
                u.accessToken()).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("ACCEPTED_SECRET"))))
                .andExpect(content().string(not(containsString("\"matches\":{\"i1\":\"OPT_SECRET_A\""))));
        getJson("/api/attempts/" + id + "/progress", u).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("ACCEPTED_SECRET"))))
                .andExpect(content().string(not(containsString("correctAnswer"))));
        getJson("/api/attempts/" + id, u).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("ACCEPTED_SECRET"))))
                .andExpect(content().string(not(containsString("breakdown"))));
        assertTrue(fill > 0);
    }
}
