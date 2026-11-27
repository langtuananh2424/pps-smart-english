package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

/** D-150, D-151: FAQ của Cam, gợi ý hết giờ và kiểm tra sai định dạng ở bộ IELTS. */
@Import(MutableClockConfig.class)
class RetryIeltsFollowupTest extends ListeningTestSupport {

    @Autowired MutableClockConfig.MutableClock clock;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private World ielts() {
        var w = world("FREE", null);
        jdbc.update("update topics set skill = 'LISTENING' where id = ?", w.topic());
        insert("bank_faqs", Map.of("bank_id", w.bank(), "question", "Hỏi", "answer", "Đáp", "sort_order", 0));
        return w;
    }

    /** D-155: học viên không chọn chế độ; EXAM giả lập bài giao Thi bằng cách đặt mode của lượt (đường bài giao có test riêng). */
    private long start(Account u, World w, String mode) throws Exception {
        long id = idOf(postJson("/api/banks/" + w.bank() + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
        jdbc.update("update student_exercise_attempts set mode = ? where id = ?", mode, id);
        return id;
    }

    @Test
    void faqIsHiddenWhileExamAttemptIsOpenOnly() throws Exception {
        var w = ielts();
        var u = student(0);
        getJson("/api/banks/" + w.bank() + "/faqs", u).andExpect(status().isOk());
        long a = start(u, w, "EXAM");
        getJson("/api/banks/" + w.bank() + "/faqs", u).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("hints_unavailable"));
        postJson("/api/attempts/" + a + "/submit", u, Map.of("answers", List.of())).andExpect(status().isOk());
        getJson("/api/banks/" + w.bank() + "/faqs", u).andExpect(status().isOk());
        start(u, w, "PRACTICE");
        getJson("/api/banks/" + w.bank() + "/faqs", u).andExpect(status().isOk());
    }

    @Test
    void hintsAfterTimeUpAreRejected() throws Exception {
        var w = ielts();
        jdbc.update("update question_banks set time_limit_sec = 60 where id = ?", w.bank());
        var u = student(0);
        long a = start(u, w, "PRACTICE");
        clock.set(Instant.now().plusSeconds(120));
        getJson("/api/attempts/" + a + "/hints", u).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("attempt_time_up"));
    }

    @Test
    void nonObjectAnswerIsBadRequestAndNotCounted() throws Exception {
        var w = ielts();
        var u = student(0);
        long a = start(u, w, "PRACTICE");
        for (Object bad : List.of("rac", 42, List.of(1, 2))) {
            postJson("/api/attempts/" + a + "/questions/" + w.q()[0] + "/check", u, Map.of("answer", bad)).andExpect(status().isBadRequest());
        }
        assertEquals(0, count("select count(*) from attempt_question_checks where attempt_id = ?", a));
    }
}
