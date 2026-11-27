package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.support.ApiTestBase;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Tester D-164: câu archived không chặn bảng band; Viết/Nói không nhận kỹ năng; bộ đã xuất bản có band không nhận câu tự chấm thiếu kỹ năng. */
class ExamSkillBandTesterTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";
    private static final String CMS = "/api/admin/cms";

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private Account admin() throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = 'ADMIN'", u.id());
        return login(u.email(), PASSWORD);
    }

    private ResultActions call(MockHttpServletRequestBuilder req, Account u, Object body) throws Exception {
        return send(req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)), u.accessToken());
    }

    private long id(ResultActions r) throws Exception {
        return json.readTree(r.andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private long examBank(Account admin) throws Exception {
        long course = insert("courses", Map.of("slug", "esbt-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", course, "code", "A", "title", "Level A", "status", "PUBLISHED"));
        long topic = insert("topics", Map.of("level_id", lv, "title", "Topic", "status", "PUBLISHED"));
        return id(call(MockMvcRequestBuilders.post(CMS + "/topics/" + topic + "/banks"), admin, Map.of("title", "Thi " + UUID.randomUUID(), "mode", "EXAM")));
    }

    private ResultActions mcq(Account admin, long bank, String skill) throws Exception {
        var body = new HashMap<String, Object>(Map.of("type", "CONNECTIVE_MCQ", "prompt", "Câu " + UUID.randomUUID(),
                "choices", List.of(Map.of("label", "A", "content", "x", "isCorrect", true), Map.of("label", "B", "content", "y", "isCorrect", false))));
        if (skill != null) {
            body.put("skill", skill);
        }
        return call(MockMvcRequestBuilders.post(CMS + "/banks/" + bank + "/questions"), admin, body);
    }

    private ResultActions rules(Account admin, long bank) throws Exception {
        return call(put(CMS + "/banks/" + bank + "/band-rules"), admin, Map.of("rules", List.of(
                Map.of("skill", "READING", "minPercent", 0, "band", 4.0), Map.of("skill", "READING", "minPercent", 100, "band", 6.0))));
    }

    private ResultActions publish(Account admin, long bank) throws Exception {
        return send(MockMvcRequestBuilders.post(CMS + "/banks/" + bank + "/publish"), admin.accessToken());
    }

    @Test
    void archivedQuestionWithoutSkillDoesNotBlockBandsOrPublish() throws Exception {
        var admin = admin();
        long bank = examBank(admin);
        id(mcq(admin, bank, "READING"));
        long old = id(mcq(admin, bank, null));
        jdbc.update("update questions set archived_at = CURRENT_TIMESTAMP where id = ?", old);
        rules(admin, bank).andExpect(status().isOk());
        publish(admin, bank).andExpect(status().isOk());
    }

    /** Lỗi chặn cả lô: mọi câu thiếu kỹ năng đều được liệt kê, không chỉ câu đầu. */
    @Test
    void bandRuleRejectionListsEveryMissingQuestion() throws Exception {
        var admin = admin();
        long bank = examBank(admin);
        id(mcq(admin, bank, "READING"));
        long a = id(mcq(admin, bank, null));
        long b = id(mcq(admin, bank, null));
        rules(admin, bank).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.questionIds.length()").value(2))
                .andExpect(jsonPath("$.error.details.questionIds[0]").value(a))
                .andExpect(jsonPath("$.error.details.questionIds[1]").value(b));
    }

    /** D-167: kỹ năng của câu Viết suy từ dạng câu, nên CMS không nhận skill gửi kèm. */
    @Test
    void writingQuestionInExamBankCannotTakeSkill() throws Exception {
        var admin = admin();
        long bank = examBank(admin);
        call(MockMvcRequestBuilders.post(CMS + "/banks/" + bank + "/questions"), admin, Map.of("type", "WRITING", "prompt", "Viết",
                "skill", "READING", "payload", Map.of("task", "Do you agree?", "requiredWords", 120)))
                .andExpect(status().isBadRequest());
        assertEquals(0, count("select count(*) from questions where bank_id = ? and skill is not null and type = 'WRITING'", bank));
    }

    /** D-164 (2): bộ đã xuất bản có bảng band; thêm câu tự chấm thiếu kỹ năng (soạn tay) làm band lệch mà không bị chặn. */
    @Test
    void publishedBandedBankRejectsNewAutoQuestionWithoutSkill() throws Exception {
        var admin = admin();
        long bank = examBank(admin);
        id(mcq(admin, bank, "READING"));
        rules(admin, bank).andExpect(status().isOk());
        publish(admin, bank).andExpect(status().isOk());
        mcq(admin, bank, null).andExpect(status().isBadRequest());
    }

    /** Như trên, qua nhập hàng loạt (dán nhanh). */
    @Test
    void importIntoPublishedBandedBankRejectsQuestionsWithoutSkill() throws Exception {
        var admin = admin();
        long bank = examBank(admin);
        id(mcq(admin, bank, "READING"));
        rules(admin, bank).andExpect(status().isOk());
        publish(admin, bank).andExpect(status().isOk());
        String paste = "# Trắc nghiệm\nQ: She was tired ___ she kept working.\nA) because\n*B) although\nC) so\n";
        var preview = json.readTree(send(MockMvcRequestBuilders.multipart(CMS + "/banks/" + bank + "/imports/preview").param("text", paste),
                admin.accessToken()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        List<Map<String, Object>> rows = new ArrayList<>();
        preview.get("rows").forEach(r -> rows.add(Map.of("line", r.get("line").asInt(), "question", json.convertValue(r.get("question"), Map.class))));
        int status = call(MockMvcRequestBuilders.post(CMS + "/banks/" + bank + "/imports"), admin,
                Map.of("source", "PASTE", "mode", "ALL_OR_NOTHING", "rows", rows)).andReturn().getResponse().getStatus();
        int noSkill = count("select count(*) from questions where bank_id = ? and skill is null and archived_at is null", bank);
        assertTrue(status >= 400 && noSkill == 0, "nhập vào bộ đã có band tạo " + noSkill + " câu thiếu kỹ năng (HTTP " + status + ")");
    }
}
