package com.pps.smartenglish.exercise.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.ai.AiTestSupport;
import com.pps.smartenglish.ai.dto.SpeakingGrade;
import com.pps.smartenglish.ai.dto.WritingGrade;
import com.pps.smartenglish.exercise.repository.StudentAttemptRepository;
import com.pps.smartenglish.support.FakeStorageConfig.FakeStorage;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** D-167 (FR-EXAM-03): % điểm từng kỹ năng (kể cả Viết/Nói), band theo bảng % chỉnh được, band tổng làm tròn IELTS, band chờ khi còn câu đang chấm. */
class ExamPercentBandTest extends AiTestSupport {

    private static final String CMS = "/api/admin/cms";
    private static final String ESSAY = "I agree with online learning because it is flexible. However, students need discipline.";

    @Autowired ExamBands examBands;
    @Autowired StudentAttemptRepository attempts;
    @Autowired FakeStorage storage;

    private record Exam(long course, long bank, List<Long> reading, List<Long> listening, Long writing, Long speaking) {}

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

    private long mcq(Account admin, long bank, String skill) throws Exception {
        return id(call(MockMvcRequestBuilders.post(CMS + "/banks/" + bank + "/questions"), admin, Map.of("type", "CONNECTIVE_MCQ",
                "prompt", "Câu " + UUID.randomUUID(), "skill", skill, "choices",
                List.of(Map.of("label", "A", "content", "x", "isCorrect", true), Map.of("label", "B", "content", "y", "isCorrect", false)))));
    }

    private static Map<String, Object> rule(String skill, int minPercent, double band) {
        return Map.of("skill", skill, "minPercent", minPercent, "band", band);
    }

    /** Bộ thi khóa dựng bằng CMS: nR câu Đọc, nL câu Nghe, tùy chọn một câu Viết và một câu Nói (không gửi skill). */
    private Exam exam(int nR, int nL, boolean writingSpeaking, List<Map<String, Object>> rules) throws Exception {
        var admin = admin();
        long course = insert("courses", Map.of("slug", "epb-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", course, "code", "A", "title", "Level A", "status", "PUBLISHED"));
        long topic = insert("topics", Map.of("level_id", lv, "title", "Topic", "status", "PUBLISHED"));
        long bank = id(call(MockMvcRequestBuilders.post(CMS + "/topics/" + topic + "/banks"), admin, Map.of("title", "Thi " + UUID.randomUUID(), "mode", "EXAM")));
        List<Long> r = new ArrayList<>();
        List<Long> l = new ArrayList<>();
        for (int i = 0; i < nR; i++) r.add(mcq(admin, bank, "READING"));
        for (int i = 0; i < nL; i++) l.add(mcq(admin, bank, "LISTENING"));
        Long w = null;
        Long s = null;
        if (writingSpeaking) {
            w = id(call(MockMvcRequestBuilders.post(CMS + "/banks/" + bank + "/questions"), admin, Map.of("type", "WRITING", "prompt", "Viết",
                    "payload", Map.of("task", "Do you agree?", "requiredWords", 100))));
            s = id(call(MockMvcRequestBuilders.post(CMS + "/banks/" + bank + "/questions"), admin, Map.of("type", "SPEAKING", "prompt", "Nói",
                    "payload", Map.of("part", 2, "prompt", "Describe a trip.", "cueCard", List.of("where")))));
        }
        call(MockMvcRequestBuilders.put(CMS + "/banks/" + bank + "/band-rules"), admin, Map.of("rules", rules)).andExpect(status().isOk());
        send(MockMvcRequestBuilders.post(CMS + "/banks/" + bank + "/publish"), admin.accessToken()).andExpect(status().isOk());
        jdbc.update("update courses set exam_bank_id = ?, exam_unlock_percent = 0 where id = ?", bank, course);
        return new Exam(course, bank, r, l, w, s);
    }

