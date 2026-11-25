package com.pps.smartenglish.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.ai.dto.WritingGrade;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** D-145: Viết/Nói trong lượt của bộ thi khóa (D-82 chỉ cấm luyện riêng) và API trạng thái phiên theo lượt. */
class AttemptAiLinksTest extends AiTestSupport {

    private static final String ESSAY = "I agree with online learning because it is flexible. However, students need discipline.";
    private static final String CUE = "{\"part\":1,\"prompt\":\"Describe a trip.\"}";

    private long startAttempt(Account u, long bank) throws Exception {
        return idOf(postJson("/api/banks/" + bank + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
    }

    /** Bộ thi khóa thật: gắn làm bài thi của khóa, học viên đã ghi danh, mở thi ngay (0% tiến độ). */
    private long examAttempt(Account u, long bank) throws Exception {
        jdbc.update("update question_banks set mode = 'EXAM' where id = ?", bank);
        long course = jdbc.queryForObject("select l.course_id from question_banks b join topics t on t.id = b.topic_id "
                + "join course_levels l on l.id = t.level_id where b.id = ?", Long.class, bank);
        jdbc.update("update courses set exam_bank_id = ?, exam_unlock_percent = 0 where id = ?", bank, course);
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', ?, null)",
                u.id(), course, java.time.OffsetDateTime.parse("2020-01-01T00:00:00Z"));
        return startAttempt(u, bank);
    }

    @Test
    void examBankAllowsWritingAndSpeakingInsideAValidAttemptOnly() throws Exception {
        var u = student(5);
        long bank = bank(false);
        long wq = writingQuestion(bank);
        long sq = question(bank, "SPEAKING", 2, "Describe a trip.", CUE, "{}");
        long attempt = examAttempt(u, bank);

        guard.resetRateLimit(u.id());
        long session = idOf(postJson("/api/writing/sessions", u, Map.of("questionId", wq, "attemptId", attempt)).andExpect(status().isCreated()), "sessionId");
        getJson("/api/writing/sessions/" + session, u).andExpect(status().isOk());
        guard.resetRateLimit(u.id());
        postJson("/api/speaking/submissions", u, Map.of("questionId", sq, "attemptId", attempt)).andExpect(status().isCreated());
        getJson("/api/speaking/questions/" + sq + "?attemptId=" + attempt, u).andExpect(status().isOk()).andExpect(jsonPath("$.part").value(1));

        // không có lượt thì vẫn bị chặn
        getJson("/api/speaking/questions/" + sq, u).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("exam_question_not_practicable"));
        guard.resetRateLimit(u.id());
        postJson("/api/writing/sessions", u, Map.of("questionId", wq)).andExpect(status().isForbidden());
    }

    @Test
    void anotherUsersAttemptIsNotFoundEvenForExamBank() throws Exception {
        var u = student(5);
        var other = student(5);
        long bank = bank(false);
        long wq = writingQuestion(bank);
        long sq = question(bank, "SPEAKING", 2, "Describe a trip.", CUE, "{}");
        long attempt = examAttempt(u, bank);
        guard.resetRateLimit(other.id());
        postJson("/api/writing/sessions", other, Map.of("questionId", wq, "attemptId", attempt)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("attempt_not_found"));
        getJson("/api/speaking/questions/" + sq + "?attemptId=" + attempt, other).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("attempt_not_found"));
    }

    @Test
    void aiSessionsListsLatestPerQuestionWithoutScoresAndIsOwnerOnly() throws Exception {
        var u = student(5);
        var other = student(5);
        long bank = bank(true);
        long wq = writingQuestion(bank);
        long attempt = startAttempt(u, bank);
        getJson("/api/attempts/" + attempt + "/ai-sessions", u).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));

        guard.resetRateLimit(u.id());
        long first = idOf(postJson("/api/writing/sessions", u, Map.of("questionId", wq, "attemptId", attempt)).andExpect(status().isCreated()), "sessionId");
        getJson("/api/attempts/" + attempt + "/ai-sessions", u).andExpect(jsonPath("$.data[0].id").value(first))
                .andExpect(jsonPath("$.data[0].kind").value("WRITING")).andExpect(jsonPath("$.data[0].questionId").value(wq))
                .andExpect(jsonPath("$.data[0].status").value("NOT_SUBMITTED"));

        postJson("/api/writing/sessions/" + first + "/submit", u, Map.of("finalText", ESSAY), UUID.randomUUID().toString()).andExpect(status().isAccepted());
        getJson("/api/attempts/" + attempt + "/ai-sessions", u).andExpect(jsonPath("$.data[0].status").value("GRADING"));

        fake.override(WritingGrade.class, new WritingGrade(80, 80, 80, 80, List.of(), "fixed", new WritingGrade.Connectives(2, 1, 2, List.of(), "ok"), List.of(), null, "fb"));
        assertEquals(1, runner.runDueNow());
        var graded = getJson("/api/attempts/" + attempt + "/ai-sessions", u).andExpect(jsonPath("$.data[0].status").value("GRADED")).andReturn();
        assertEquals(false, graded.getResponse().getContentAsString().toLowerCase().contains("score")); // không lộ điểm

        guard.resetRateLimit(u.id());
        long second = idOf(postJson("/api/writing/sessions", u, Map.of("questionId", wq, "attemptId", attempt)).andExpect(status().isCreated()), "sessionId");
        getJson("/api/attempts/" + attempt + "/ai-sessions", u).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(second)).andExpect(jsonPath("$.data[0].status").value("NOT_SUBMITTED"));

        getJson("/api/attempts/" + attempt + "/ai-sessions", other).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("attempt_not_found"));
        getJson("/api/attempts/999999/ai-sessions", u).andExpect(status().isNotFound());
    }
}
