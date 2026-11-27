package com.pps.smartenglish.ai;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** D-158 (tester): GET tiến độ Video phản xạ chỉ cho câu thuộc bộ của lượt, như mọi endpoint gắn lượt khác (AttemptLinks.requireOpen). */
class VideoReflexTesterTest extends AiTestSupport {

    private long reflexQuestion(long bank, String type, String payload) {
        long group = insert("question_groups", Map.of("bank_id", bank, "sort_order", 0, "video_youtube_id", "dQw4w9WgXcQ", "video_duration_sec", 300));
        long q = question(bank, type, 1, "Câu video", payload, null);
        jdbc.update("update questions set group_id = ? where id = ?", group, q);
        return q;
    }

    /** Lượt của tôi trên bộ A không được dùng để đọc cấu hình câu của bộ B (bộ khóa/chưa mua): inAttempt bỏ qua kiểm quyền vào bộ. */
    @Test
    void progressOfQuestionOutsideAttemptBankIsRejected() throws Exception {
        var u = student(10);
        long mine = bank(true);
        reflexQuestion(mine, "VIDEO_CUE", "{\"at\":5,\"prompt\":\"Say it.\",\"cue\":\"cue\"}");
        long foreignBank = bank(false);
        long foreign = reflexQuestion(foreignBank, "VIDEO_SHORT", "{\"at\":12,\"prompt\":\"Describe.\"}");
        long attempt = idOf(postJson("/api/banks/" + mine + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
        int code = getJson("/api/attempts/" + attempt + "/video-reflex/" + foreign, u).andReturn().getResponse().getStatus();
        org.junit.jupiter.api.Assertions.assertTrue(code == 400 || code == 403 || code == 404, "trả " + code + " (lộ writingFirst/maxRecordings của câu bộ khác)");
    }
}
