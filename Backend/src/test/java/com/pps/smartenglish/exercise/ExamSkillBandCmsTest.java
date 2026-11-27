package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.support.ApiTestBase;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** D-164: skill của câu bộ thi khóa do CMS chọn; bảng band và xuất bản đòi mọi câu tự chấm có skill; lượt thi ra band. */
class ExamSkillBandCmsTest extends ApiTestBase {

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

    private ResultActions call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req, Account u, Object body) throws Exception {
        return send(req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)), u.accessToken());
    }

    private long id(ResultActions r) throws Exception {
        return json.readTree(r.andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private long bank(Account admin, long topic, String mode) throws Exception {
        return id(call(MockMvcRequestBuilders.post(CMS + "/topics/" + topic + "/banks"), admin, Map.of("title", "Thi " + UUID.randomUUID(), "mode", mode)));
    }

    /** Câu trắc nghiệm đáp án đúng là A; skill null thì không gửi. */
    private ResultActions question(Account admin, long bank, String skill) throws Exception {
        var body = new java.util.HashMap<String, Object>(Map.of("type", "CONNECTIVE_MCQ", "prompt", "Câu " + UUID.randomUUID(),
                "choices", List.of(Map.of("label", "A", "content", "x", "isCorrect", true), Map.of("label", "B", "content", "y", "isCorrect", false))));
        if (skill != null) {
            body.put("skill", skill);
        }
        return call(MockMvcRequestBuilders.post(CMS + "/banks/" + bank + "/questions"), admin, body);
    }

    private ResultActions rules(Account admin, long bank) throws Exception {
        return call(put(CMS + "/banks/" + bank + "/band-rules"), admin, Map.of("rules", List.of(
                Map.of("skill", "READING", "minPercent", 0, "band", 4.0), Map.of("skill", "READING", "minPercent", 100, "band", 6.0),
                Map.of("skill", "LISTENING", "minPercent", 0, "band", 4.0), Map.of("skill", "LISTENING", "minPercent", 100, "band", 5.0))));
    }

    private long choiceId(long q, String label) {
        return jdbc.queryForObject("select id from question_choices where question_id = ? and label = ?", Long.class, q, label);
    }

    @Test
    void examBankSkillIsRequiredForBandsAndGivesBandsOnAttempt() throws Exception {
        var admin = admin();
        long course = insert("courses", Map.of("slug", "esb-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", course, "code", "A", "title", "Level A", "status", "PUBLISHED"));
        long topic = insert("topics", Map.of("level_id", lv, "title", "Topic", "status", "PUBLISHED"));
        long practice = bank(admin, topic, "PRACTICE");
        question(admin, practice, "READING").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.skill").exists());

        long exam = bank(admin, topic, "EXAM");
        long reading = id(question(admin, exam, "READING"));
        long noSkill = id(question(admin, exam, null)); // skill tùy chọn khi chưa có bảng band
        var missing = rules(admin, exam).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.questionIds[0]").value(noSkill));
        assertEquals(0, count("select count(*) from exam_band_rules where bank_id = ?", exam));
        call(patch(CMS + "/questions/" + noSkill), admin, Map.of("skill", "LISTENING")).andExpect(status().isOk());
        rules(admin, exam).andExpect(status().isOk());

        question(admin, exam, null).andExpect(status().isBadRequest()); // D-164: đã có bảng thì thêm câu tự chấm phải có kỹ năng
        send(MockMvcRequestBuilders.post(CMS + "/banks/" + exam + "/publish"), admin.accessToken()).andExpect(status().isOk());

        // học viên thi: đúng câu Reading đầu, sai hai câu còn lại
        jdbc.update("update courses set exam_bank_id = ?, exam_unlock_percent = 0 where id = ?", exam, course);
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at) values (?, ?, 'ACTIVE', ?)",
                u.id(), course, OffsetDateTime.parse("2020-01-01T00:00:00Z"));
        long attempt = id(call(MockMvcRequestBuilders.post("/api/banks/" + exam + "/attempts"), u, Map.of()));
        call(MockMvcRequestBuilders.post("/api/attempts/" + attempt + "/submit"), u, Map.of("answers", List.of(
                Map.of("questionId", reading, "answer", Map.of("choiceId", choiceId(reading, "A"))),
                Map.of("questionId", noSkill, "answer", Map.of("choiceId", choiceId(noSkill, "B")))))).andExpect(status().isOk());
        send(get("/api/courses/" + course + "/exam"), u.accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.lastResult.bands.skills.READING.band").value(6.0)) // 1/1 câu đúng = 100%
                .andExpect(jsonPath("$.lastResult.bands.skills.LISTENING.band").value(4.0));
    }
}
