package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.ai.AiTestSupport;
import com.pps.smartenglish.ai.speaking.FakeSpeakingEngineClient;
import com.pps.smartenglish.support.FakeStorageConfig.FakeStorage;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Tester PR #88: câu Viết/Nói của bài giao (assignment_id có, attempt_id null) vào hồ sơ đúng người, không đếm đôi;
 * endpoint giáo viên giữ quyền (D-139, NFR-SEC-05); câu bỏ trống trong bộ câu; số lượng lớn phiên độc lập.
 */
class LearningProfileAssignmentSessionsTesterTest extends AiTestSupport {

    private static final String ESSAY = "I agree with online learning because it is flexible. However, students need discipline.";

    @Autowired FakeStorage storage;
    @Autowired FakeSpeakingEngineClient engine;

    private record Fx(long course, long cls, long writing, long speaking, Account teacher) {}

    @BeforeEach
    void reset() {
        engine.reset();
        storage.objects.clear();
    }

    private Account teacherOf(long cls) throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = 'TEACHER'", u.id());
        jdbc.update("insert into class_staff (class_id, user_id, role, assigned_from) values (?, ?, 'TEACHER', ?)", cls, u.id(), LocalDate.now().minusDays(10));
        return login(u.email(), PASSWORD);
    }

    private long newClass(long course) {
        return insert("classes", Map.of("course_id", course, "name", "Lớp " + UUID.randomUUID().toString().substring(0, 4),
                "starts_on", LocalDate.now().minusDays(10), "ends_on", LocalDate.now().plusDays(60), "mode", "ONLINE", "capacity", 20));
    }

    private Fx fixture() throws Exception {
        long c = insert("courses", Map.of("slug", "lp-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED",
                "delivery_mode", "CLASS_BASED", "sequential", false));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED", "sort_order", 0));
        long t = insert("topics", Map.of("level_id", lv, "title", "Unit A", "is_free_trial", false, "status", "PUBLISHED", "sort_order", 0));
        long bank = insert("question_banks", Map.of("topic_id", t, "title", "Bộ", "is_trial", false, "status", "PUBLISHED", "mode", "PRACTICE"));
        long w = writingQuestion(bank);
        long sp = question(bank, "SPEAKING", 2, "Describe a trip.", "{\"part\":1,\"prompt\":\"Describe a trip.\"}", "{}");
        long cls = newClass(c);
        return new Fx(c, cls, w, sp, teacherOf(cls));
    }

    private Account member(Fx f, long cls) throws Exception {
        var s = student(3);
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', TIMESTAMP '2020-01-01 00:00:00', TIMESTAMP '2099-01-01 00:00:00')",
                s.id(), f.course());
        jdbc.update("insert into class_members (class_id, course_id, user_id, status, live_key, joined_at) values (?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP)",
                cls, f.course(), s.id(), f.course() + ":" + s.id());
        return s;
    }

    private long assign(Fx f, long q) throws Exception {
        var m = Map.of("questionId", q, "dueAt", Instant.now().plus(48, ChronoUnit.HOURS).toString());
        return idOf(postJson("/api/classes/" + f.cls() + "/assignments", f.teacher(), m).andExpect(status().isCreated()), "id");
    }

    private long submitWriting(Fx f, Account s, long asg) throws Exception {
        guard.resetRateLimit(s.id());
        var body = new HashMap<String, Object>();
        body.put("questionId", f.writing());
        body.put("assignmentId", asg);
        long session = idOf(postJson("/api/writing/sessions", s, body).andExpect(status().isCreated()), "sessionId");
        guard.resetRateLimit(s.id());
        postJson("/api/writing/sessions/" + session + "/submit", s, Map.of("finalText", ESSAY), UUID.randomUUID().toString()).andExpect(status().isAccepted());
        return session;
    }

    private long submitSpeaking(Fx f, Account s, long asg) throws Exception {
        guard.resetRateLimit(s.id());
        long id = idOf(postJson("/api/speaking/submissions", s, Map.of("questionId", f.speaking(), "assignmentId", asg))
                .andExpect(status().isCreated()), "submissionId");
        storage.put(jdbc.queryForObject("select object_key from speaking_submissions where id = ?", String.class, id), wav(20));
        guard.resetRateLimit(s.id());
        postJson("/api/speaking/submissions/" + id + "/complete", s, Map.of()).andExpect(status().isAccepted());
        return id;
    }

    private static byte[] wav(double seconds) {
        int rate = 32000;
        int data = (int) (seconds * rate);
        var b = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + data).put("WAVE".getBytes()).put("fmt ".getBytes()).putInt(16).putShort((short) 1)
                .putShort((short) 1).putInt(rate / 2).putInt(rate).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes()).putInt(data);
        return b.array();
    }

    private static JsonNode skillOf(JsonNode profile, String key) {
        for (JsonNode n : profile.get("bySkill")) {
            if (key.equals(n.get("key").asText())) {
                return n;
            }
        }
        return null;
    }

    private JsonNode profile(String url, Account who) throws Exception {
        return json.readTree(getJson(url, who).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    /** Bài giao một câu Viết và một câu Nói: vào hồ sơ người nộp đúng một lần; bạn cùng lớp không lẫn; giáo viên lớp thấy cùng số liệu. */
    @Test
    void assignmentSessionsCountOnceForOwnerOnlyAndTeacherSeesSame() throws Exception {
        var f = fixture();
        var s = member(f, f.cls());
        var mate = member(f, f.cls());
        long asgW = assign(f, f.writing());
        long asgS = assign(f, f.speaking());
        submitWriting(f, s, asgW);
        long sub = submitSpeaking(f, s, asgS);
        for (int i = 0; i < 4 && runner.runDueNow() > 0; i++) { }
        assertEquals("GRADED", jdbc.queryForObject("select status from speaking_submissions where id = ?", String.class, sub));
        double overall = json.readTree(jdbc.queryForObject("select cast(result as varchar) from speaking_submissions where id = ?", String.class, sub))
                .path("overall").asDouble();

        var mine = profile("/api/me/learning-profile", s);
        var w = skillOf(mine, "WRITING");
        assertEquals(1, w.get("aiGraded").asInt());
        assertEquals(1, w.get("ungraded").asInt());
        assertEquals(70.0, w.get("aiAverageScore").asDouble());
        var sp = skillOf(mine, "SPEAKING");
        assertEquals(1, sp.get("aiGraded").asInt());
        assertEquals(1, sp.get("ungraded").asInt());
        assertEquals(BigDecimal.valueOf(overall * 100 / 9).setScale(2, java.math.RoundingMode.HALF_UP).doubleValue(), sp.get("aiAverageScore").asDouble());

        var m = profile("/api/me/learning-profile", mate);
        assertNull(skillOf(m, "WRITING"));
        assertNull(skillOf(m, "SPEAKING"));

        String teacherUrl = "/api/classes/" + f.cls() + "/students/" + s.id() + "/learning-profile";
        assertEquals(mine.get("bySkill"), profile(teacherUrl, f.teacher()).get("bySkill"));

        // NFR-SEC-05: giáo viên lớp khác, bạn cùng lớp, chính học viên gọi endpoint giáo viên: bị chặn
        long otherCls = newClass(f.course());
        var otherTeacher = teacherOf(otherCls);
        for (var who : new Account[] {otherTeacher, mate, s}) {
            int st = getJson(teacherUrl, who).andReturn().getResponse().getStatus();
            assertTrue(st == 403 || st == 404, "status " + st);
        }
        int st = getJson("/api/classes/" + otherCls + "/students/" + s.id() + "/learning-profile", otherTeacher).andReturn().getResponse().getStatus();
        assertEquals(404, st);
    }

    /** Bộ câu có câu Viết bỏ trống: kết quả lượt tính 0 cho câu đó và hồ sơ cũng tính "đã chấm 0 điểm" (nhất quán với điểm lượt). */
    @Test
    void blankWritingInSetCountsZeroInAttemptAndProfile() throws Exception {
        var u = student(1);
        long b = bank(true);
        long wq = writingQuestion(b);
        jdbc.update("update questions set skill = 'WRITING' where id = ?", wq);
        long mcq = question(b, "CONNECTIVE_MCQ", 2, "Chọn từ nối", "{}", "{}");
        long right = insert("question_choices", Map.of("question_id", mcq, "label", "A", "content", "although", "is_correct", true));
        insert("question_choices", Map.of("question_id", mcq, "label", "B", "content", "so", "is_correct", false));
        long attempt = idOf(postJson("/api/banks/" + b + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
        guard.resetRateLimit(u.id());
        postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers", List.of(Map.of("questionId", mcq, "answer", Map.of("choiceId", right)))))
                .andExpect(status().isOk());

        assertEquals(0, BigDecimal.ZERO.compareTo(jdbc.queryForObject(
                "select credit from student_answers where attempt_id = ? and question_id = ?", BigDecimal.class, attempt, wq)));
        var w = skillOf(profile("/api/me/learning-profile", u), "WRITING");
        assertEquals(1, w.get("aiGraded").asInt());
        assertEquals(0.0, w.get("aiAverageScore").asDouble());
    }

    /** Nói độc lập FAILED/UNCLEAR/EXPIRED không tính; GRADED thiếu overall bị tính "đang chờ" mãi (khác bộ câu tính 0). */
    @Test
    void standaloneSpeakingStatuses() throws Exception {
        var u = student(0);
        long sq = question(bank(true), "SPEAKING", 2, "Nói", "{}", "{}");
        for (String[] r : new String[][] {{"FAILED", null}, {"UNCLEAR", null}, {"EXPIRED", null}, {"GRADED", "{\"overall\":9}"}}) {
            jdbc.update("insert into speaking_submissions (user_id, question_id, status, object_key, max_seconds, max_bytes, upload_expires_at, result) "
                    + "values (?, ?, ?, ?, 60, 1000000, ?, ? format json)", u.id(), sq, r[0], "k-" + UUID.randomUUID(), Timestamp.from(Instant.now()), r[1]);
        }
        var sp = skillOf(profile("/api/me/learning-profile", u), "SPEAKING");
        assertEquals(1, sp.get("ungraded").asInt());
        assertEquals(1, sp.get("aiGraded").asInt());
        assertEquals(100.0, sp.get("aiAverageScore").asDouble());
    }

    /** P0-B: 2000 phiên Viết (output ~8 KB) + 2000 lượt Nói độc lập: hồ sơ vẫn đúng số và trả dưới 3 giây trên H2. */
    @Test
    void manyStandaloneSessionsStayCorrectAndFast() throws Exception {
        var u = student(0);
        long b = bank(true);
        long wq = writingQuestion(b);
        long sq = question(b, "SPEAKING", 2, "Nói", "{}", "{}");
        int n = 2000;
        String pad = "x".repeat(8000);
        List<Object[]> ws = new ArrayList<>();
        List<Object[]> sps = new ArrayList<>();
        Timestamp now = Timestamp.from(Instant.now());
        for (int i = 0; i < n; i++) {
            ws.add(new Object[] {u.id(), wq});
            sps.add(new Object[] {u.id(), sq, "k-" + UUID.randomUUID(), now});
        }
        jdbc.batchUpdate("insert into writing_sessions (user_id, question_id, turns, status) values (?, ?, '[]' format json, 'SUBMITTED')", ws);
        List<Long> ids = jdbc.queryForList("select id from writing_sessions where user_id = ?", Long.class, u.id());
        List<Object[]> jobs = new ArrayList<>();
        for (long id : ids) {
            jobs.add(new Object[] {u.id(), "{\"scorePercent\":60,\"feedback\":\"" + pad + "\"}", id});
        }
        jdbc.batchUpdate("insert into ai_jobs (user_id, kind, status, output, session_id) values (?, 'WRITING_GRADE', 'DONE', ? format json, ?)", jobs);
        jdbc.update("update writing_sessions s set grade_job_id = (select max(j.id) from ai_jobs j where j.session_id = s.id) where s.user_id = ?", u.id());
        jdbc.batchUpdate("insert into speaking_submissions (user_id, question_id, status, object_key, max_seconds, max_bytes, upload_expires_at, result) "
                + "values (?, ?, 'GRADED', ?, 60, 1000000, ?, '{\"overall\":4.5}' format json)", sps);

        profile("/api/me/learning-profile", u); // làm nóng
        long t0 = System.nanoTime();
        var p = profile("/api/me/learning-profile", u);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        var w = skillOf(p, "WRITING");
        assertEquals(n, w.get("aiGraded").asInt());
        assertEquals(60.0, w.get("aiAverageScore").asDouble());
        assertEquals(50.0, skillOf(p, "SPEAKING").get("aiAverageScore").asDouble());
        System.out.println("[tester] learning-profile 2000+2000 sessions: " + ms + " ms");
        assertTrue(ms < 3000, "hồ sơ mất " + ms + " ms");
    }
}
