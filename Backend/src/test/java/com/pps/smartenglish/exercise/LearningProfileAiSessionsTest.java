package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.ai.AiTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** FR-EX-09, D-139, D-166, D-167: phiên Viết/Nói độc lập vào hồ sơ theo kỹ năng, điểm Cam quy về % 0..100. */
class LearningProfileAiSessionsTest extends AiTestSupport {

    private JsonNode skill(Account u, String key) throws Exception {
        var p = json.readTree(getJson("/api/me/learning-profile", u).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode n : p.get("bySkill")) {
            if (key.equals(n.get("key").asText())) {
                return n;
            }
        }
        return null;
    }

    private long writing(Account u, long q, Long attempt, String jobStatus, String output) {
        jdbc.update("insert into writing_sessions (user_id, question_id, turns, status, attempt_id) values (?, ?, '[]' format json, 'SUBMITTED', ?)",
                u.id(), q, attempt);
        long s = jdbc.queryForObject("select max(id) from writing_sessions", Long.class);
        jdbc.update("insert into ai_jobs (user_id, kind, status, output, session_id) values (?, 'WRITING_GRADE', ?, ? format json, ?)",
                u.id(), jobStatus, output, s);
        jdbc.update("update writing_sessions set grade_job_id = (select max(id) from ai_jobs) where id = ?", s);
        return s;
    }

    private long speaking(Account u, long q, Long attempt, String status, String result) {
        jdbc.update("insert into speaking_submissions (user_id, question_id, status, object_key, max_seconds, max_bytes, upload_expires_at, result, attempt_id) "
                + "values (?, ?, ?, ?, 60, 1000000, ?, ? format json, ?)", u.id(), q, status, "k-" + UUID.randomUUID(),
                Timestamp.from(Instant.now()), result, attempt);
        return jdbc.queryForObject("select max(id) from speaking_submissions", Long.class);
    }

    @Test
    void standaloneSessionsCountPerSkillOnA100Scale() throws Exception {
        var u = student(0);
        var other = student(0);
        long bank = bank(true);
        long wq = writingQuestion(bank);
        long sq = question(bank, "SPEAKING", 2, "Nói", "{}", "{}");
        writing(u, wq, null, "DONE", "{\"scorePercent\":70}");
        writing(u, wq, null, "RUNNING", null); // đang chấm
        writing(u, wq, null, "FAILED", null); // lỗi: không tính
        speaking(u, sq, null, "GRADED", "{\"overall\":6.5}");
        speaking(u, sq, null, "UNCLEAR", null);
        writing(other, wq, null, "DONE", "{\"scorePercent\":10}"); // người khác: không lẫn

        var w = skill(u, "WRITING");
        assertEquals(1, w.get("aiGraded").asInt());
        assertEquals(2, w.get("ungraded").asInt());
        assertEquals(70.0, w.get("aiAverageScore").asDouble());
        assertEquals(0, w.get("answered").asInt());
        assertEquals(72.22, skill(u, "SPEAKING").get("aiAverageScore").asDouble());
        assertEquals(1, skill(u, "SPEAKING").get("ungraded").asInt());
    }

    @Test
    void gradedSpeakingWithoutNumericOverallCountsAsZeroNotPending() throws Exception {
        var u = student(0);
        long sq = question(bank(true), "SPEAKING", 2, "Nói", "{}", "{}");
        speaking(u, sq, null, "GRADED", "{}");

        var s = skill(u, "SPEAKING");
        assertEquals(1, s.get("aiGraded").asInt());
        assertEquals(0.0, s.get("aiAverageScore").asDouble());
    }

    @Test
    void sessionLinkedToAnAttemptIsNotCountedTwice() throws Exception {
        var u = student(0);
        long bank = bank(true);
        long wq = writingQuestion(bank);
        jdbc.update("update questions set skill = 'WRITING' where id = ?", wq);
        jdbc.update("insert into student_exercise_attempts (user_id, bank_id, submitted_at, score, max_score) values (?, ?, CURRENT_TIMESTAMP, 0.8, 1)", u.id(), bank);
        long att = jdbc.queryForObject("select max(id) from student_exercise_attempts", Long.class);
        jdbc.update("insert into student_answers (attempt_id, question_id, credit) values (?, ?, 0.8)", att, wq);
        writing(u, wq, att, "DONE", "{\"scorePercent\":80}");

        var w = skill(u, "WRITING");
        assertEquals(1, w.get("ungraded").asInt()); // chỉ câu trong bộ, không cộng thêm phiên đã gắn lượt
        assertEquals(1, w.get("aiGraded").asInt());
        assertEquals(80.0, w.get("aiAverageScore").asDouble());
    }

    @Test
    void teacherOverrideDoesNotChangeProfileAndNoSessionsMeansNoSkill() throws Exception {
        var u = student(0);
        assertNull(skill(u, "WRITING"));
        long wq = writingQuestion(bank(true));
        long s = writing(u, wq, null, "DONE", "{\"scorePercent\":70}");
        jdbc.update("insert into grade_overrides (writing_session_id, score, graded_by, graded_at) values (?, 95, ?, CURRENT_TIMESTAMP)", s, u.id());
        assertEquals(70.0, skill(u, "WRITING").get("aiAverageScore").asDouble());
    }

    /** Trung bình gộp bộ câu + phiên độc lập tính từ tổng chính xác, làm tròn một lần: 33,34 + 33,35 + 1 = 67,69 / 3 = 22,56 (làm tròn hai lần ra 22,57). */
    @Test
    void mergedAverageIsRoundedOnlyOnce() throws Exception {
        var u = student(0);
        long bank = bank(true);
        long wq = writingQuestion(bank);
        jdbc.update("update questions set skill = 'WRITING' where id = ?", wq);
        for (String credit : new String[] {"0.3334", "0.3335"}) {
            jdbc.update("insert into student_exercise_attempts (user_id, bank_id, submitted_at, score, max_score) values (?, ?, CURRENT_TIMESTAMP, 0.3, 1)", u.id(), bank);
            long att = jdbc.queryForObject("select max(id) from student_exercise_attempts", Long.class);
            jdbc.update("insert into student_answers (attempt_id, question_id, credit) values (?, ?, ?)", att, wq, new java.math.BigDecimal(credit));
        }
        writing(u, wq, null, "DONE", "{\"scorePercent\":1}");

        var w = skill(u, "WRITING");
        assertEquals(3, w.get("aiGraded").asInt());
        assertEquals(22.56, w.get("aiAverageScore").asDouble());
    }
}
