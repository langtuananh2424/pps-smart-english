package com.pps.smartenglish.exercise;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.support.MutableClockConfig;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Tester D-150 (thử lại kèm gợi ý) và D-151/D-155 (chế độ của lượt): phân quyền theo lượt, lộ gợi ý/đáp án, đầu vào xấu, hết giờ, giới hạn tần suất. */
@Import(MutableClockConfig.class)
class RetryIeltsTesterTest extends ListeningTestSupport {

    private static final String HINT = "GOI-Y-BI-MAT";

    @Autowired MutableClockConfig.MutableClock clock;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    /** Bộ Listening (playback FREE) gắn kỹ năng LISTENING: thuộc phạm vi D-151; câu q0 có gợi ý. */
    private World ielts() {
        var w = world("FREE", null);
        jdbc.update("update topics set skill = 'LISTENING' where id = ?", w.topic());
        insert("question_hints", Map.of("question_id", w.q()[0], "content", HINT, "sort_order", 0));
        return w;
    }

    /** D-155: học viên không chọn chế độ; EXAM giả lập bài giao Thi bằng cách đặt mode của lượt (đường bài giao có test riêng). */
    private long start(Account u, World w, String mode) throws Exception {
        long id = idOf(postJson("/api/banks/" + w.bank() + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
        jdbc.update("update student_exercise_attempts set mode = ? where id = ?", mode, id);
        return id;
    }

    private ResultActions check(Account u, long attempt, long question, Object answer) throws Exception {
        return postJson("/api/attempts/" + attempt + "/questions/" + question + "/check", u, Map.of("answer", answer));
    }

    @Test
    void strangerCannotTouchHintsProgressCheckOrPlayOfAnotherAttempt() throws Exception {
        var w = ielts();
        var owner = student(0);
        var stranger = student(0);
        long a = start(owner, w, "PRACTICE");
        getJson("/api/attempts/" + a + "/hints", stranger).andExpect(status().isNotFound()).andExpect(content().string(not(containsString(HINT))));
        getJson("/api/attempts/" + a + "/progress", stranger).andExpect(status().isNotFound());
        check(stranger, a, w.q()[0], Map.of("choiceId", w.right()[0])).andExpect(status().isNotFound());
        play(stranger, a, w.g1()).andExpect(status().isNotFound());
        assertEquals(0, count("select count(*) from attempt_question_checks where attempt_id = ?", a));
    }

    @Test
    void examAttemptGetsNoHintsFromBankOrAttemptEvenAfterSubmit() throws Exception {
        var w = ielts();
        var u = student(0);
        long a = start(u, w, "EXAM");
        getJson("/api/banks/" + w.bank(), u).andExpect(status().isOk()).andExpect(content().string(not(containsString(HINT))));
        getJson("/api/attempts/" + a + "/hints", u).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("hints_unavailable")).andExpect(content().string(not(containsString(HINT))));
        check(u, a, w.q()[0], Map.of("choiceId", w.right()[0])).andExpect(status().isConflict());
        postJson("/api/attempts/" + a + "/submit", u, Map.of("answers", List.of())).andExpect(status().isOk());
        getJson("/api/attempts/" + a + "/hints", u).andExpect(status().isForbidden());
    }

    @Test
    void practiceHintsCarryNoAnswerOrExplanation() throws Exception {
        var w = ielts();
        var u = student(0);
        long a = start(u, w, "PRACTICE");
        getJson("/api/attempts/" + a + "/hints", u).andExpect(status().isOk()).andExpect(content().string(containsString(HINT)))
                .andExpect(content().string(not(containsString(SECRET_EXPLANATION)))).andExpect(content().string(not(containsString("isCorrect"))));
        check(u, a, w.q()[0], Map.of("choiceId", w.wrong()[0])).andExpect(status().isOk())
                .andExpect(content().string(not(containsString(String.valueOf(w.right()[0])))))
                .andExpect(content().string(not(containsString(SECRET_EXPLANATION))));
    }

    /** Đáp án của câu khác (đúng ở câu đó) không được tính đúng cho câu đang kiểm tra. */
    @Test
    void choiceFromAnotherQuestionIsNotCorrect() throws Exception {
        var w = ielts();
        var u = student(0);
        long a = start(u, w, "PRACTICE");
        check(u, a, w.q()[0], Map.of("choiceId", w.right()[1])).andExpect(status().isOk()).andExpect(jsonPath("$.correct").value(false));
    }

    /** Câu trả lời sai định dạng: không 500, và khóa câu bằng giá trị rác không làm hỏng việc nộp bài. */
    @Test
    void malformedAnswersNeverBreakCheckOrSubmit() throws Exception {
        var w = ielts();
        var u = student(0);
        long a = start(u, w, "PRACTICE");
        for (Object bad : List.of("rac", 42, List.of(1, 2), Map.of("choiceId", "abc"), Map.of("choiceId", Map.of("x", 1)))) {
            int s = check(u, a, w.q()[1], bad).andReturn().getResponse().getStatus();
            org.junit.jupiter.api.Assertions.assertTrue(s == 200 || s == 400 || s == 409, "kiểm tra với " + bad + " trả " + s);
        }
        check(u, a, w.q()[0], Map.of("choiceId", "abc")); // chuỗi rác không phải object bị 400 và không tính lượt; object sai kiểu thì tính
        check(u, a, w.q()[0], Map.of("choiceId", "abc"));
        getJson("/api/attempts/" + a + "/progress", u).andExpect(status().isOk());
        put("/api/attempts/" + a + "/draft", u, Map.of("answers", answers(w, true, true))).andExpect(status().isOk());
        // q0 (rác, đã khóa) giữ giá trị rác; q1 cũng khóa với rác: nộp vẫn 200, không câu nào được tính đúng
        postJson("/api/attempts/" + a + "/submit", u, Map.of("answers", answers(w, true, true))).andExpect(status().isOk()).andExpect(jsonPath("$.score").value(0));
    }

    @Test
    void checkAfterTimeUpIsRejectedAndNotRecorded() throws Exception {
        var w = ielts();
        jdbc.update("update question_banks set time_limit_sec = 60 where id = ?", w.bank());
        var u = student(0);
        long a = start(u, w, "PRACTICE");
        clock.set(Instant.now().plusSeconds(120));
        check(u, a, w.q()[0], Map.of("choiceId", w.wrong()[0])).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("attempt_time_up"));
        assertEquals(0, count("select count(*) from attempt_question_checks where attempt_id = ?", a));
    }

