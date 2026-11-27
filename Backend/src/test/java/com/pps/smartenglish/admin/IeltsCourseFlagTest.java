package com.pps.smartenglish.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.support.ApiTestBase;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** D-161: cờ courses.is_ielts quyết định bộ nào được soạn kiểu câu IELTS_*; CMS bộ trả ielts và skill. */
class IeltsCourseFlagTest extends ApiTestBase {

    private static final String CMS = "/api/admin/cms";

    private static MockHttpServletRequestBuilder post(String url) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url);
    }

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private Account admin() throws Exception {
        var u = register(newEmail(), "Password123!");
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = 'ADMIN'", u.id());
        return login(u.email(), "Password123!");
    }

    private ResultActions call(MockHttpServletRequestBuilder req, Account a, Object body) throws Exception {
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return send(req, a.accessToken());
    }

    private long idOf(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private record Place(long course, long topic) {}

    private Place place(boolean ielts) {
        long c = insert("courses", Map.of("slug", "i-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED", "is_ielts", ielts));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "L", "status", "PUBLISHED"));
        return new Place(c, insert("topics", Map.of("level_id", lv, "title", "T", "is_free_trial", true, "status", "PUBLISHED", "skill", "READING")));
    }

    private long bank(Account a, long topic) throws Exception {
        return idOf(call(post(CMS + "/topics/" + topic + "/banks"), a, Map.of("title", "Bộ", "isTrial", false)).andExpect(status().isCreated()));
    }

    private static Map<String, Object> tfng() {
        return Map.of("type", "IELTS_TFNG", "prompt", "p", "payload", Map.of("statement", "The dam is old."), "answerKey", Map.of("value", "TRUE"));
    }

    @Test
    void ieltsQuestionTypeRejectedInPlainCourseAcceptedInIeltsCourse() throws Exception {
        var a = admin();
        long plain = bank(a, place(false).topic());
        call(post(CMS + "/banks/" + plain + "/questions"), a, tfng()).andExpect(status().isBadRequest());
        long ielts = bank(a, place(true).topic());
        call(post(CMS + "/banks/" + ielts + "/questions"), a, tfng()).andExpect(status().isCreated());
    }

    @Test
    void existingIeltsQuestionStaysEditableInPlainBankButTypeCannotBeSwitchedTo() throws Exception {
        var a = admin();
        var p = place(false);
        long bank = bank(a, p.topic());
        long q = idOf(call(post(CMS + "/banks/" + bank + "/questions"), a, Map.of("type", "CONNECTIVE_MCQ", "prompt", "p", "choices", java.util.List.of(
                Map.of("label", "A", "content", "Therefore", "isCorrect", false), Map.of("label", "B", "content", "However", "isCorrect", true)))));
        call(patch(CMS + "/questions/" + q), a, Map.of("type", "IELTS_TFNG", "payload", Map.of("statement", "s"), "answerKey", Map.of("value", "FALSE")))
                .andExpect(status().isBadRequest());
        call(patch(CMS + "/questions/" + q), a, Map.of("explanation", "ok")).andExpect(status().isOk());
    }

    @Test
    void bankViewCarriesIeltsAndSkillAndFlagDefaultsFalse() throws Exception {
        var a = admin();
        var yes = place(true);
        var no = place(false);
        bank(a, yes.topic());
        bank(a, no.topic());
        call(get(CMS + "/topics/" + yes.topic() + "/banks"), a, null).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ielts").value(true)).andExpect(jsonPath("$[0].skill").value("READING"));
        call(get(CMS + "/topics/" + no.topic() + "/banks"), a, null).andExpect(jsonPath("$[0].ielts").value(false));
    }

    @Test
    void courseCanBeFlaggedThroughPatch() throws Exception {
        var a = admin();
        long c = insert("courses", Map.of("slug", "f-" + UUID.randomUUID(), "title", "Khóa", "status", "DRAFT"));
        call(patch(CMS + "/courses/" + c), a, Map.of("ielts", true)).andExpect(status().isOk()).andExpect(jsonPath("$.ielts").value(true));
    }
}
