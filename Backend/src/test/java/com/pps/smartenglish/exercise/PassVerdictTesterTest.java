package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tester đợt D-145: các ca biên của passed/passPct và đường lộ đáp án ở lượt Nghe ép. */
class PassVerdictTesterTest extends ListeningTestSupport {

    private static final String REWRITE_PAYLOAD = "{\"sentence\":\"It rained. We went out.\",\"requiredWord\":\"however\"}";

    private long rewriteBank(int n, String playback) {
        long b = bank(true);
        jdbc.update("update question_banks set playback_mode = ? where id = ?", playback, b);
        return b;
    }

    private JsonNode submitRewrite(Account u, long b, int n) throws Exception {
        long[] q = new long[n];
        for (int i = 0; i < n; i++) {
            q[i] = question(b, "CONNECTIVE_REWRITE", i + 1, "Viết lại", REWRITE_PAYLOAD, "{}");
        }
        long attempt = idOf(postJson("/api/banks/" + b + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
        var text = Map.of("text", "It rained. However, we went out.");
        var answers = java.util.Arrays.stream(q).mapToObj(id -> Map.<String, Object>of("questionId", id, "answer", text)).toList();
        var body = postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers", answers)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(body);
        node.put("_attemptId", attempt);
        return node;
    }

    /** Hết lượt AI: câu viết lại tính 0 kèm lý do (D-147), không có job chờ; điểm chốt luôn nên Đạt/Chưa đạt có ngay. */
    @Test
    void quotaExhaustedAiAnswersHoldBackVerdict() throws Exception {
        var u = student(0);
        var s = submitRewrite(u, rewriteBank(4, "FREE"), 4);
        assertEquals(true, s.path("aiQuotaRequired").asBoolean(false), "điều kiện ca: hết lượt AI");
        assertEquals(false, s.path("passed").asBoolean(true), "câu hết lượt tính 0 nên Chưa đạt: " + s);
        var g = get("/api/attempts/" + s.get("_attemptId").asLong(), u, 200);
        assertEquals(false, g.path("passed").asBoolean(true), "GET lượt phải chốt Chưa đạt: " + g);
    }

    /** Lượt Nghe ép có câu AI: kết quả job chấm không được lộ đúng/sai hay câu sửa (D-142: chỉ số câu đúng). */
    @Test
    void forcedListeningAiJobDoesNotRevealAnswer() throws Exception {
        var u = student(5);
        var s = submitRewrite(u, rewriteBank(2, "LOCKED"), 2);
        assertEquals(true, s.has("forced"), "điều kiện ca: lượt Nghe ép " + s);
        List<Long> jobs = new java.util.ArrayList<>();
        s.path("pendingJobs").forEach(j -> jobs.add(j.asLong()));
        assertFalse(jobs.isEmpty(), "điều kiện ca: có job AI chờ " + s);
        runner.runDueNow();
        for (long job : jobs) {
            var view = get("/api/ai/jobs/" + job, u, 200);
            assertFalse(view.hasNonNull("output"), "job của lượt Nghe ép trả output chấm chi tiết: " + view);
        }
    }

    /** Bộ bài cũ chưa có cấu hình: cột mặc định 70 cho bản ghi mới chèn thẳng bằng SQL (tương thích bản chạy cũ). */
    @Test
    void legacyInsertGetsDefault70() {
        long b = bank(true);
        assertEquals(70, count("select pass_pct from question_banks where id = ?", b));
    }

    /** CHECK ở DB chặn giá trị ngoài 1..100 kể cả khi đi vòng qua API. */
    @Test
    void dbRejectsOutOfRangePassPct() {
        long b = bank(true);
        for (int bad : new int[] {0, 101}) {
            try {
                jdbc.update("update question_banks set pass_pct = ? where id = ?", bad, b);
                throw new AssertionError("DB nhận pass_pct = " + bad);
            } catch (org.springframework.dao.DataIntegrityViolationException expected) {
                // đúng
            }
        }
    }

    /** passPct kiểu sai (chuỗi, số thực) phải 400, không 500. */
    @Test
    void cmsRejectsNonIntegerPassPct() throws Exception {
        var admin = withRole("ADMIN");
        var w = world("FREE", null);
        for (Object bad : new Object[] {"abc", 70.5, true}) {
            int code = send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/admin/cms/banks/" + w.bank())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("passPct", bad))), admin.accessToken()).andReturn().getResponse().getStatus();
            assertEquals(400, code, "passPct = " + bad + " (máy chủ trả " + code + ", ngưỡng hiện tại "
                    + count("select pass_pct from question_banks where id = ?", w.bank()) + ")");
        }
        // học viên không sửa được ngưỡng
        var stu = student(0);
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/admin/cms/banks/" + w.bank())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("passPct", 1))), stu.accessToken())
                .andExpect(status().isForbidden());
        getJson("/api/banks/" + w.bank(), stu).andExpect(status().isOk());
    }

    /** Trước khi nộp: GET lượt không có passed/passPct/score. */
    @Test
    void unsubmittedAttemptHasNoVerdict() throws Exception {
        var u = student(0);
        var w = world("FREE", null);
        long id = startAttempt(u, w);
        getJson("/api/attempts/" + id, u).andExpect(status().isOk()).andExpect(jsonPath("$.passed").doesNotExist())
                .andExpect(jsonPath("$.passPct").doesNotExist()).andExpect(jsonPath("$.results").doesNotExist());
    }

    /** GlobalExceptionHandler.unreadable: details chỉ có tên trường, không lộ tên class, gói Java, dòng JSON hay stack trace. */
    @Test
    void malformedJsonDetailsDoNotLeakInternals() throws Exception {
        var admin = withRole("ADMIN");
        var w = world("FREE", null);
        var stu = student(0);
        long attempt = startAttempt(stu, w);
        var cases = new java.util.ArrayList<String[]>();
        // {url, token, body, tên trường mong đợi trong details hoặc rỗng}
        cases.add(new String[] {"/api/admin/cms/banks/" + w.bank(), admin.accessToken(), "{\"passPct\":\"abc\"}", "passPct"});
        cases.add(new String[] {"/api/admin/cms/banks/" + w.bank(), admin.accessToken(), "{\"passPct\":70.5}", "passPct"});
        cases.add(new String[] {"/api/admin/cms/banks/" + w.bank(), admin.accessToken(), "{\"timeLimitSec\":{\"a\":1}}", "timeLimitSec"});
        cases.add(new String[] {"/api/admin/cms/banks/" + w.bank(), admin.accessToken(), "{\"passPct\":", ""});
        cases.add(new String[] {"/api/admin/cms/banks/" + w.bank(), admin.accessToken(), "[1,2]", ""});
        cases.add(new String[] {"/api/admin/cms/banks/" + w.bank(), admin.accessToken(),
                "{\"<img src=x onerror=alert(1)>\":1,\"passPct\":\"x\"}", "passPct"});
        cases.add(new String[] {"/api/attempts/" + attempt + "/submit", stu.accessToken(),
                "{\"answers\":[{\"questionId\":\"abc\",\"answer\":{}}]}", "questionId"});
        cases.add(new String[] {"/api/attempts/" + attempt + "/submit", stu.accessToken(), "{\"answers\":\"x\"}", "answers"});
        for (String[] c : cases) {
            var req = c[0].endsWith("/submit") ? org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(c[0])
                    : org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(c[0]);
            var res = send(req.contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(c[2]), c[1])
                    .andReturn().getResponse();
            String body = res.getContentAsString();
            assertEquals(400, res.getStatus(), c[2] + " -> " + body);
            for (String leak : new String[] {"java.", "com.pps", "com.fasterxml", "Exception", "	at ", "line:", "column:",
                    "Cannot deserialize", "<img"}) {
                assertFalse(body.contains(leak), c[2] + " lộ '" + leak + "': " + body);
            }
            JsonNode details = json.readTree(body).path("error").path("details");
            if (c[3].isEmpty()) {
                assertEquals(0, details.size(), body);
            } else {
                assertEquals(1, details.size(), body);
                assertEquals("Giá trị không hợp lệ", details.path(c[3]).asText(), body);
            }
        }
    }
}
