package com.pps.smartenglish.exercise;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.support.ApiTestBase;
import com.pps.smartenglish.support.FakeStorageConfig.FakeStorage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** #66, D-133: nhóm câu hỏi (bài đọc dùng chung) và nội dung giàu định dạng do server làm sạch. */
class QuestionGroupApiTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";
    private static final String CMS = "/api/admin/cms";
    private static final String GROUP_ID_NULL = "{\"groupId\":null}";

    @Autowired FakeStorage storage;

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private Account withRole(String role) throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = ?", u.id(), role);
        return login(u.email(), PASSWORD);
    }

    private ResultActions call(MockHttpServletRequestBuilder req, Account a, Object body) throws Exception {
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(body instanceof String s ? s : json.writeValueAsString(body));
        }
        return send(req, a.accessToken());
    }

    private static MockHttpServletRequestBuilder post(String url) {
        return MockMvcRequestBuilders.post(url);
    }

    private JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private long newBank(Account admin) throws Exception {
        long c = insert("courses", Map.of("slug", "g-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "L", "status", "PUBLISHED"));
        long t = insert("topics", Map.of("level_id", lv, "title", "T", "is_free_trial", true, "status", "PUBLISHED"));
        return body(call(post(CMS + "/topics/" + t + "/banks"), admin, Map.of("title", "Bộ", "isTrial", true))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private ResultActions addQuestion(Account admin, long bank, Map<String, Object> extra) throws Exception {
        Map<String, Object> req = new HashMap<>(Map.of("type", "CONNECTIVE_MCQ",
                "choices", List.of(Map.of("label", "A", "content", "Therefore", "isCorrect", false),
                        Map.of("label", "B", "content", "However", "isCorrect", true))));
        req.putAll(extra);
        return call(post(CMS + "/banks/" + bank + "/questions"), admin, req);
    }

    private long createQuestion(Account admin, long bank, Map<String, Object> extra) throws Exception {
        return body(addQuestion(admin, bank, extra).andExpect(status().isCreated())).get("id").asLong();
    }

    private long createGroup(Account admin, long bank) throws Exception {
        return body(call(post(CMS + "/banks/" + bank + "/groups"), admin, Map.of("title", "Passage 1", "sortOrder", 1))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private long readyImage(Account admin) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB), "png", out);
        var permit = body(call(post(CMS + "/media/images"), admin, Map.of("contentType", "image/png")).andExpect(status().isCreated()));
        storage.put(permit.get("fields").get("key").asText(), out.toByteArray());
        long id = permit.get("assetId").asLong();
        call(post(CMS + "/media/" + id + "/complete"), admin, null).andExpect(status().isOk());
        return id;
    }

    private Account student() throws Exception {
        return login(register(newEmail(), PASSWORD).email(), PASSWORD);
    }

    @Test
    void groupCrudWithInUseGuardAndNotFounds() throws Exception {
        var admin = withRole("EDITOR");
        long bank = newBank(admin);
        var g = body(call(post(CMS + "/banks/" + bank + "/groups"), admin, Map.of("title", " Passage 1 ", "instruction", "Read",
                "passageHtml", "<p onclick=\"x()\">Text</p><script>alert(1)</script>", "sortOrder", 2)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Passage 1")).andExpect(jsonPath("$.passageHtml").value("<p>Text</p>")));
        long id = g.get("id").asLong();
        assertEquals("<p>Text</p>", jdbc.queryForObject("select passage_html from question_groups where id = ?", String.class, id));

        call(patch(CMS + "/groups/" + id), admin, Map.of("title", "Passage A", "passageHtml", "")).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Passage A")).andExpect(jsonPath("$.instruction").value("Read"))
                .andExpect(jsonPath("$.passageHtml").value(nullValue()));
        call(get(CMS + "/banks/" + bank), admin, null).andExpect(jsonPath("$.groups[0].id").value(id))
                .andExpect(jsonPath("$.groups[0].sortOrder").value(2));

        long q = createQuestion(admin, bank, Map.of("prompt", "Q", "groupId", id));
        call(delete(CMS + "/groups/" + id), admin, null).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("in_use"));
        call(patch(CMS + "/questions/" + q), admin, GROUP_ID_NULL).andExpect(status().isOk());
        call(delete(CMS + "/groups/" + id), admin, null).andExpect(status().isNoContent());

        call(post(CMS + "/banks/999999999/groups"), admin, Map.of("title", "x")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("bank_not_found"));
        call(patch(CMS + "/groups/999999999"), admin, Map.of("title", "x")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("group_not_found"));
        call(delete(CMS + "/groups/999999999"), admin, null).andExpect(status().isNotFound());
    }

    @Test
    void promptHtmlWithImageIsRenderedForCmsAndLearnerAndXssIsStripped() throws Exception {
        var admin = withRole("ADMIN");
        long bank = newBank(admin);
        long img = readyImage(admin);
        long group = createGroup(admin, bank);
        String html = "<p>Pick <b>one</b><img data-asset-id=\"" + img + "\" src=\"http://evil/x.png\" onerror=\"x()\">"
                + "<script>alert(1)</script></p>";
        long q = createQuestion(admin, bank, Map.of("promptHtml", html, "groupId", group));

        String stored = jdbc.queryForObject("select prompt_html from questions where id = ?", String.class, q);
        assertEquals("<p>Pick <b>one</b><img data-asset-id=\"" + img + "\"></p>", stored);
        assertEquals("Pick one", jdbc.queryForObject("select prompt from questions where id = ?", String.class, q));

        call(get(CMS + "/banks/" + bank), admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].prompt").value("Pick one"))
                .andExpect(jsonPath("$.questions[0].groupId").value(group))
                .andExpect(jsonPath("$.questions[0].promptHtml").value(containsString("src=\"http://fake-storage/media/images/")))
                .andExpect(jsonPath("$.questions[0].promptHtml").value(containsString("width=\"40\"")))
                .andExpect(jsonPath("$.questions[0].promptHtml").value(not(containsString("evil"))));
        call(post(CMS + "/banks/" + bank + "/publish"), admin, null).andExpect(status().isOk());
        send(get("/api/banks/" + bank), student().accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].promptHtml").value(containsString("src=\"http://fake-storage/media/images/")))
                .andExpect(jsonPath("$.questions[0].groupId").value(group))
                .andExpect(jsonPath("$.groups[0].id").value(group)).andExpect(jsonPath("$.groups[0].title").value("Passage 1"));
    }

    @Test
    void learnerSeesPassageAndChoiceHtmlButNoAnswers() throws Exception {
        var admin = withRole("ADMIN");
        long bank = newBank(admin);
        long img = readyImage(admin);
        long group = body(call(post(CMS + "/banks/" + bank + "/groups"), admin,
                Map.of("passageHtml", "<h3>Title</h3><p>Body<img data-asset-id=\"" + img + "\"></p>")).andExpect(status().isCreated()))
                .get("id").asLong();
        createQuestion(admin, bank, Map.of("prompt", "Q", "groupId", group, "choices", List.of(
                Map.of("label", "A", "contentHtml", "<p><em>Therefore</em></p>", "isCorrect", false),
                Map.of("label", "B", "content", "However", "isCorrect", true))));
        call(post(CMS + "/banks/" + bank + "/publish"), admin, null).andExpect(status().isOk());

        var res = send(get("/api/banks/" + bank), student().accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].passageHtml").value(containsString("src=\"http://fake-storage/")))
                .andExpect(jsonPath("$.questions[0].choices[0].content").value("Therefore"))
                .andExpect(jsonPath("$.questions[0].choices[0].contentHtml").value("<p><em>Therefore</em></p>"))
                .andExpect(jsonPath("$.questions[0].choices[1].contentHtml").value(nullValue()));
        assertFalse(res.andReturn().getResponse().getContentAsString().contains("isCorrect"));
    }

    @Test
    void groupFromAnotherBankIsRejectedAndPatchCanMoveOrRemoveTheGroup() throws Exception {
        var admin = withRole("ADMIN");
        long bank = newBank(admin);
        long other = newBank(admin);
        long foreign = createGroup(admin, other);
        long mine = createGroup(admin, bank);
        addQuestion(admin, bank, Map.of("prompt", "Q", "groupId", foreign)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.groupId").exists());

        long q = createQuestion(admin, bank, Map.of("prompt", "Q"));
        call(patch(CMS + "/questions/" + q), admin, Map.of("groupId", foreign)).andExpect(status().isBadRequest());
        call(patch(CMS + "/questions/" + q), admin, Map.of("groupId", mine)).andExpect(jsonPath("$.groupId").value(mine));
        call(patch(CMS + "/questions/" + q), admin, Map.of("explanation", "e")).andExpect(jsonPath("$.groupId").value(mine)); // vắng mặt = giữ
        call(patch(CMS + "/questions/" + q), admin, GROUP_ID_NULL).andExpect(jsonPath("$.groupId").value(nullValue()));
    }

    @Test
    void plainPromptPatchClearsHtmlAndPromptHtmlPatchReplacesPrompt() throws Exception {
        var admin = withRole("ADMIN");
        long bank = newBank(admin);
        long q = createQuestion(admin, bank, Map.of("promptHtml", "<p>Rich <i>text</i></p>"));
        call(patch(CMS + "/questions/" + q), admin, Map.of("explanation", "e"))
                .andExpect(jsonPath("$.promptHtml").value("<p>Rich <i>text</i></p>"));
        call(patch(CMS + "/questions/" + q), admin, Map.of("prompt", "Plain")).andExpect(status().isOk())
                .andExpect(jsonPath("$.prompt").value("Plain")).andExpect(jsonPath("$.promptHtml").value(nullValue()));
        call(patch(CMS + "/questions/" + q), admin, Map.of("promptHtml", "<p>New</p>")).andExpect(jsonPath("$.prompt").value("New"))
                .andExpect(jsonPath("$.promptHtml").value("<p>New</p>"));
        assertEquals(1, jdbc.queryForObject("select answer_version from questions where id = ?", Integer.class, q));
    }

    @Test
    void invalidRichInputIsRejected() throws Exception {
        var admin = withRole("ADMIN");
        long bank = newBank(admin);
        addQuestion(admin, bank, Map.of()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.prompt").exists());
        addQuestion(admin, bank, Map.of("promptHtml", "<script>x</script><p> </p>")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.promptHtml").exists());
        addQuestion(admin, bank, Map.of("prompt", "Q", "choices", List.of(Map.of("label", "A", "isCorrect", true),
                Map.of("label", "B", "content", "x", "isCorrect", false)))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[\"choices[0].content\"]").exists());

        var pending = body(call(post(CMS + "/media/images"), admin, Map.of("contentType", "image/png")));
        addQuestion(admin, bank, Map.of("promptHtml", "<p>Q<img data-asset-id=\"" + pending.get("assetId").asLong() + "\"></p>"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("media_not_ready"));
        addQuestion(admin, bank, Map.of("prompt", "Q", "choices", List.of(
                Map.of("label", "A", "contentHtml", "<p>x<img data-asset-id=\"999999\"></p>", "isCorrect", true),
                Map.of("label", "B", "content", "x", "isCorrect", false)))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("media_not_ready"));
        call(post(CMS + "/banks/" + bank + "/groups"), admin, Map.of("passageHtml", "<p><img data-asset-id=\"999999\"></p>"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("media_not_ready"));
        jdbc.update("delete from media_assets where id = ?", pending.get("assetId").asLong()); // không để lại lượt tải dở cho test khác
    }

    @Test
    void studentsCannotUseGroupEndpoints() throws Exception {
        var admin = withRole("ADMIN");
        long bank = newBank(admin);
        long group = createGroup(admin, bank);
        var s = student();
        call(post(CMS + "/banks/" + bank + "/groups"), s, Map.of("title", "x")).andExpect(status().isForbidden());
        call(patch(CMS + "/groups/" + group), s, Map.of("title", "x")).andExpect(status().isForbidden());
        call(delete(CMS + "/groups/" + group), s, null).andExpect(status().isForbidden());
    }
}
