package com.pps.smartenglish.exercise;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.ai.AiTestSupport;
import com.pps.smartenglish.support.FakeStorageConfig.FakeStorage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** D-154, D-157 (FR-EX-05): nhóm có layout (bảng...) và các câu IELTS_* điền slot; kiểm chéo câu-layout; học viên không thấy đáp án. */
class IeltsLayoutApiTest extends AiTestSupport {

    private static final String CMS = "/api/admin/cms";
    private static final String TABLE = "{\"kind\":\"TABLE\",\"title\":\"Dam facts\",\"header\":[\"Item\",\"Detail\"],"
            + "\"rows\":[[\"Built in\",\"{q1}\"],[\"Height\",\"{q2} metres\"]]}";

    @Autowired FakeStorage storage;

    /** D-161: kiểu IELTS_* chỉ soạn được trong bộ của khóa IELTS. */
    private long ieltsBank() {
        long b = bank(true);
        jdbc.update("update courses set is_ielts = true where id = (select lv.course_id from question_banks qb join topics t on t.id = qb.topic_id"
                + " join course_levels lv on lv.id = t.level_id where qb.id = ?)", b);
        return b;
    }

    private Account admin() throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = 'ADMIN'", u.id());
        return login(u.email(), PASSWORD);
    }

    private ResultActions group(Account a, long bank, String layout) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("title", "Passage"));
        body.put("layout", json.readTree(layout));
        return postJson(CMS + "/banks/" + bank + "/groups", a, body);
    }

    private long groupId(Account a, long bank, String layout) throws Exception {
        return idOf(group(a, bank, layout).andExpect(status().isCreated()), "id");
    }

    private ResultActions question(Account a, long bank, long group, String type, Map<String, Object> payload, Map<String, Object> key) throws Exception {
        return postJson(CMS + "/banks/" + bank + "/questions", a,
                Map.of("type", type, "prompt", "p", "groupId", group, "payload", payload, "answerKey", key));
    }

    private ResultActions patchGroup(Account a, long group, String body) throws Exception {
        return send(patch(CMS + "/groups/" + group).contentType(MediaType.APPLICATION_JSON).content(body), a.accessToken());
    }

    private long readyImage(Account a) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB), "png", out);
        var permit = json.readTree(postJson(CMS + "/media/images", a, Map.of("contentType", "image/png")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        storage.put(permit.get("fields").get("key").asText(), out.toByteArray());
        long id = permit.get("assetId").asLong();
        postJson(CMS + "/media/" + id + "/complete", a, null).andExpect(status().isOk());
        return id;
    }

    private BigDecimal credit(long attempt, long q) {
        return jdbc.queryForObject("select credit from student_answers where attempt_id = ? and question_id = ?", BigDecimal.class, attempt, q);
    }

    @Test
    void tableGroupWithTwoQuestionsEndToEnd() throws Exception {
        var admin = admin();
        long bank = ieltsBank();
        long g = groupId(admin, bank, TABLE);
        long q1 = idOf(question(admin, bank, g, "IELTS_TABLE", Map.of("slot", "q1", "maxWords", 2), Map.of("accepted", List.of("1990", "nineteen ninety")))
                .andExpect(status().isCreated()), "id");
        long q2 = idOf(question(admin, bank, g, "IELTS_TABLE", Map.of("slot", "q2"), Map.of("accepted", List.of("120"))).andExpect(status().isCreated()), "id");

        var u = student(0);
        getJson("/api/banks/" + bank, u).andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].layout.kind").value("TABLE"))
                .andExpect(jsonPath("$.groups[0].layout.rows[0][1]").value("{q1}"))
                .andExpect(jsonPath("$.questions[0].payload.slot").value("q1"))
                .andExpect(content().string(not(containsString("accepted"))))
                .andExpect(content().string(not(containsString("answerKey"))))
                .andExpect(content().string(not(containsString("nineteen"))));

        long attempt = idOf(postJson("/api/banks/" + bank + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
        postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers", List.of(
                Map.of("questionId", q1, "answer", Map.of("text", " 1990 ")),
                Map.of("questionId", q2, "answer", Map.of("text", "130"))))).andExpect(status().isOk());
        assertEquals(0, BigDecimal.ONE.compareTo(credit(attempt, q1)));
        assertEquals(0, BigDecimal.ZERO.compareTo(credit(attempt, q2)));
        getJson("/api/attempts/" + attempt, u).andExpect(status().isOk())
                .andExpect(jsonPath("$.results[1].correctAnswer.accepted[0]").value("120"));
    }

    @Test
    void layoutAndSlotMismatchesAreRejected() throws Exception {
        var admin = admin();
        long bank = ieltsBank();
        long g = groupId(admin, bank, TABLE);
        Map<String, Object> key = Map.of("accepted", List.of("x"));

        question(admin, bank, g, "IELTS_TABLE", Map.of("slot", "q9"), key).andExpect(status().isBadRequest()); // slot không có trong layout
        question(admin, bank, g, "IELTS_NOTE", Map.of("slot", "q1"), key).andExpect(status().isBadRequest()); // sai kiểu layout
        question(admin, bank, g, "IELTS_MAP", Map.of("label", "Café"), Map.of("option", "A")).andExpect(status().isBadRequest());
        postJson(CMS + "/banks/" + bank + "/questions", admin, Map.of("type", "IELTS_TABLE", "prompt", "p", "payload", Map.of("slot", "q1"),
                "answerKey", key)).andExpect(status().isBadRequest()); // thiếu nhóm
        question(admin, bank, g, "IELTS_TABLE", Map.of("slot", "q1"), key).andExpect(status().isCreated());
        question(admin, bank, g, "IELTS_TABLE", Map.of("slot", "q1"), key).andExpect(status().isBadRequest()); // slot đã có câu khác

        // đổi layout làm mất slot của câu đang dùng
        patchGroup(admin, g, "{\"layout\":{\"kind\":\"FLOW\",\"steps\":[\"Start {q1}\",\"End\"]}}").andExpect(status().isBadRequest());
        patchGroup(admin, g, "{\"layout\":{\"kind\":\"TABLE\",\"header\":[\"A\"],\"rows\":[[\"{q2}\"]]}}").andExpect(status().isBadRequest());
        patchGroup(admin, g, "{\"layout\":null}").andExpect(status().isBadRequest());
        patchGroup(admin, g, "{\"layout\":{\"kind\":\"TABLE\",\"header\":[\"A\"],\"rows\":[[\"{q1}\"],[\"{q2}\"]]}}").andExpect(status().isOk());
    }

    @Test
    void invalidLayoutsAreRejectedAndLimitsHold() throws Exception {
        var admin = admin();
        long bank = ieltsBank();
        List<String> bad = new ArrayList<>(List.of(
                "{\"kind\":\"TABLE\",\"header\":[\"A\",\"B\"],\"rows\":[[\"{q1}\"]]}", // hàng thiếu ô
                "{\"kind\":\"TABLE\",\"header\":[\"A\"],\"rows\":[[\"no slot\"]]}", // không có chỗ trống
                "{\"kind\":\"TABLE\",\"header\":[\"A\"],\"rows\":[[\"{q1}\"],[\"{q1}\"]]}", // slot lặp
                "{\"kind\":\"WHATEVER\"}",
                "{\"kind\":\"OPTIONS\",\"options\":[{\"id\":\"A\",\"text\":\"x\"}]}", // dưới 2 lựa chọn
                "{\"kind\":\"OPTIONS\",\"options\":[{\"id\":\"A\",\"text\":\"x\"},{\"id\":\"a\",\"text\":\"y\"}]}", // id lặp không phân biệt hoa thường
                "{\"kind\":\"OPTIONS\",\"options\":[{\"id\":\"A\",\"text\":\"<b>x</b>\"},{\"id\":\"B\",\"text\":\"y\"}]}", // HTML trong chữ thuần
                "{\"kind\":\"OPTIONS\",\"extra\":1,\"options\":[{\"id\":\"A\",\"text\":\"x\"},{\"id\":\"B\",\"text\":\"y\"}]}",
                "{\"kind\":\"FORM\",\"rows\":[{\"label\":\"Name\",\"slot\":\"q1\",\"value\":\"Tom\"}]}", // vừa slot vừa value
                "{\"kind\":\"DIAGRAM\",\"assetId\":999999999,\"points\":[{\"slot\":\"q1\",\"x\":10,\"y\":10}]}", // ảnh không có
                "{\"kind\":\"FLOW\",\"steps\":" + json.writeValueAsString(java.util.Collections.nCopies(51, "step {q1}")) + "}")); // vượt 50 hàng
        bad.add("{\"kind\":\"TABLE\",\"header\":" + json.writeValueAsString(java.util.Collections.nCopies(11, "h"))
                + ",\"rows\":[[\"{q1}\"]]}"); // vượt 10 cột
        for (String layout : bad) {
            group(admin, bank, layout).andExpect(status().isBadRequest());
        }
    }

    @Test
    void everyLayoutKindIsAcceptedAndOptionAnswersAreCheckedAgainstLayout() throws Exception {
        var admin = admin();
        long bank = ieltsBank();
        long img = readyImage(admin);
        for (String layout : List.of(
                "{\"kind\":\"TEXT\",\"html\":\"<p>The {q1} is big. <script>x()</script></p>\"}",
                "{\"kind\":\"TEXT_BANK\",\"html\":\"<p>A {q1} B {q2}</p>\",\"words\":[{\"id\":\"w1\",\"text\":\"cat\"},{\"id\":\"w2\",\"text\":\"dog\"}],\"reuse\":false}",
                "{\"kind\":\"NOTE\",\"title\":\"Notes\",\"lines\":[{\"html\":\"<p>Name: {q1}</p>\"}]}",
                "{\"kind\":\"FLOW\",\"steps\":[\"Mix {q1}\",\"Bake\"]}",
                "{\"kind\":\"FORM\",\"rows\":[{\"label\":\"Name\",\"slot\":\"q1\"},{\"label\":\"City\",\"value\":\"Hanoi\"}]}",
                "{\"kind\":\"DIAGRAM\",\"assetId\":" + img + ",\"points\":[{\"slot\":\"q1\",\"x\":10,\"y\":20.5}]}",
                "{\"kind\":\"MAP\",\"assetId\":" + img + ",\"options\":[{\"id\":\"A\",\"x\":1,\"y\":2},{\"id\":\"B\",\"x\":50,\"y\":60}]}")) {
            group(admin, bank, layout).andExpect(status().isCreated()).andExpect(content().string(not(containsString("<script"))));
        }

        long g = groupId(admin, bank, "{\"kind\":\"OPTIONS\",\"title\":\"List of headings\",\"options\":[{\"id\":\"i\",\"text\":\"First\"},"
                + "{\"id\":\"ii\",\"text\":\"Second\"}],\"reuse\":false}");
        question(admin, bank, g, "IELTS_MATCH_HEADINGS", Map.of("paragraph", "Paragraph B"), Map.of("option", "iii")).andExpect(status().isBadRequest());
        long q = idOf(question(admin, bank, g, "IELTS_MATCH_HEADINGS", Map.of("paragraph", "Paragraph B"), Map.of("option", "II"))
                .andExpect(status().isCreated()), "id");
        var u = student(0);
        long attempt = idOf(postJson("/api/banks/" + bank + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
        postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers", List.of(Map.of("questionId", q, "answer", Map.of("option", "ii")))))
                .andExpect(status().isOk());
        assertEquals(0, BigDecimal.ONE.compareTo(credit(attempt, q)));
    }

    @Test
    void listeningTypesNeedGroupAudio() throws Exception {
        var admin = admin();
        long bank = ieltsBank();
        long g = idOf(postJson(CMS + "/banks/" + bank + "/groups", admin, Map.of("title", "Audio")).andExpect(status().isCreated()), "id");
        question(admin, bank, g, "DICTATION", Map.of("maxWords", 6), Map.of("accepted", List.of("It is a nice day.")))
                .andExpect(status().isBadRequest()); // nhóm chưa có audio
    }
}
