package com.pps.smartenglish.admin;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.support.ApiTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** FR-ADM-02 (không gồm import): CMS bộ câu hỏi và câu hỏi, kiểm tra theo dạng, khóa khi đã có bài nộp, xóa an toàn. */
class CmsQuestionTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";
    private static final String CMS = "/api/admin/cms";

    private static MockHttpServletRequestBuilder post(String url) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url);
    }

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private Account withRole(String role) throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("update users set role = ? where id = ?", role, u.id());
        return login(u.email(), PASSWORD);
    }

    private ResultActions call(MockHttpServletRequestBuilder req, Account a, Object body) throws Exception {
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return a == null ? mvc.perform(req) : send(req, a.accessToken());
    }

    private long idOf(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    /** Topic học thử đã PUBLISHED trọn chuỗi để học viên thấy bộ câu hỏi sau khi xuất bản; kèm một bài học cùng topic. */
    private record Place(long topic, long lesson, long otherTopicLesson) {}

    private Place place() {
        long c = insert("courses", Map.of("slug", "q-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "L", "status", "PUBLISHED"));
        long t = insert("topics", Map.of("level_id", lv, "title", "T", "is_free_trial", true, "status", "PUBLISHED"));
        long t2 = insert("topics", Map.of("level_id", lv, "title", "T2", "is_free_trial", true, "status", "PUBLISHED"));
        return new Place(t, lesson(t), lesson(t2));
    }

    private long lesson(long topic) {
        return insert("lessons", Map.of("topic_id", topic, "type", "CONNECTIVE_VIDEO", "title", "B", "status", "PUBLISHED",
                "video_source", "YOUTUBE", "video_ref", "dQw4w9WgXcQ", "duration_sec", 60));
    }

    private long bank(Account a, long topic) throws Exception {
        return idOf(call(post(CMS + "/topics/" + topic + "/banks"), a, Map.of("title", "Bộ", "isTrial", false)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT")));
    }

    private ResultActions question(Account a, long bank, Map<String, Object> body) throws Exception {
        return call(post(CMS + "/banks/" + bank + "/questions"), a, body);
    }

    private static Map<String, Object> q(String type, Map<String, Object> rest) {
        var m = new java.util.HashMap<String, Object>(rest);
        m.put("type", type);
        m.putIfAbsent("prompt", "Đề " + type);
        return m;
    }

    private static Map<String, Object> mcq() {
        return q("CONNECTIVE_MCQ", Map.of("choices", List.of(Map.of("label", "A", "content", "Therefore", "isCorrect", false),
                Map.of("label", "B", "content", "Nevertheless", "isCorrect", true, "explanation", "Đối lập"))));
    }

    private static Map<String, Object> cloze() {
        return q("CONNECTIVE_CLOZE", Map.of("payload", Map.of("text", "I like {b1} and {b2}.", "blanks", List.of("b1", "b2")),
                "answerKey", Map.of("blanks", Map.of("b1", List.of("tea", "Tea"), "b2", List.of("SECRETCLOZE")))));
    }

    private static Map<String, Object> build() {
        return q("GRAMMAR_BUILD", Map.of("payload", Map.of("blocks", List.of(Map.of("id", "b2", "text", "was built"),
                Map.of("id", "b1", "text", "The bridge"), Map.of("id", "b3", "text", "by workers"))),
                "answerKey", Map.of("order", List.of("b1", "b2", "b3"))));
    }

    // ---- quyền ----

    @Test
    void onlyAdminAndEditorMayUseTheQuestionCms() throws Exception {
        var place = place();
        var admin = withRole("ADMIN");
        var editor = withRole("EDITOR");
        var student = register(newEmail(), PASSWORD);
        long bank = bank(admin, place.topic);
        call(get(CMS + "/banks/" + bank), null, null).andExpect(status().isUnauthorized());
        call(get(CMS + "/banks/" + bank), student, null).andExpect(status().isForbidden());
        call(post(CMS + "/topics/" + place.topic + "/banks"), student, Map.of("title", "x")).andExpect(status().isForbidden());
        call(get(CMS + "/grammar-elements"), student, null).andExpect(status().isForbidden());
        call(get(CMS + "/banks/" + bank), editor, null).andExpect(status().isOk());
        call(get(CMS + "/banks/" + bank), admin, null).andExpect(status().isOk());
        bank(editor, place.topic);
    }

    @Test
    void grammarElementsAreTheEightBuiltInOnesAndTagsAreChecked() throws Exception {
        var admin = withRole("EDITOR");
        call(get(CMS + "/grammar-elements"), admin, null).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$[*].code", hasItems("RELATIVE_CLAUSE", "PASSIVE", "OTHER_COMMON")));
        long bank = bank(admin, place().topic);
        var body = mcq();
        body.put("grammarElementCodes", List.of("PASSIVE", "RELATIVE_CLAUSE"));
        question(admin, bank, body).andExpect(status().isCreated())
                .andExpect(jsonPath("$.grammarElementCodes[0]").value("RELATIVE_CLAUSE")).andExpect(jsonPath("$.grammarElementCodes[1]").value("PASSIVE"));
        var bad = mcq();
        bad.put("grammarElementCodes", List.of("PASSIVE", "NOPE"));
        question(admin, bank, bad).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details['grammarElementCodes']").exists());
    }

    // ---- bộ câu hỏi ----

    @Test
    void bankLifecycleWithLessonCheckListingAndPublishRules() throws Exception {
        var admin = withRole("ADMIN");
        var place = place();
        call(get(CMS + "/topics/999999999/banks"), admin, null).andExpect(status().isNotFound());
        call(post(CMS + "/topics/" + place.topic + "/banks"), admin, Map.of("title", "x", "lessonId", place.otherTopicLesson))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.lessonId").exists());
        call(post(CMS + "/topics/" + place.topic + "/banks"), admin, Map.of("title", "x", "lessonId", 999999999L)).andExpect(status().isBadRequest());
        call(post(CMS + "/topics/" + place.topic + "/banks"), admin, Map.of("title", " ")).andExpect(status().isBadRequest());

        long bank = idOf(call(post(CMS + "/topics/" + place.topic + "/banks"), admin, Map.of("title", "Bộ A", "lessonId", place.lesson, "isTrial", true))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.lessonId").value(place.lesson)).andExpect(jsonPath("$.isTrial").value(true)));
        call(patch(CMS + "/banks/" + bank), admin, Map.of("lessonId", place.otherTopicLesson)).andExpect(status().isBadRequest());
        call(patch(CMS + "/banks/" + bank), admin, Map.of("title", "Bộ B", "isTrial", false)).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Bộ B")).andExpect(jsonPath("$.isTrial").value(false)).andExpect(jsonPath("$.lessonId").value(place.lesson))
                .andExpect(jsonPath("$.topicId").value(place.topic));
        call(get(CMS + "/topics/" + place.topic + "/banks"), admin, null).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].questionCount").value(0));

        // xuất bản bộ rỗng: 400; có câu rồi thì được và idempotent
        call(post(CMS + "/banks/" + bank + "/publish"), admin, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.questions").exists());
        question(admin, bank, mcq()).andExpect(status().isCreated());
        for (int i = 0; i < 2; i++) {
            call(post(CMS + "/banks/" + bank + "/publish"), admin, null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"))
                    .andExpect(jsonPath("$.questionCount").value(1));
        }
        for (int i = 0; i < 2; i++) {
            call(post(CMS + "/banks/" + bank + "/unpublish"), admin, null).andExpect(jsonPath("$.status").value("DRAFT"));
        }
        call(get(CMS + "/banks/999999999"), admin, null).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("bank_not_found"));
        call(delete(CMS + "/banks/" + bank), admin, null).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("in_use")); // còn câu hỏi
    }

    // ---- câu hỏi: mỗi dạng một ca hợp lệ ----

    @Test
    void everySupportedTypeAcceptsAValidQuestionAndStoresItExactly() throws Exception {
        var admin = withRole("ADMIN");
        long bank = bank(admin, place().topic);
        var cases = List.of(mcq(), cloze(), build(),
                q("CONNECTIVE_MATCH", Map.of("payload", Map.of("left", List.of(Map.of("id", "l1", "text", "It rained"), Map.of("id", "l2", "text", "He studied")),
                        "right", List.of(Map.of("id", "r1", "text", "so we stayed"), Map.of("id", "r2", "text", "because he wanted"))),
                        "answerKey", Map.of("pairs", Map.of("l1", "r1", "l2", "r2")))),
                q("CONNECTIVE_REWRITE", Map.of("payload", Map.of("sentence", "It was hard. We won.", "requiredWord", "although"),
                        "answerKey", Map.of("acceptedAnswers", List.of("Although it was hard, we won.")))),
                q("CONNECTIVE_REWRITE", Map.of("payload", Map.of("sentence", "It was hard. We won.", "requiredWord", "although"))), // không có key: AI chấm
                q("GRAMMAR_IDENTIFY", Map.of("payload", Map.of("tokens", List.of("The", "book", "that", "I", "read", "was", "good")),
                        "answerKey", Map.of("spans", List.of(Map.of("start", 2, "end", 4, "element", "RELATIVE_CLAUSE"))))),
                q("GRAMMAR_SPOT_FIX", Map.of("payload", Map.of("tokens", List.of("The", "letter", "was", "write")),
                        "answerKey", Map.of("position", 3, "corrections", List.of("written")))),
                q("GRAMMAR_REWRITE", Map.of("payload", Map.of("sentence", "They built it.", "requiredElement", "PASSIVE"))),
                q("WRITING", Map.of("payload", Map.of("task", "Do you agree?", "requiredWords", 120))));
        for (var body : cases) {
            ResultActions r = question(admin, bank, body).andExpect(status().isCreated()).andExpect(jsonPath("$.type").value(body.get("type")));
            JsonNode saved = json.readTree(r.andReturn().getResponse().getContentAsString());
            if (body.get("payload") != null) {
                assertEquals(json.valueToTree(body.get("payload")), saved.get("payload"), String.valueOf(body.get("type")));
            }
            if (body.get("answerKey") != null) {
                assertEquals(json.valueToTree(body.get("answerKey")), saved.get("answerKey"), String.valueOf(body.get("type")));
            }
        }
        call(get(CMS + "/banks/" + bank), admin, null).andExpect(jsonPath("$.questions", hasSize(cases.size())))
                .andExpect(jsonPath("$.questions[0].choices[?(@.isCorrect==true)].label").value(hasItems("B")))
                .andExpect(jsonPath("$.questions[0].choices", hasSize(2)));
    }

    // ---- câu hỏi: các ca sai chính ----

    private void rejected(Account a, long bank, Map<String, Object> body, String detailField) throws Exception {
        question(a, bank, body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("validation_failed"))
                .andExpect(jsonPath("$.error.details['" + detailField + "']").exists());
    }

    @Test
    void invalidQuestionsAreRejectedWithTheOffendingField() throws Exception {
        var admin = withRole("ADMIN");
        long bank = bank(admin, place().topic);
        var ok = Map.of("label", "A", "content", "x", "isCorrect", true);
        var no = Map.of("label", "B", "content", "y", "isCorrect", false);

        // trắc nghiệm
        rejected(admin, bank, q("CONNECTIVE_MCQ", Map.of("choices", List.of(ok))), "choices");
        rejected(admin, bank, q("CONNECTIVE_MCQ", Map.of("choices", List.of(no, Map.of("label", "C", "content", "z", "isCorrect", false)))), "choices");
        rejected(admin, bank, q("CONNECTIVE_MCQ", Map.of("choices", List.of(ok, Map.of("label", "B", "content", "z", "isCorrect", true)))), "choices");
        rejected(admin, bank, q("CONNECTIVE_MCQ", Map.of("choices", List.of(ok, Map.of("label", "A", "content", "z", "isCorrect", false)))), "choices[1].label");
        rejected(admin, bank, q("CONNECTIVE_MCQ", Map.of("choices", List.of(ok, no), "payload", Map.of("x", 1))), "payload");
        rejected(admin, bank, q("CONNECTIVE_MCQ", Map.of("choices", List.of(ok, no), "answerKey", Map.of("choiceId", 1))), "answerKey");
        // ghép cặp
        var side = Map.of("left", List.of(Map.of("id", "l1", "text", "a"), Map.of("id", "l2", "text", "b")),
                "right", List.of(Map.of("id", "r1", "text", "c"), Map.of("id", "r2", "text", "d")));
        rejected(admin, bank, q("CONNECTIVE_MATCH", Map.of("payload", side, "answerKey", Map.of("pairs", Map.of("l1", "r1")))), "answerKey.pairs");
        rejected(admin, bank, q("CONNECTIVE_MATCH", Map.of("payload", side, "answerKey", Map.of("pairs", Map.of("l1", "r1", "l2", "r9")))), "answerKey.pairs.l2");
        // điền từ
        var clozePayload = Map.of("text", "A {b1} B {b2}", "blanks", List.of("b1", "b2"));
        rejected(admin, bank, q("CONNECTIVE_CLOZE", Map.of("payload", Map.of("text", "A {b1} B {b3}", "blanks", List.of("b1", "b2")),
                "answerKey", Map.of("blanks", Map.of("b1", List.of("x"), "b2", List.of("y"))))), "payload.text");
        rejected(admin, bank, q("CONNECTIVE_CLOZE", Map.of("payload", clozePayload, "answerKey", Map.of("blanks", Map.of("b1", List.of("x"))))), "answerKey.blanks");
        rejected(admin, bank, q("CONNECTIVE_CLOZE", Map.of("payload", clozePayload, "answerKey", Map.of("blanks", Map.of("b1", List.of("x"), "b2", List.of())))),
                "answerKey.blanks.b2");
        // viết lại
        rejected(admin, bank, q("CONNECTIVE_REWRITE", Map.of("payload", Map.of("sentence", "s"))), "payload.requiredWord");
        rejected(admin, bank, q("CONNECTIVE_REWRITE", Map.of("payload", Map.of("sentence", "s", "requiredWord", "w"), "answerKey", Map.of("acceptedAnswers", "x"))),
                "answerKey.acceptedAnswers");
        rejected(admin, bank, q("GRAMMAR_REWRITE", Map.of("payload", Map.of("sentence", "s", "requiredElement", "NOPE"))), "payload.requiredElement");
        // nhận diện, sửa lỗi
        var tokens = Map.of("tokens", List.of("a", "b", "c"));
        rejected(admin, bank, q("GRAMMAR_IDENTIFY", Map.of("payload", tokens, "answerKey", Map.of("spans", List.of(Map.of("start", 1, "end", 3, "element", "PASSIVE"))))),
                "answerKey.spans[0]");
        rejected(admin, bank, q("GRAMMAR_IDENTIFY", Map.of("payload", tokens, "answerKey", Map.of("spans", List.of(Map.of("start", 0, "end", 1, "element", "NOPE"))))),
                "answerKey.spans[0].element");
        rejected(admin, bank, q("GRAMMAR_SPOT_FIX", Map.of("payload", tokens, "answerKey", Map.of("position", 3, "corrections", List.of("x")))), "answerKey.position");
        rejected(admin, bank, q("GRAMMAR_SPOT_FIX", Map.of("payload", tokens, "answerKey", Map.of("position", 1, "corrections", List.of()))), "answerKey.corrections");
        // xếp khối
        var blocks = Map.of("blocks", List.of(Map.of("id", "b1", "text", "a"), Map.of("id", "b2", "text", "b"), Map.of("id", "b3", "text", "c")));
        rejected(admin, bank, q("GRAMMAR_BUILD", Map.of("payload", blocks, "answerKey", Map.of("order", List.of("b1", "b2")))), "answerKey.order");
        rejected(admin, bank, q("GRAMMAR_BUILD", Map.of("payload", blocks, "answerKey", Map.of("order", List.of("b1", "b2", "b2")))), "answerKey.order[2]");
        // viết, nói, dạng lạ, choices ở dạng khác
        rejected(admin, bank, q("WRITING", Map.of("payload", Map.of("task", "t", "requiredWords", 0))), "payload.requiredWords");
        rejected(admin, bank, q("WRITING", Map.of("payload", Map.of("task", "t", "requiredWords", 50), "answerKey", Map.of("x", 1))), "answerKey");
        rejected(admin, bank, q("SPEAKING", Map.of("payload", Map.of("x", 1))), "type");
        rejected(admin, bank, q("NOPE", Map.of()), "type");
        rejected(admin, bank, q("WRITING", Map.of("payload", Map.of("task", "t", "requiredWords", 50), "choices", List.of(ok, no))), "choices");
        question(admin, bank, Map.of("type", "CONNECTIVE_MCQ")).andExpect(status().isBadRequest()); // thiếu prompt
        question(admin, 999999999L, mcq()).andExpect(status().isNotFound());
        assertEquals(0, count("select count(*) from questions where bank_id = ?", bank));
    }

    @Test
    void payloadMayOnlyHoldTheFieldsOfItsTypeSoNoAnswerCanLeakToStudents() throws Exception {
        var admin = withRole("ADMIN");
        long bank = bank(admin, place().topic);
        var tokens = List.of("a", "b", "c");
        for (String leak : new String[] {"answerKey", "answer", "solution", "correct", "order", "spans"}) {
            rejected(admin, bank, q("GRAMMAR_SPOT_FIX", Map.of("payload", Map.of("tokens", tokens, leak, "x"),
                    "answerKey", Map.of("position", 1, "corrections", List.of("x")))), "payload." + leak);
            rejected(admin, bank, q("WRITING", Map.of("payload", Map.of("task", "t", "requiredWords", 10, leak, "x"))), "payload." + leak);
            rejected(admin, bank, q("CONNECTIVE_CLOZE", Map.of("payload", Map.of("text", "{b}", "blanks", List.of("b"), leak, "x"),
                    "answerKey", Map.of("blanks", Map.of("b", List.of("x"))))), "payload." + leak);
        }
        // sửa payload bằng PATCH cũng bị chặn như vậy
        long id = idOf(question(admin, bank, cloze()).andExpect(status().isCreated()));
        call(patch(CMS + "/questions/" + id), admin, Map.of("payload", Map.of("text", "I like {b1} and {b2}.", "blanks", List.of("b1", "b2"), "answers", "x")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details['payload.answers']").exists());
    }

    // ---- dữ liệu CMS khớp grader: học viên làm và chấm đúng ----

    @Test
    void questionsMadeInTheCmsAreGradedCorrectlyAndNeverLeakAnswersToStudents() throws Exception {
        var admin = withRole("EDITOR");
        var student = register(newEmail(), PASSWORD);
        long bank = bank(admin, place().topic);
        long mcqId = idOf(question(admin, bank, mcq()));
        long clozeId = idOf(question(admin, bank, cloze()));
        long buildId = idOf(question(admin, bank, build()));
        call(post(CMS + "/banks/" + bank + "/publish"), admin, null).andExpect(status().isOk());

        // đề của học viên: không answerKey, không isCorrect, không đáp án, không giải thích
        send(get("/api/banks/" + bank), student.accessToken()).andExpect(status().isOk()).andExpect(jsonPath("$.questions", hasSize(3)))
                .andExpect(content().string(not(containsString("answerKey")))).andExpect(content().string(not(containsString("isCorrect"))))
                .andExpect(content().string(not(containsString("SECRETCLOZE")))).andExpect(content().string(not(containsString("Đối lập"))));

        JsonNode detail = json.readTree(call(get(CMS + "/banks/" + bank), admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[1].answerKey.blanks.b2[0]").value("SECRETCLOZE")).andReturn().getResponse().getContentAsString());
        long right = 0;
        for (JsonNode c : detail.at("/questions/0/choices")) {
            if (c.get("isCorrect").asBoolean()) {
                right = c.get("id").asLong();
            }
        }
        long attempt = idOf(call(post("/api/banks/" + bank + "/attempts"), student, null).andExpect(status().isCreated()));
        call(post("/api/attempts/" + attempt + "/submit"), student, Map.of("answers", List.of(
                Map.of("questionId", mcqId, "answer", Map.of("choiceId", right)),
                Map.of("questionId", clozeId, "answer", Map.of("blanks", Map.of("b1", "  TEA ", "b2", "secretcloze"))),
                Map.of("questionId", buildId, "answer", Map.of("order", List.of("b1", "b2", "b3"))))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(3)).andExpect(jsonPath("$.maxScore").value(3));

        // bộ chưa xuất bản thì học viên không thấy
        call(post(CMS + "/banks/" + bank + "/unpublish"), admin, null).andExpect(status().isOk());
        send(get("/api/banks/" + bank), student.accessToken()).andExpect(status().isNotFound());
    }

    @Test
    void publishedBankBecomesVisibleToStudents() throws Exception {
        var admin = withRole("ADMIN");
        var student = register(newEmail(), PASSWORD);
        long bank = bank(admin, place().topic);
        question(admin, bank, mcq()).andExpect(status().isCreated());
        send(get("/api/banks/" + bank), student.accessToken()).andExpect(status().isNotFound());
        call(post(CMS + "/banks/" + bank + "/publish"), admin, null).andExpect(status().isOk());
        send(get("/api/banks/" + bank), student.accessToken()).andExpect(status().isOk());
    }

    // ---- khóa khi đã có bài nộp ----

    @Test
    void contentIsLockedOnceSubmittedButPromptTagsAndOrderStayEditable() throws Exception {
        var admin = withRole("ADMIN");
        var student = register(newEmail(), PASSWORD);
        long bank = bank(admin, place().topic);
        long mcqId = idOf(question(admin, bank, mcq()));
        long buildId = idOf(question(admin, bank, build()));
        call(post(CMS + "/banks/" + bank + "/publish"), admin, null).andExpect(status().isOk());
        long attempt = idOf(call(post("/api/banks/" + bank + "/attempts"), student, null).andExpect(status().isCreated()));
        call(post("/api/attempts/" + attempt + "/submit"), student, Map.of("answers", List.of())).andExpect(status().isOk());

        // đổi nội dung chấm: 409 has_submissions
        call(patch(CMS + "/questions/" + buildId), admin, Map.of("answerKey", Map.of("order", List.of("b3", "b2", "b1")))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("has_submissions"));
        call(patch(CMS + "/questions/" + buildId), admin, Map.of("payload", Map.of("blocks", List.of(Map.of("id", "b1", "text", "a"),
                Map.of("id", "b2", "text", "b"), Map.of("id", "b3", "text", "c"))))).andExpect(status().isConflict());
        call(patch(CMS + "/questions/" + buildId), admin, Map.of("type", "WRITING", "payload", Map.of("task", "t", "requiredWords", 5), "answerKey", json.nullNode()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("has_submissions"));
        call(patch(CMS + "/questions/" + mcqId), admin, Map.of("choices", List.of(Map.of("label", "A", "content", "Therefore", "isCorrect", true),
                Map.of("label", "B", "content", "Nevertheless", "isCorrect", false)))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("has_submissions"));

        // gửi lại đúng dữ liệu cũ: không tính là đổi
        call(patch(CMS + "/questions/" + buildId), admin, Map.of("answerKey", Map.of("order", List.of("b1", "b2", "b3")), "payload", build().get("payload")))
                .andExpect(status().isOk());
        call(patch(CMS + "/questions/" + mcqId), admin, Map.of("choices", List.of(Map.of("label", "B", "content", "Nevertheless", "isCorrect", true,
                "explanation", "Đối lập"), Map.of("label", "A", "content", "Therefore", "isCorrect", false)))).andExpect(status().isOk());

        // prompt, giải thích, thứ tự, thẻ vẫn sửa được
        call(patch(CMS + "/questions/" + buildId), admin, Map.of("prompt", "Đề mới", "explanation", "Giải thích mới", "sortOrder", 9,
                "grammarElementCodes", List.of("PASSIVE"))).andExpect(status().isOk()).andExpect(jsonPath("$.prompt").value("Đề mới"))
                .andExpect(jsonPath("$.sortOrder").value(9)).andExpect(jsonPath("$.grammarElementCodes[0]").value("PASSIVE"))
                .andExpect(jsonPath("$.answerKey.order[0]").value("b1"));
        call(patch(CMS + "/questions/" + buildId), admin, Map.of("grammarElementCodes", List.of())).andExpect(jsonPath("$.grammarElementCodes", hasSize(0)));
        assertEquals("Đề mới", jdbc.queryForObject("select prompt from questions where id = ?", String.class, buildId));
    }

    @Test
    void patchBeforeAnySubmissionAppliesTheNewContentAndRevalidates() throws Exception {
        var admin = withRole("ADMIN");
        long bank = bank(admin, place().topic);
        long mcqId = idOf(question(admin, bank, mcq()));
        call(patch(CMS + "/questions/" + mcqId), admin, Map.of("choices", List.of(Map.of("label", "A", "content", "x", "isCorrect", true),
                Map.of("label", "B", "content", "y", "isCorrect", false), Map.of("label", "C", "content", "z", "isCorrect", false))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.choices", hasSize(3)));
        assertEquals(3, count("select count(*) from question_choices where question_id = ?", mcqId));
        // PATCH dẫn tới trạng thái sai thì 400 và dữ liệu cũ còn nguyên
        call(patch(CMS + "/questions/" + mcqId), admin, Map.of("choices", List.of(Map.of("label", "A", "content", "x", "isCorrect", true)))).andExpect(status().isBadRequest());
        assertEquals(3, count("select count(*) from question_choices where question_id = ?", mcqId));
        // đổi dạng sang dạng không dùng choices phải xóa choices cùng lúc
        call(patch(CMS + "/questions/" + mcqId), admin, Map.of("type", "WRITING", "payload", Map.of("task", "t", "requiredWords", 5), "choices", List.of()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.type").value("WRITING")).andExpect(jsonPath("$.choices", hasSize(0)));
        assertEquals(0, count("select count(*) from question_choices where question_id = ?", mcqId));
        call(patch(CMS + "/questions/999999999"), admin, Map.of("prompt", "x")).andExpect(status().isNotFound());
    }

    // ---- xóa ----

    @Test
    void deletingQuestionsAndBanksIsSafe() throws Exception {
        var admin = withRole("ADMIN");
        var student = register(newEmail(), PASSWORD);
        var place = place();
        long bank = bank(admin, place.topic);
        var withTag = mcq();
        withTag.put("grammarElementCodes", List.of("PASSIVE"));
        long submitted = idOf(question(admin, bank, withTag));
        call(post(CMS + "/banks/" + bank + "/publish"), admin, null).andExpect(status().isOk());
        long attempt = idOf(call(post("/api/banks/" + bank + "/attempts"), student, null).andExpect(status().isCreated()));
        long right = jdbc.queryForObject("select id from question_choices where question_id = ? and is_correct = true", Long.class, submitted);
        call(post("/api/attempts/" + attempt + "/submit"), student, Map.of("answers",
                List.of(Map.of("questionId", submitted, "answer", Map.of("choiceId", right))))).andExpect(status().isOk());

        long plain = idOf(question(admin, bank, withTag)); // thêm sau khi nộp nên chưa có bài trả lời
        // câu đã có bài nộp: 409 in_use và KHÔNG mất lựa chọn, thẻ (rollback trọn vẹn)
        call(delete(CMS + "/questions/" + submitted), admin, null).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("in_use"));
        assertEquals(2, count("select count(*) from question_choices where question_id = ?", submitted));
        assertEquals(1, count("select count(*) from question_grammar_tags where question_id = ?", submitted));
        call(get(CMS + "/banks/" + bank), admin, null).andExpect(status().isOk()).andExpect(jsonPath("$.questions", hasSize(2)));

        // câu chưa ai nộp: xóa luôn lựa chọn và thẻ
        call(delete(CMS + "/questions/" + plain), admin, null).andExpect(status().isNoContent());
        assertEquals(0, count("select count(*) from question_choices where question_id = ?", plain));
        assertEquals(0, count("select count(*) from question_grammar_tags where question_id = ?", plain));
        call(delete(CMS + "/questions/" + plain), admin, null).andExpect(status().isNotFound());

        // bộ còn câu: in_use; bộ rỗng nhưng có lượt làm bài: khóa ngoại chặn, vẫn 409
        call(delete(CMS + "/banks/" + bank), admin, null).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("in_use"));
        long emptyWithAttempt = bank(admin, place.topic);
        jdbc.update("insert into student_exercise_attempts (user_id, bank_id) values (?, ?)", student.id(), emptyWithAttempt);
        call(delete(CMS + "/banks/" + emptyWithAttempt), admin, null).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("in_use"));
        assertEquals(1, count("select count(*) from question_banks where id = ?", emptyWithAttempt));

        long empty = bank(admin, place.topic);
        call(delete(CMS + "/banks/" + empty), admin, null).andExpect(status().isNoContent());
        call(get(CMS + "/banks/" + empty), admin, null).andExpect(status().isNotFound());
    }

    @Test
    void bankDetailListsQuestionsBySortOrderWithAnswerKeysChoicesAndTags() throws Exception {
        var admin = withRole("ADMIN");
        long bank = bank(admin, place().topic);
        var second = build();
        second.put("sortOrder", 2);
        var first = mcq();
        first.put("sortOrder", 1);
        first.put("grammarElementCodes", List.of("INVERSION"));
        question(admin, bank, second).andExpect(status().isCreated());
        question(admin, bank, first).andExpect(status().isCreated());
        call(get(CMS + "/banks/" + bank), admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.bank.questionCount").value(2)).andExpect(jsonPath("$.questions[0].type").value("CONNECTIVE_MCQ"))
                .andExpect(jsonPath("$.questions[0].grammarElementCodes[0]").value("INVERSION"))
                .andExpect(jsonPath("$.questions[0].choices[1].isCorrect").value(true))
                .andExpect(jsonPath("$.questions[1].type").value("GRAMMAR_BUILD")).andExpect(jsonPath("$.questions[1].answerKey.order", hasSize(3)));
    }
}