    private Account learner(Exam e) throws Exception {
        var u = student(10);
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at) values (?, ?, 'ACTIVE', ?)",
                u.id(), e.course(), OffsetDateTime.parse("2020-01-01T00:00:00Z"));
        return u;
    }

    private long start(Account u, Exam e) throws Exception {
        return idOf(postJson("/api/banks/" + e.bank() + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
    }

    private long choice(long q, String label) {
        return jdbc.queryForObject("select id from question_choices where question_id = ? and label = ?", Long.class, q, label);
    }

    /** Nộp: đúng nR câu Đọc đầu và nL câu Nghe đầu, còn lại chọn sai. */
    private ResultActions submit(Account u, Exam e, long attempt, int nR, int nL) throws Exception {
        List<Map<String, Object>> answers = new ArrayList<>();
        for (int i = 0; i < e.reading().size(); i++) answers.add(answer(e.reading().get(i), i < nR));
        for (int i = 0; i < e.listening().size(); i++) answers.add(answer(e.listening().get(i), i < nL));
        return postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers", answers)).andExpect(status().isOk());
    }

    private Map<String, Object> answer(long q, boolean right) {
        return Map.of("questionId", q, "answer", Map.of("choiceId", choice(q, right ? "A" : "B")));
    }

    private JsonNode bands(Account u, long attempt) throws Exception {
        return json.readTree(getJson("/api/attempts/" + attempt, u).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("bands");
    }

    private static byte[] wav(double seconds) {
        int data = (int) (seconds * 32000);
        var b = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + data).put("WAVE".getBytes()).put("fmt ".getBytes()).putInt(16).putShort((short) 1)
                .putShort((short) 1).putInt(16000).putInt(32000).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes()).putInt(data);
        return b.array();
    }

    private void gradeWriting(Account u, Exam e, long attempt) throws Exception {
        guard.resetRateLimit(u.id());
        long session = idOf(postJson("/api/writing/sessions", u, Map.of("questionId", e.writing(), "attemptId", attempt)).andExpect(status().isCreated()), "sessionId");
        postJson("/api/writing/sessions/" + session + "/submit", u, Map.of("finalText", ESSAY), UUID.randomUUID().toString()).andExpect(status().isAccepted());
    }

    private void gradeSpeaking(Account u, Exam e, long attempt) throws Exception {
        guard.resetRateLimit(u.id());
        long id = idOf(postJson("/api/speaking/submissions", u, Map.of("questionId", e.speaking(), "attemptId", attempt)).andExpect(status().isCreated()), "submissionId");
        storage.put(jdbc.queryForObject("select object_key from speaking_submissions where id = ?", String.class, id), wav(20));
        guard.resetRateLimit(u.id());
        postJson("/api/speaking/submissions/" + id + "/complete", u, Map.of()).andExpect(status().isAccepted());
    }

    private static WritingGrade grade(int pct) {
        return new WritingGrade(pct, pct, pct, pct, List.of(), "fixed", new WritingGrade.Connectives(2, 1, 2, List.of(), "ok"), List.of(), null, "fb");
    }

    @Test
    void percentPerSkillBandsFromPercentTableAndOverallExcludesSkillsWithoutTable() throws Exception {
        var e = exam(4, 4, true, List.of(
                rule("READING", 0, 4.0), rule("READING", 50, 5.0), rule("READING", 75, 6.0),
                rule("LISTENING", 0, 4.0), rule("LISTENING", 50, 5.0), rule("LISTENING", 75, 6.5),
                rule("WRITING", 0, 3.0), rule("WRITING", 60, 5.0), rule("WRITING", 90, 7.0)));
        var u = learner(e);
        long attempt = start(u, e);
        gradeWriting(u, e, attempt);
        gradeSpeaking(u, e, attempt);
        submit(u, e, attempt, 3, 2);
        fake.override(WritingGrade.class, grade(80));
        fake.override(SpeakingGrade.class, new SpeakingGrade(7, 6.5, "Tốt.", List.of()));
        assertEquals(2, runner.runDueNow());

        var b = bands(u, attempt);
        assertEquals(75.0, b.at("/skills/READING/percent").asDouble());
        assertEquals(6.0, b.at("/skills/READING/band").asDouble());
        assertEquals(50.0, b.at("/skills/LISTENING/percent").asDouble());
        assertEquals(5.0, b.at("/skills/LISTENING/band").asDouble());
        assertEquals(80.0, b.at("/skills/WRITING/percent").asDouble(), 0.01);
        assertEquals(5.0, b.at("/skills/WRITING/band").asDouble());
        // Nói: có % từ credit đã lưu nhưng không có bảng nên không có band
        BigDecimal credit = jdbc.queryForObject("select credit from student_answers where attempt_id = ? and question_id = ?", BigDecimal.class, attempt, e.speaking());
        assertNotNull(credit);
        assertEquals(credit.doubleValue() * 100, b.at("/skills/SPEAKING/percent").asDouble(), 0.01);
        assertTrue(b.at("/skills/SPEAKING/band").isNull());
        assertEquals(5.5, b.at("/overall").asDouble()); // (6.0 + 5.0 + 5.0) / 3 = 5.33 -> 5.5, Nói không có bảng nên không tính
        assertFalse(b.at("/gradingPending").asBoolean());
    }

    @Test
    void overallRoundsQuarterUpToHalfAndThreeQuartersUpToWhole() throws Exception {
        var table = List.of(rule("READING", 0, 4.0), rule("READING", 50, 6.0), rule("READING", 75, 6.5), rule("READING", 100, 7.0),
                rule("LISTENING", 0, 4.0), rule("LISTENING", 50, 6.0), rule("LISTENING", 75, 6.5), rule("LISTENING", 100, 7.0));
        var e = exam(4, 4, false, table);
        var x = learner(e);
        long ax = start(x, e);
        submit(x, e, ax, 3, 2); // 6.5 và 6.0 -> 6.25 -> 6.5
        assertEquals(6.5, bands(x, ax).path("overall").asDouble());
        var y = learner(e);
        long ay = start(y, e);
        submit(y, e, ay, 4, 3); // 7.0 và 6.5 -> 6.75 -> 7.0
        assertEquals(7.0, bands(y, ay).path("overall").asDouble());
    }

    @Test
    void bandAndOverallWaitWhileWritingIsStillGrading() throws Exception {
        var e = exam(2, 0, true, List.of(rule("READING", 0, 4.0), rule("READING", 100, 6.0), rule("WRITING", 0, 3.0), rule("WRITING", 50, 5.0)));
        var u = learner(e);
        long attempt = start(u, e);
        gradeWriting(u, e, attempt);
        submit(u, e, attempt, 2, 0).andExpect(jsonPath("$.score").doesNotExist()); // D-147: API ẩn điểm khi còn câu chấm

        var pending = examBands.of(attempts.findById(attempt).orElseThrow());
        assertEquals(100.0, pending.skills().get("READING").percent().doubleValue());
        assertEquals(6.0, pending.skills().get("READING").band().doubleValue());
        assertNull(pending.skills().get("WRITING").band());
        assertNull(pending.overall());
        assertTrue(pending.gradingPending());

        fake.override(WritingGrade.class, grade(60));
        assertEquals(1, runner.runDueNow());
        var done = examBands.of(attempts.findById(attempt).orElseThrow());
        assertEquals(5.0, done.skills().get("WRITING").band().doubleValue());
        assertEquals(5.5, done.overall().doubleValue()); // (6.0 + 5.0) / 2 = 5.5
        assertFalse(done.gradingPending());
    }

    @Test
    void writingAndSpeakingQuestionsTakeSkillFromTypeAndStillRejectExplicitSkill() throws Exception {
        var e = exam(1, 0, true, List.of(rule("READING", 0, 4.0)));
        assertEquals(0, count("select count(*) from questions where bank_id = ? and skill is not null and type in ('WRITING', 'SPEAKING')", e.bank()));
        call(MockMvcRequestBuilders.post(CMS + "/banks/" + e.bank() + "/questions"), admin(), Map.of("type", "WRITING", "prompt", "Viết",
                "skill", "READING", "payload", Map.of("task", "Do you agree?", "requiredWords", 100))).andExpect(status().isBadRequest());
    }

    @Test
    void cmsRulesAcceptWritingSpeakingAndRejectOutOfRangePercentAndDuplicates() throws Exception {
        var admin = admin();
        var e = exam(1, 0, false, List.of(rule("READING", 0, 4.0)));
        var url = CMS + "/banks/" + e.bank() + "/band-rules";
        call(MockMvcRequestBuilders.put(url), admin, Map.of("rules", List.of(rule("WRITING", 0, 3.0), rule("SPEAKING", 50, 5.0)))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].skill").value("SPEAKING")).andExpect(jsonPath("$[0].minPercent").value(50));
        call(MockMvcRequestBuilders.put(url), admin, Map.of("rules", List.of(rule("READING", 101, 4.0)))).andExpect(status().isBadRequest());
        call(MockMvcRequestBuilders.put(url), admin, Map.of("rules", List.of(rule("READING", -1, 4.0)))).andExpect(status().isBadRequest());
        call(MockMvcRequestBuilders.put(url), admin, Map.of("rules", List.of(rule("READING", 50, 4.0), rule("READING", 50, 5.0)))).andExpect(status().isBadRequest());
        assertEquals(2, count("select count(*) from exam_band_rules where bank_id = ?", e.bank())); // lỗi không đổi bảng cũ
    }
}
