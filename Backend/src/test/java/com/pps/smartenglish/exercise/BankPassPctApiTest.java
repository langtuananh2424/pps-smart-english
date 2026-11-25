package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** D-145: Đạt/Chưa đạt theo ngưỡng pass_pct của bộ; world() có 4 câu MCQ nên mỗi câu đúng = 25%. */
class BankPassPctApiTest extends ListeningTestSupport {

    private static final String CMS = "/api/admin/cms";

    private void setPct(World w, int pct) {
        jdbc.update("update question_banks set pass_pct = ? where id = ?", pct, w.bank());
    }

    /** Phản hồi nộp bài và GET lượt phải cho cùng kết luận. */
    private void assertSame(Submitted s, Account u, Boolean passed, int pct) throws Exception {
        for (JsonNode n : List.of(s.body(), get("/api/attempts/" + s.id(), u, 200))) {
            assertEquals(passed, n.has("passed") ? n.get("passed").asBoolean() : null);
            assertEquals(pct, n.get("passPct").asInt());
        }
    }

    @Test
    void defaultIs70AndBoundaryIsInclusive() throws Exception {
        var u = student(0);
        var w = world("FREE", null);
        assertEquals(70, count("select pass_pct from question_banks where id = ?", w.bank())); // cột có mặc định
        assertSame(submitWithId(u, w, true, true, true, false), u, true, 70);   // 75% >= 70
        assertSame(submitWithId(u, w, true, true, false, false), u, false, 70); // 50% < 70
        setPct(w, 75);
        assertSame(submitWithId(u, w, true, true, true, false), u, true, 75);   // đúng bằng ngưỡng
        setPct(w, 76);
        assertSame(submitWithId(u, w, true, true, true, false), u, false, 76);  // sát dưới ngưỡng
    }

    @Test
    void customThresholdAppliesPerBank() throws Exception {
        var u = student(0);
        var w = world("FREE", null);
        setPct(w, 50);
        assertSame(submitWithId(u, w, true, true, false, false), u, true, 50);
        setPct(w, 100);
        assertSame(submitWithId(u, w, true, true, true, false), u, false, 100);
        assertSame(submitWithId(u, w, true, true, true, true), u, true, 100);
    }

    @Test
    void zeroMaxScoreHasNoVerdict() throws Exception {
        var u = student(0);
        var w = world("FREE", null);
        long id = startAttempt(u, w);
        jdbc.update("update student_exercise_attempts set submitted_at = CURRENT_TIMESTAMP, score = 0, max_score = 0 where id = ?", id);
        assertEquals(false, get("/api/attempts/" + id, u, 200).has("passed"));
    }

    @Test
    void pendingAiAnswersHoldBackVerdictButKeepPassPct() throws Exception {
        var u = student(2);
        long b = bank(true);
        long[] q = new long[4];
        for (int i = 0; i < 4; i++) {
            q[i] = question(b, "CONNECTIVE_REWRITE", i + 1, "Viết lại",
                    "{\"sentence\":\"It rained. We went out.\",\"requiredWord\":\"however\"}", "{}");
        }
        long attempt = idOf(postJson("/api/banks/" + b + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
        var text = Map.of("text", "It rained. However, we went out.");
        var answers = List.of(Map.of("questionId", q[0], "answer", text), Map.of("questionId", q[1], "answer", text),
                Map.of("questionId", q[2], "answer", text));
        postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers", answers)).andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingJobs[0]").exists()).andExpect(jsonPath("$.passed").doesNotExist())
                .andExpect(jsonPath("$.passPct").value(70));
        getJson("/api/attempts/" + attempt, u).andExpect(jsonPath("$.passed").doesNotExist()).andExpect(jsonPath("$.passPct").value(70));
        runner.runDueNow();
        // điểm đã chốt thì phải có kết luận (đúng hay sai tùy kết quả AI giả)
        getJson("/api/attempts/" + attempt, u).andExpect(jsonPath("$.pendingJobs").isEmpty()).andExpect(jsonPath("$.passed").exists());
    }

    @Test
    void forcedListeningHasNoPassFields() throws Exception {
        var u = student(0);
        var w = world("LOCKED", null);
        var s = submitWithId(u, w, true, true, true, true);
        for (JsonNode n : List.of(s.body(), get("/api/attempts/" + s.id(), u, 200))) {
            assertEquals(false, n.has("passed"));
            assertEquals(false, n.has("passPct"));
            assertEquals(true, n.has("forced"));
        }
    }

    private ResultActions call(MockHttpServletRequestBuilder req, Account a, Object body) throws Exception {
        req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return send(req, a.accessToken());
    }

    @Test
    void cmsCreatesAndPatchesThreshold() throws Exception {
        var admin = withRole("ADMIN");
        var w = world("FREE", null);
        var url = CMS + "/topics/" + w.topic() + "/banks";
        call(MockMvcRequestBuilders.post(url), admin, Map.of("title", "Mặc định")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.passPct").value(70));
        long id = json.readTree(call(MockMvcRequestBuilders.post(url), admin, Map.of("title", "Tùy chỉnh", "passPct", 55))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.passPct").value(55)).andReturn().getResponse().getContentAsString())
                .get("id").asLong();
        for (int bad : new int[] {0, 101, -5}) {
            call(MockMvcRequestBuilders.post(url), admin, Map.of("title", "Sai", "passPct", bad)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.details.passPct").exists());
            call(MockMvcRequestBuilders.patch(CMS + "/banks/" + id), admin, Map.of("passPct", bad)).andExpect(status().isBadRequest());
        }
        call(MockMvcRequestBuilders.patch(CMS + "/banks/" + id), admin, Map.of("title", "Đổi tên")).andExpect(status().isOk())
                .andExpect(jsonPath("$.passPct").value(55)); // không gửi thì giữ
        call(MockMvcRequestBuilders.patch(CMS + "/banks/" + id), admin, Map.of("passPct", 100)).andExpect(status().isOk())
                .andExpect(jsonPath("$.passPct").value(100));
        send(MockMvcRequestBuilders.get(CMS + "/banks/" + id), admin.accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.bank.passPct").value(100));
        var placement = json.readTree(call(MockMvcRequestBuilders.post(CMS + "/placement/banks"), admin,
                Map.of("title", "Đầu vào", "timeLimitSec", 600)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        call(MockMvcRequestBuilders.patch(CMS + "/banks/" + placement.get("id").asLong()), admin, Map.of("passPct", 60))
                .andExpect(status().isBadRequest());
    }
}