    /** app.retry.checksPerMinute mặc định 60: lần thứ 61 trong một phút bị 429, kể cả khi các lần trước bị từ chối vì câu đã khóa. */
    @Test
    void checkFloodIsRateLimited() throws Exception {
        var w = ielts();
        var u = student(0);
        long a = start(u, w, "PRACTICE");
        for (int i = 0; i < 60; i++) {
            assertNotEquals(429, check(u, a, w.q()[i % 4], Map.of("choiceId", w.wrong()[i % 4])).andReturn().getResponse().getStatus(), "lần " + i);
        }
        check(u, a, w.q()[0], Map.of("choiceId", w.right()[0])).andExpect(status().isTooManyRequests());
    }

    /** D-155: body của client cũ (mode bất kỳ, kể cả hỏng) bị bỏ qua; chế độ theo bộ. */
    @Test
    void legacyModeInBodyIsIgnored() throws Exception {
        var u = student(0);
        for (Object bad : List.of("EXAM", "exam", "", 1, Map.of("x", 1), List.of("EXAM"))) {
            var p = ielts();
            idOf(postJson("/api/banks/" + p.bank() + "/attempts", u, Map.of("mode", bad)).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.mode").value("PRACTICE")), "id");
        }
        var p = ielts();
        send(MockMvcRequestBuilders.post("/api/banks/" + p.bank() + "/attempts").contentType(MediaType.APPLICATION_JSON).content("{bad"),
                u.accessToken()).andExpect(status().isCreated());
    }

    /** Dò đáp án ở lượt Luyện tập rồi mở lượt Mô phỏng thi: phải nộp lượt cũ trước (D-147 mục 10). */
    @Test
    void cannotOpenExamWhilePracticeAttemptIsOpen() throws Exception {
        var w = ielts();
        var u = student(0);
        long a = start(u, w, "PRACTICE");
        check(u, a, w.q()[0], Map.of("choiceId", w.wrong()[0])).andExpect(status().isOk());
        postJson("/api/banks/" + w.bank() + "/attempts", u, Map.of("mode", "EXAM")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("attempt_in_progress")).andExpect(jsonPath("$.error.details.attemptId").value(a));
    }

    @Test
    void examAttemptOnFreeListeningBankCountsPlaysAndNeedsFinishBeforeReplay() throws Exception {
        var w = ielts();
        jdbc.update("update question_banks set play_limit = 2 where id = ?", w.bank());
        var u = student(0);
        long a = start(u, w, "EXAM");
        play(u, a, w.g1()).andExpect(status().isOk()).andExpect(jsonPath("$.playbackMode").value("EXAM")).andExpect(jsonPath("$.remaining").value(1));
        playDone(u, a, w.g1()).andExpect(status().isOk()).andExpect(jsonPath("$.remaining").value(1));
        play(u, a, w.g1()).andExpect(status().isOk()).andExpect(jsonPath("$.remaining").value(0));
        playDone(u, a, w.g1());
        play(u, a, w.g1()).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("play_limit_reached"));
    }

