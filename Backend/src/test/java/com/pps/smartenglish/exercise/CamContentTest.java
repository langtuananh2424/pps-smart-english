package com.pps.smartenglish.exercise;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.support.ApiTestBase;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** FR-AI-06, FR-AI-07, D-28: gợi ý và hỏi đáp soạn sẵn của Cam: CMS, hiển thị cho học viên, giới hạn theo mode. */
class CamContentTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";
    private static final String CMS = "/api/admin/cms";

    private static MockHttpServletRequestBuilder post(String url) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url);
    }

    private record Place(long course, long bank, long q1, long q2) {}

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    /** Bộ câu hỏi 2 câu; freeTrial = học viên mới vào được không cần mua. */
    private Place place(String mode, boolean freeTrial) {
        long c = insert("courses", Map.of("slug", "cam-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "L", "status", "PUBLISHED"));
        long t = insert("topics", Map.of("level_id", lv, "title", "T", "is_free_trial", freeTrial, "status", "PUBLISHED"));
        long b = insert("question_banks", new HashMap<>(Map.of("topic_id", t, "title", "Bộ", "is_trial", freeTrial,
                "status", "PUBLISHED", "mode", mode)));
        return new Place(c, b, question(b, 1), question(b, 2));
    }

    private long question(long bank, int order) {
        return insert("questions", new HashMap<>(Map.of("bank_id", bank, "type", "WRITING", "prompt", "Đề " + order,
                "sort_order", order)));
    }

    /** Bài thi khóa: gắn vào khóa, học viên đã mua, ngưỡng mở 0%. */
    private void enrollInExam(Account u, Place p) {
        jdbc.update("update courses set exam_bank_id = ?, exam_unlock_percent = 0 where id = ?", p.bank, p.course);
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', ?, ?)",
                u.id(), p.course, java.time.OffsetDateTime.parse("2020-01-01T00:00:00Z"), java.time.OffsetDateTime.parse("2099-01-01T00:00:00Z"));
    }

    private Account withRole(String role) throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = ?", u.id(), role);
        return login(u.email(), PASSWORD);
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

    private long hint(Account a, long q, String content, String detail, Integer order) throws Exception {
        var body = new HashMap<String, Object>();
        body.put("content", content);
        body.put("detail", detail);
        body.put("sortOrder", order);
        return idOf(call(post(CMS + "/questions/" + q + "/hints"), a, body).andExpect(status().isCreated()));
    }

    private long faq(Account a, long bank, String question, String answer, Integer order) throws Exception {
        var body = new HashMap<String, Object>();
        body.put("question", question);
        body.put("answer", answer);
        body.put("sortOrder", order);
        return idOf(call(post(CMS + "/banks/" + bank + "/faqs"), a, body).andExpect(status().isCreated()));
    }

    // ---- CMS: gợi ý ----

    @Test
    void cmsHintCrudSortAndAudit() throws Exception {
        var p = place("PRACTICE", true);
        var editor = withRole("EDITOR");
        long a = hint(editor, p.q1, "  Xem thì hiện tại  ", "Dài\r\nhơn", 2);
        long b = hint(editor, p.q1, "Gợi ý B", null, 1);
        call(get(CMS + "/questions/" + p.q1 + "/hints"), editor, null).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(b)).andExpect(jsonPath("$[1].id").value(a))
                .andExpect(jsonPath("$[1].content").value("Xem thì hiện tại")).andExpect(jsonPath("$[1].detail").value("Dài\nhơn"));

        call(patch(CMS + "/hints/" + a), editor, Map.of("sortOrder", 0, "detail", "")).andExpect(status().isOk())
                .andExpect(jsonPath("$.detail").doesNotExist());
        call(get(CMS + "/questions/" + p.q1 + "/hints"), editor, null).andExpect(jsonPath("$[0].id").value(a));

        call(delete(CMS + "/hints/" + b), editor, null).andExpect(status().isNoContent());
        call(delete(CMS + "/hints/" + b), editor, null).andExpect(status().isNotFound());
        assertEquals(1, count("select count(*) from audit_logs where target_type = 'QUESTION_HINT' and action = 'CREATE' and target_id = ?", String.valueOf(a)));
        assertEquals(1, count("select count(*) from audit_logs where target_type = 'QUESTION_HINT' and action = 'UPDATE' and target_id = ?", String.valueOf(a)));
        assertEquals(1, count("select count(*) from audit_logs where target_type = 'QUESTION_HINT' and action = 'DELETE' and target_id = ?", String.valueOf(b)));
    }

    @Test
    void hintValidation() throws Exception {
        var p = place("PRACTICE", true);
        var admin = withRole("ADMIN");
        call(post(CMS + "/questions/" + p.q1 + "/hints"), admin, Map.of("content", "   ")).andExpect(status().isBadRequest());
        call(post(CMS + "/questions/" + p.q1 + "/hints"), admin, Map.of("detail", "x")).andExpect(status().isBadRequest());
        call(post(CMS + "/questions/" + p.q1 + "/hints"), admin, Map.of("content", "x".repeat(501))).andExpect(status().isBadRequest());
        call(post(CMS + "/questions/" + p.q1 + "/hints"), admin, Map.of("content", "ok", "detail", "x".repeat(5001)))
                .andExpect(status().isBadRequest());
        call(post(CMS + "/questions/" + p.q1 + "/hints"), admin, Map.of("content", "<script>alert(1)</script>"))
                .andExpect(status().isBadRequest());
        call(post(CMS + "/questions/999999/hints"), admin, Map.of("content", "ok")).andExpect(status().isNotFound());
        for (int i = 0; i < 10; i++) {
            hint(admin, p.q1, "g" + i, null, null);
        }
        call(post(CMS + "/questions/" + p.q1 + "/hints"), admin, Map.of("content", "thứ 11")).andExpect(status().isBadRequest());
        hint(admin, p.q2, "câu khác vẫn được", null, null);
        long id = hint(admin, p.q2, "để sửa", null, null);
        call(patch(CMS + "/hints/" + id), admin, Map.of("content", " ")).andExpect(status().isBadRequest());
    }

    // ---- CMS: hỏi đáp ----

    @Test
    void cmsFaqCrudValidateAndLimit() throws Exception {
        var p = place("PRACTICE", true);
        var admin = withRole("ADMIN");
        long a = faq(admin, p.bank, "Khi nào dùng thì hiện tại hoàn thành?", "Khi nói về kết quả.", 1);
        long b = faq(admin, p.bank, "Câu hai", "Đáp hai", 0);
        call(get(CMS + "/banks/" + p.bank + "/faqs"), admin, null).andExpect(jsonPath("$[0].id").value(b))
                .andExpect(jsonPath("$[1].id").value(a));
        call(patch(CMS + "/faqs/" + a), admin, Map.of("answer", "Mới", "sortOrder", -1)).andExpect(status().isOk());
        call(get(CMS + "/banks/" + p.bank + "/faqs"), admin, null).andExpect(jsonPath("$[0].answer").value("Mới"));
        call(post(CMS + "/banks/" + p.bank + "/faqs"), admin, Map.of("question", "", "answer", "x")).andExpect(status().isBadRequest());
        call(post(CMS + "/banks/" + p.bank + "/faqs"), admin, Map.of("question", "x".repeat(301), "answer", "x"))
                .andExpect(status().isBadRequest());
        call(post(CMS + "/banks/" + p.bank + "/faqs"), admin, Map.of("question", "x", "answer", "y".repeat(5001)))
                .andExpect(status().isBadRequest());
        call(patch(CMS + "/faqs/" + a), admin, Map.of("answer", "  ")).andExpect(status().isBadRequest());
        call(delete(CMS + "/faqs/" + b), admin, null).andExpect(status().isNoContent());
        for (int i = 0; i < 29; i++) {
            faq(admin, p.bank, "q" + i, "a", null);
        }
        call(post(CMS + "/banks/" + p.bank + "/faqs"), admin, Map.of("question", "vượt", "answer", "x")).andExpect(status().isBadRequest());
        assertEquals(1, count("select count(*) from audit_logs where target_type = 'BANK_FAQ' and action = 'DELETE' and target_id = ?", String.valueOf(b)));
    }

    @Test
    void cmsRequiresContentPermission() throws Exception {
        var p = place("PRACTICE", true);
        var student = register(newEmail(), PASSWORD);
        var s = login(student.email(), PASSWORD);
        call(get(CMS + "/questions/" + p.q1 + "/hints"), s, null).andExpect(status().isForbidden());
        call(post(CMS + "/questions/" + p.q1 + "/hints"), s, Map.of("content", "x")).andExpect(status().isForbidden());
        call(get(CMS + "/banks/" + p.bank + "/faqs"), s, null).andExpect(status().isForbidden());
        call(delete(CMS + "/faqs/1"), s, null).andExpect(status().isForbidden());
        mvc.perform(get(CMS + "/banks/" + p.bank + "/faqs")).andExpect(status().isUnauthorized());
    }

    // ---- học viên ----

    @Test
    void studentSeesHintsInOrderOnPracticeOnly() throws Exception {
        var editor = withRole("EDITOR");
        var practice = place("PRACTICE", true);
        hint(editor, practice.q1, "thứ hai", "chi tiết", 2);
        hint(editor, practice.q1, "thứ nhất", null, 1);
        var exam = place("EXAM", false);
        hint(editor, exam.q1, "không hiện", null, 1);
        var student = register(newEmail(), PASSWORD);
        enrollInExam(student, exam);
        send(post("/api/banks/" + exam.bank + "/attempts").contentType(MediaType.APPLICATION_JSON).content("{}"), student.accessToken())
                .andExpect(status().isCreated());

        send(get("/api/banks/" + practice.bank), student.accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].hints", hasSize(2))).andExpect(jsonPath("$.questions[0].hints[0].content").value("thứ nhất"))
                .andExpect(jsonPath("$.questions[0].hints[1].detail").value("chi tiết"))
                .andExpect(jsonPath("$.questions[1].hints", hasSize(0)));
        send(get("/api/banks/" + exam.bank), student.accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].hints", hasSize(0)));
    }

    @Test
    void studentFaqsFollowBankAccessAndModeAndOrder() throws Exception {
        var editor = withRole("EDITOR");
        var open = place("PRACTICE", true);
        faq(editor, open.bank, "Hai", "Đáp hai", 2);
        faq(editor, open.bank, "Một", "Đáp một", 1);
        var locked = place("PRACTICE", false);
        faq(editor, locked.bank, "Q", "A", 1);
        var exam = place("EXAM", false);
        faq(editor, exam.bank, "Q", "A", 1);
        var student = register(newEmail(), PASSWORD);
        enrollInExam(student, exam);
        String t = student.accessToken();

        send(get("/api/banks/" + open.bank + "/faqs"), t).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].question").value("Một")).andExpect(jsonPath("$[1].answer").value("Đáp hai"));
        send(get("/api/banks/" + locked.bank + "/faqs"), t).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("bank_locked"));
        send(get("/api/banks/" + exam.bank + "/faqs"), t).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("assistant_unavailable"));
        send(get("/api/banks/999999/faqs"), t).andExpect(status().isNotFound());
        mvc.perform(get("/api/banks/" + open.bank + "/faqs")).andExpect(status().isUnauthorized());
    }

    @Test
    void deletingQuestionOrBankRemovesItsCamContent() throws Exception {
        var admin = withRole("ADMIN");
        var p = place("PRACTICE", true);
        hint(admin, p.q1, "g", null, null);
        call(delete(CMS + "/questions/" + p.q1), admin, null).andExpect(status().isNoContent());
        assertEquals(0, count("select count(*) from question_hints where question_id = ?", p.q1));

        long other = place("PRACTICE", true).bank;
        jdbc.update("delete from questions where bank_id = ?", other);
        faq(admin, other, "q", "a", null);
        call(delete(CMS + "/banks/" + other), admin, null).andExpect(status().isNoContent());
        assertEquals(0, count("select count(*) from bank_faqs where bank_id = ?", other));
    }
}
