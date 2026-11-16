package com.pps.smartenglish.exercise;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.ai.AiTestSupport;
import com.pps.smartenglish.exercise.service.RegradeRunner;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** FR-EX-02, D-44: GRAMMAR_IDENTIFY chấm từng phần, điểm lượt là tổng credit (số lẻ), học viên thấy số cụm cần tìm. */
class IdentifyPartialCreditTest extends AiTestSupport {

    private static final String CMS = "/api/admin/cms";
    private static final String TOKENS = "{\"tokens\":[\"a\",\"b\",\"c\",\"d\",\"e\"]}";

    @Autowired RegradeRunner regrader;

    private static Map<String, Object> span(int i) {
        return Map.of("start", i, "end", i, "element", "PASSIVE");
    }

    /** n cụm đáp án: token i là một cụm PASSIVE. */
    private static String keyOf(int n) {
        var sb = new StringBuilder("{\"spans\":[");
        for (int i = 0; i < n; i++) {
            sb.append(i == 0 ? "" : ",").append("{\"start\":").append(i).append(",\"end\":").append(i).append(",\"element\":\"PASSIVE\"}");
        }
        return sb.append("]}").toString();
    }

    private static Map<String, Object> spansAnswer(long q, int... idx) {
        var spans = new ArrayList<Map<String, Object>>();
        for (int i : idx) {
            spans.add(span(i));
        }
        return Map.of("questionId", q, "answer", Map.of("spans", spans));
    }