    /**
     * D-151 mục 1, 7: luật "lượt EXAM trên bộ FREE nghe như thi" chỉ cho lượt Mô phỏng thi của bộ IELTS. Bộ EXAM khác (bài kiểm tra
     * chương, thi khóa) để playback FREE (mặc định V33) phải giữ FREE như trước đợt này (D-135), cả lượt cũ (mode null) lẫn lượt mới.
     */
    @Test
    void nonIeltsExamBankWithFreePlaybackStaysFree() throws Exception {
        var w = world("FREE", null);
        jdbc.update("update question_banks set mode = 'EXAM' where id = ?", w.bank());
        var u = student(0);
        long legacy = insert("student_exercise_attempts", Map.of("user_id", u.id(), "bank_id", w.bank(), "started_at", java.time.OffsetDateTime.now()));
        for (int i = 0; i < 3; i++) {
            play(u, legacy, w.g1()).andExpect(status().isOk()).andExpect(jsonPath("$.playbackMode").value("FREE"));
        }
        jdbc.update("update student_exercise_attempts set mode = 'EXAM' where id = ?", legacy); // lượt mới ghi mode = bank.mode
        for (int i = 0; i < 3; i++) {
            play(u, legacy, w.g2()).andExpect(status().isOk()).andExpect(jsonPath("$.playbackMode").value("FREE"));
        }
    }
    /** FAQ chỉ chặn theo lượt EXAM đang mở của chính học viên: bộ ngoài IELTS, lượt PRACTICE, học viên khác không bị chặn sai. */
    @Test
    void faqBlockIsScopedToOwnOpenExamAttempt() throws Exception {
        var other = world("FREE", null); // ngoài IELTS, bộ PRACTICE
        insert("bank_faqs", Map.of("bank_id", other.bank(), "question", "Hỏi", "answer", "FAQ-NGOAI", "sort_order", 0));
        var u = student(0);
        startAttempt(u, other);
        getJson("/api/banks/" + other.bank() + "/faqs", u).andExpect(status().isOk()).andExpect(content().string(containsString("FAQ-NGOAI")));

        var w = ielts();
        insert("bank_faqs", Map.of("bank_id", w.bank(), "question", "Hỏi", "answer", "FAQ-IELTS", "sort_order", 0));
        start(u, w, "EXAM");
        var stranger = student(0);
        getJson("/api/banks/" + w.bank() + "/faqs", stranger).andExpect(status().isOk()).andExpect(content().string(containsString("FAQ-IELTS")));
        getJson("/api/banks/" + w.bank() + "/faqs", u).andExpect(status().isForbidden()).andExpect(content().string(not(containsString("FAQ-IELTS"))));
    }

    /** Lượt EXAM hết giờ mà chưa nộp: vẫn không lộ FAQ hay gợi ý. */
    @Test
    void timedOutExamAttemptStillLeaksNoFaqOrHints() throws Exception {
        var w = ielts();
        jdbc.update("update question_banks set time_limit_sec = 60 where id = ?", w.bank());
        insert("bank_faqs", Map.of("bank_id", w.bank(), "question", "Hỏi", "answer", "FAQ-HET-GIO", "sort_order", 0));
        var u = student(0);
        long a = start(u, w, "EXAM");
        clock.set(Instant.now().plusSeconds(120));
        getJson("/api/banks/" + w.bank() + "/faqs", u).andExpect(content().string(not(containsString("FAQ-HET-GIO"))));
        int s = getJson("/api/attempts/" + a + "/hints", u).andExpect(content().string(not(containsString(HINT)))).andReturn().getResponse().getStatus();
        org.junit.jupiter.api.Assertions.assertTrue(s == 403 || s == 409, "hints trả " + s);
    }

    /** Bộ EXAM ngoài IELTS vẫn bị chặn FAQ như trước (assistant_unavailable). */
    @Test
    void nonIeltsExamBankFaqStaysBlocked() throws Exception {
        var w = world("FREE", null);
        jdbc.update("update question_banks set mode = 'EXAM' where id = ?", w.bank());
        insert("bank_faqs", Map.of("bank_id", w.bank(), "question", "Hỏi", "answer", "FAQ-THI", "sort_order", 0));
        getJson("/api/banks/" + w.bank() + "/faqs", student(0)).andExpect(status().isForbidden()) // bank_locked hoặc assistant_unavailable tùy cổng
                .andExpect(content().string(not(containsString("FAQ-THI"))));
    }

    /** Lượt PRACTICE trên bộ IELTS FREE vẫn nghe tự do (không bị luật thi). */
    @Test
    void practiceAttemptOnIeltsFreeBankStaysFree() throws Exception {
        var w = ielts();
        var u = student(0);
        long a = start(u, w, "PRACTICE");
        for (int i = 0; i < 3; i++) {
            play(u, a, w.g1()).andExpect(status().isOk()).andExpect(jsonPath("$.playbackMode").value("FREE"));
        }
    }
}
