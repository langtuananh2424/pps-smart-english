package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.ai.AiTestSupport;
import com.pps.smartenglish.ai.dto.WritingGrade;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** D-152: XP theo lượt, link thông báo, và kết quả Viết không lộ sang lượt khác chứa cùng câu. */
class ExtraPracticeFollowupTest extends AiTestSupport {

    private static final String ESSAY = "I agree with online learning because it is flexible. However, students need discipline.";

    private long course;
    private long topic;
    private long bank;

    private void setup(Account u) {
        course = insert("courses", Map.of("slug", "fu-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        long level = insert("course_levels", Map.of("course_id", course, "code", "A", "title", "Level A", "status", "PUBLISHED"));
        topic = insert("topics", Map.of("level_id", level, "title", "Topic", "status", "PUBLISHED"));
        bank = insert("question_banks", Map.of("topic_id", topic, "title", "Bộ", "is_trial", false, "status", "PUBLISHED", "mode", "PRACTICE"));
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', ?, null)",
                u.id(), course, OffsetDateTime.parse("2020-01-01T00:00:00Z"));
    }

    private long startExtra(Account u, Map<String, Object> body) throws Exception {
        return idOf(postJson("/api/me/courses/" + course + "/extra-practice/attempts", u, body).andExpect(status().isCreated()), "id");
    }

    private void submit(Account u, long attempt) throws Exception {
        postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers", List.of())).andExpect(status().isOk());
    }

    @Test
    void everySubmittedExtraAttemptEarnsXpOnceAndNormalBanksKeepTheirKey() throws Exception {
        var u = student(0);
        setup(u);
        for (int i = 0; i < 4; i++) {
            question(bank, "CONNECTIVE_MCQ", i + 1, "Đề " + i, "{}", "{}");
        }
        long e1 = startExtra(u, Map.of("scope", "CHALLENGE", "topicId", topic));
        submit(u, e1);
        long e2 = startExtra(u, Map.of("scope", "CHALLENGE", "topicId", topic));
        submit(u, e2);
        assertEquals(2, count("select count(*) from xp_events where user_id = ? and source = 'EXERCISE' and ref_id in (?, ?)", u.id(), -e1, -e2));
        long normal = idOf(postJson("/api/banks/" + bank + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
        submit(u, normal);
        assertEquals(1, count("select count(*) from xp_events where user_id = ? and source = 'EXERCISE' and ref_id = ?", u.id(), bank));
        assertEquals(30, count("select xp from user_profiles where user_id = ?", u.id()));
    }

    @Test
    void writingResultsOfOneAttemptStaySealedWhileAnotherAttemptHoldingTheQuestionIsOpen() throws Exception {
        var u = student(5);
        setup(u);
        long wq = writingQuestion(bank);
        long extra = startExtra(u, Map.of("scope", "CHALLENGE", "topicId", topic, "kind", "WRITING"));
        guard.resetRateLimit(u.id());
        long session = idOf(postJson("/api/writing/sessions", u, Map.of("questionId", wq, "attemptId", extra)).andExpect(status().isCreated()), "sessionId");
        guard.resetRateLimit(u.id());
        postJson("/api/writing/sessions/" + session + "/submit", u, Map.of("finalText", ESSAY), UUID.randomUUID().toString()).andExpect(status().isAccepted());
        submit(u, extra);
        fake.override(WritingGrade.class, new WritingGrade(80, 80, 80, 80, List.of(), "fixed", new WritingGrade.Connectives(2, 1, 2, List.of(), "ok"), List.of(), null, "fb"));
        assertEquals(1, runner.runDueNow());
        // lượt Luyện thêm đã nộp và chấm xong: thông báo trỏ tới màn Luyện thêm, không phải /bai-tap/{bộ neo}
        String payload = jdbc.queryForObject("select payload from notifications where user_id = ? and type = 'EXERCISE_GRADED'", String.class, u.id());
        assertTrue(payload.contains("/bai-tap/luyen-them/" + extra), payload);
        getJson("/api/me/writing", u).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].scorePercent").exists());

        // mở lượt thường trên bộ chứa cùng câu: kết quả lượt Luyện thêm phải giấu lại, nếu không đọc bản sửa rồi chép
        long normal = idOf(postJson("/api/banks/" + bank + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
        getJson("/api/me/writing", u).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].scorePercent").doesNotExist());
        getJson("/api/writing/sessions/" + session, u).andExpect(status().isOk()).andExpect(jsonPath("$.corrected").doesNotExist());
        submit(u, normal);
        getJson("/api/me/writing", u).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].scorePercent").exists());
    }
}