    private long attemptOf(Account u, long bank, List<Map<String, Object>> answers) throws Exception {
        long a = idOf(postJson("/api/banks/" + bank + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
        postJson("/api/attempts/" + a + "/submit", u, Map.of("answers", answers)).andExpect(status().isOk());
        return a;
    }

    private BigDecimal decimal(String sql, Object... args) {
        return jdbc.queryForObject(sql, BigDecimal.class, args);
    }

    private BigDecimal creditOf(long attempt, long q) {
        return decimal("select credit from student_answers where attempt_id = ? and question_id = ?", attempt, q);
    }

    private Boolean correctOf(long attempt, long q) {
        return jdbc.queryForObject("select is_correct from student_answers where attempt_id = ? and question_id = ?", Boolean.class, attempt, q);
    }

    private BigDecimal scoreOf(long attempt) {
        return decimal("select score from student_exercise_attempts where id = ?", attempt);
    }

    private static void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    @Test
    void studentSeesSpanCountButNeverTheSpans() throws Exception {
        long bank = bank(true);
        question(bank, "GRAMMAR_IDENTIFY", 1, "Tìm cụm", TOKENS, keyOf(3));
        var u = student(0);
        getJson("/api/banks/" + bank, u).andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].payload.spanCount").value(3))
                .andExpect(jsonPath("$.questions[0].payload.tokens.length()").value(5))
                .andExpect(content().string(not(containsString("spans"))))
                .andExpect(content().string(not(containsString("PASSIVE"))));
        // lúc bắt đầu làm bài cũng không có đáp án
        postJson("/api/banks/" + bank + "/attempts", u, Map.of()).andExpect(status().isCreated())
                .andExpect(content().string(not(containsString("spans"))));
    }

    @Test
    void partialCreditSumsIntoFractionalScoreAndFullCreditIsCorrect() throws Exception {
        long bank = bank(true);
        long q34 = question(bank, "GRAMMAR_IDENTIFY", 1, "ba trên bốn", TOKENS, keyOf(4));
        long qExtra = question(bank, "GRAMMAR_IDENTIFY", 2, "đủ bốn thừa một", TOKENS, keyOf(4));
        long qFull = question(bank, "GRAMMAR_IDENTIFY", 3, "đủ", TOKENS, keyOf(2));
        long qBlank = question(bank, "GRAMMAR_IDENTIFY", 4, "bỏ trống", TOKENS, keyOf(2));
        long qBad = question(bank, "GRAMMAR_IDENTIFY", 5, "sai định dạng", TOKENS, keyOf(2));
        long qDup = question(bank, "GRAMMAR_IDENTIFY", 6, "trùng lặp", TOKENS, keyOf(2));
        long qBuild = question(bank, "GRAMMAR_BUILD", 7, "xếp khối",
                "{\"blocks\":[{\"id\":\"b2\",\"text\":\"m\"},{\"id\":\"b1\",\"text\":\"n\"}]}", "{\"order\":[\"b1\",\"b2\"]}");
        var u = student(0);
        long a = attemptOf(u, bank, List.of(
                spansAnswer(q34, 0, 1, 2),
                // 4 đúng + 1 thừa (end khác nên không khớp cụm nào)
                Map.of("questionId", qExtra, "answer", Map.of("spans", List.of(span(0), span(1), span(2), span(3),
                        Map.of("start", 0, "end", 2, "element", "PASSIVE")))),
                spansAnswer(qFull, 1, 0),
                Map.of("questionId", qBad, "answer", Map.of("spans", "oops")),
                spansAnswer(qDup, 0, 0),
                Map.of("questionId", qBuild, "answer", Map.of("order", List.of("b1", "b2")))));

        assertDecimal("0.7500", creditOf(a, q34));
        assertEquals(Boolean.FALSE, correctOf(a, q34));
        assertDecimal("0.8000", creditOf(a, qExtra));
        assertEquals(Boolean.FALSE, correctOf(a, qExtra));
        assertDecimal("1", creditOf(a, qFull));
        assertEquals(Boolean.TRUE, correctOf(a, qFull));
        assertDecimal("0", creditOf(a, qBlank)); // không gửi câu trả lời
        assertDecimal("0", creditOf(a, qBad));
        assertDecimal("0.5000", creditOf(a, qDup)); // cụm trùng lặp chỉ tính một lần
        assertDecimal("1", creditOf(a, qBuild)); // GRAMMAR_BUILD vẫn chấm cả câu
        assertEquals(Boolean.TRUE, correctOf(a, qBuild));

        // 0.75 + 0.8 + 1 + 0 + 0 + 0.5 + 1 = 4.05 trên 7 câu
        assertDecimal("4.05", scoreOf(a));
        getJson("/api/attempts/" + a, u).andExpect(status().isOk()).andExpect(jsonPath("$.score").value(4.05))
                .andExpect(jsonPath("$.maxScore").value(7)).andExpect(jsonPath("$.results[0].isCorrect").value(false))
                .andExpect(jsonPath("$.results[2].isCorrect").value(true));
    }

    @Test
    void regradeRecomputesPartialCreditForIdentify() throws Exception {
        var plain = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = 'ADMIN'", plain.id());
        var admin = login(plain.email(), PASSWORD);
        long bank = bank(true);
        long q = idOf(postJson(CMS + "/banks/" + bank + "/questions", admin, Map.of("type", "GRAMMAR_IDENTIFY", "prompt", "Tìm cụm",
                "payload", json.readTree(TOKENS), "answerKey", json.readTree(keyOf(2)))).andExpect(status().isCreated()), "id");
        var stu = student(0);
        long a = attemptOf(stu, bank, List.of(spansAnswer(q, 0, 1)));
        assertDecimal("1.00", scoreOf(a));
        assertEquals(Boolean.TRUE, correctOf(a, q));

        // đáp án mới có 3 cụm: bài cũ đúng 2/3
        send(patch(CMS + "/questions/" + q).contentType(MediaType.APPLICATION_JSON)
                .content("{\"answerKey\":" + keyOf(3) + "}"), admin.accessToken()).andExpect(status().isOk());
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/admin/questions/" + q + "/regrade").contentType(MediaType.APPLICATION_JSON)
                .content("{\"answerVersion\":2}"), admin.accessToken()).andExpect(status().isAccepted());
        regrader.tick();

        assertDecimal("0.6667", creditOf(a, q));
        assertEquals(Boolean.FALSE, correctOf(a, q));
        assertDecimal("0.67", scoreOf(a));
        assertDecimal("0.67", decimal("select new_attempt_score from regrade_items where attempt_id = ?", a));
        assertDecimal("1.00", decimal("select old_attempt_score from regrade_items where attempt_id = ?", a));
        getJson("/api/attempts/" + a, stu).andExpect(jsonPath("$.score").value(0.67));
    }
}
