package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** #69, FR-EX-10, D-136: kết quả Nghe ép chỉ có số câu đúng, câu sai nhiều sau N lượt, xem script, ngưỡng theo part. */
class ForcedListeningApiTest extends ListeningTestSupport {

    private static final String[] LEAKS = {"isCorrect", "correctAnswer", "explanation", "breakdown", "wrongQuestions", "results",
            SECRET_EXPLANATION};

    private static void assertNoAnswerLeak(JsonNode n) {
        String raw = n.toString();
        for (String leak : LEAKS) {
            assertFalse(raw.contains(leak), "lộ " + leak + " trong " + raw);
        }
    }

    private Account admin;

    private Account admin() throws Exception {
        if (admin == null) {
            admin = withRole("ADMIN");
        }
        return admin;
    }

    private void rule(World w, Map<String, Object> body) throws Exception {
        put("/api/admin/cms/banks/" + w.bank() + "/forced-listening", admin(), body).andExpect(status().isOk());
    }

    @Test
    void submitResponseAndGetAttemptOnlyCountCorrectAnswers() throws Exception {
        var w = world("LOCKED", null);
        var u = student(0);
        var sub = submitWithId(u, w, true, true, false, false);

        var forced = sub.body().get("forced");
        assertNoAnswerLeak(sub.body());
        assertEquals(2, forced.get("correctCount").asInt());
        assertEquals(4, forced.get("totalCount").asInt());
        assertEquals(50, forced.get("percent").asInt());
        assertEquals(70, forced.get("passPct").asInt());
        assertFalse(forced.get("passed").asBoolean());
        assertEquals(2, forced.at("/parts/0/correct").asInt());
        assertEquals(0, forced.at("/parts/1/correct").asInt());
        assertEquals(1, forced.get("attemptNo").asInt());
        assertEquals("COUNT_ONLY", jdbc.queryForObject("select result_mode from student_exercise_attempts where id = ?", String.class, sub.id()));

        var again = get("/api/attempts/" + sub.id(), u, 200); // xem lại sau: breakdown cũng không lộ
        assertNoAnswerLeak(again);
        assertEquals(2, again.at("/forced/correctCount").asInt());
        assertFalse(again.has("breakdown"));

        var mine = get("/api/me/attempts", u, 200).toString();
        assertFalse(mine.contains("isCorrect") || mine.contains("correctAnswer"));
    }

    @Test
    void draftAndProgressNeverCarryVerdicts() throws Exception {
        var w = world("LOCKED", null);
        var u = student(0);
        long a = startAttempt(u, w);
        put("/api/attempts/" + a + "/draft", u, Map.of("answers", answers(w, true, false))).andExpect(status().isOk());
        assertNoAnswerLeak(get("/api/attempts/" + a + "/progress", u, 200));
        assertNoAnswerLeak(get("/api/attempts/" + a, u, 200));
        assertNoAnswerLeak(json.readTree(getJson("/api/banks/" + w.bank(), u).andReturn().getResponse().getContentAsString()));
    }

    @Test
    void normalBankStillReturnsFullResultAndBreakdown() throws Exception {
        var w = world("FREE", null);
        var u = student(0);
        var sub = submitWithId(u, w, true, false, false, false);
        assertEquals(4, sub.body().get("results").size());
        assertFalse(sub.body().has("forced"));
        assertTrue(get("/api/attempts/" + sub.id(), u, 200).has("breakdown"));
        assertEquals("FULL", jdbc.queryForObject("select result_mode from student_exercise_attempts where id = ?", String.class, sub.id()));
    }

    @Test
    void passUsesEitherTotalThresholdOrPartThresholdsNeverBoth() throws Exception {
        var w = world("LOCKED", null);
        var u = student(0);
        var url = "/api/admin/cms/banks/" + w.bank() + "/forced-listening";
        put(url, admin(), Map.of("passPctTotal", 50, "groupThresholds", List.of(Map.of("groupId", w.g1(), "passPct", 100))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("threshold_conflict"));
        assertEquals(0, count("select count(*) from forced_listening_rules where bank_id = ?", w.bank()), "luật sai không được lưu");

        // theo part: đạt khi mọi part có ngưỡng đều đạt, bỏ qua ngưỡng cả bài (mặc định 70)
        rule(w, Map.of("groupThresholds", List.of(Map.of("groupId", w.g1(), "passPct", 100))));
        var partFails = submit(u, w, true, false, true, true).get("forced"); // tổng 75% nhưng part 1 chỉ 50% < 100%
        assertFalse(partFails.get("passed").asBoolean());
        assertEquals(100, partFails.at("/parts/0/passPct").asInt());
        assertFalse(partFails.at("/parts/0/passed").asBoolean());
        assertFalse(partFails.at("/parts/1").has("passPct"), "part không có ngưỡng riêng");
        assertFalse(partFails.has("passPct"), "theo part thì không có ngưỡng cả bài");
        assertTrue(submit(u, w, true, true, false, false).at("/forced/passed").asBoolean()); // part 1 đủ, tổng 50% < 70% vẫn đạt
        assertFalse(submit(u, w, false, false, false, false).at("/forced/passed").asBoolean());

        // đổi sang cả bài: ngưỡng part bị bỏ
        rule(w, Map.of("passPctTotal", 50));
        assertEquals(0, count("select count(*) from forced_listening_group_thresholds"
                + " where rule_id = (select id from forced_listening_rules where bank_id = ?)", w.bank()));
        var total = submit(u, w, true, true, false, false).get("forced"); // đúng 50%: biên đạt
        assertTrue(total.get("passed").asBoolean());
        assertEquals(50, total.get("passPct").asInt());
        assertFalse(total.at("/parts/0").has("passPct"));
        assertFalse(submit(u, w, true, false, false, false).at("/forced/passed").asBoolean());
    }

    @Test
    void countOnlyResponsesCarryNoScoreAnywhere() throws Exception {
        var w = world("LOCKED", null);
        var u = student(0);
        var sub = submitWithId(u, w, true, true, false, false);
        assertFalse(sub.body().has("score") || sub.body().has("maxScore"), sub.body().toString());
        var got = get("/api/attempts/" + sub.id(), u, 200);
        assertFalse(got.has("score") || got.has("maxScore"), got.toString());
        assertTrue(jdbc.queryForObject("select score from student_exercise_attempts where id = ?", java.math.BigDecimal.class, sub.id()) != null,
                "điểm vẫn lưu nội bộ cho giáo viên và thống kê");
        var mine = get("/api/me/attempts", u, 200).at("/data/0");
        assertTrue(mine.path("score").isNull() && mine.path("maxScore").isNull(), mine.toString()); // danh sách lượt luôn có khóa, giá trị null
        long open = startAttempt(u, w);
        put("/api/attempts/" + open + "/draft", u, Map.of("answers", answers(w, true))).andExpect(status().isOk());
        String progress = get("/api/attempts/" + open + "/progress", u, 200).toString();
        assertFalse(progress.contains("score") || progress.contains("credit"), progress);
        // hồ sơ năng lực không gộp lượt Nghe ép
        assertEquals(0, get("/api/me/learning-profile", u, 200).get("answered").asInt());
        // bình thường vẫn có điểm
        var f = world("FREE", null);
        assertTrue(submit(u, f, true, true, true, true).has("score"));
        assertFalse(get("/api/me/attempts", u, 200).at("/data/0/score").isNull());
    }

    @Test
    void unlimitedRetriesAndPassDoNotChangeSequentialUnlock() throws Exception {
        long c = insert("courses", Map.of("slug", "seq-" + UUID.randomUUID(), "title", "Khóa tuần tự", "status", "PUBLISHED", "sequential", true));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "A", "status", "PUBLISHED", "sort_order", 0));
        long ta = insert("topics", Map.of("level_id", lv, "title", "Unit A", "is_free_trial", false, "status", "PUBLISHED", "sort_order", 0));
        long tb = insert("topics", Map.of("level_id", lv, "title", "Unit B", "is_free_trial", false, "status", "PUBLISHED", "sort_order", 1));
        long bankA = insert("question_banks", Map.of("topic_id", ta, "title", "Nghe ép", "is_trial", false, "status", "PUBLISHED",
                "mode", "PRACTICE", "playback_mode", "LOCKED"));
        long bankB = insert("question_banks", Map.of("topic_id", tb, "title", "Unit kế", "is_trial", false, "status", "PUBLISHED", "mode", "PRACTICE"));
        long qa = question(bankA, "CONNECTIVE_MCQ", 1, "Câu A", "{}", "{}");
        insert("question_choices", Map.of("question_id", qa, "label", "B", "content", "đúng", "is_correct", true));
        long wrong = insert("question_choices", Map.of("question_id", qa, "label", "A", "content", "sai", "is_correct", false));
        question(bankB, "CONNECTIVE_MCQ", 1, "Câu B", "{}", "{}");
        var u = student(0);
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', "
                + "TIMESTAMP '2020-01-01 00:00:00', TIMESTAMP '2099-01-01 00:00:00')", u.id(), c);

        getJson("/api/banks/" + bankB, u).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("unit_locked"));
        for (int i = 0; i < 3; i++) { // chưa đạt thì làm lại không giới hạn
            long a = idOf(postJson("/api/banks/" + bankA + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
            postJson("/api/attempts/" + a + "/submit", u, Map.of("answers", List.of(Map.of("questionId", qa, "answer", Map.of("choiceId", wrong)))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.forced.passed").value(false));
        }
        // luật học tuần tự hiện có chỉ đòi nộp bài, không đòi đạt: Nghe ép không thêm ràng buộc nào
        getJson("/api/banks/" + bankB, u).andExpect(status().isOk());
        assertEquals(0, count("select count(*) from forced_listening_rules where bank_id = ?", bankA));
    }

    @Test
    void hardQuestionsAppearEveryNthAttemptAndScriptsFollowThem() throws Exception {
        var w = world("LOCKED", null);
        var u = student(0);
        rule(w, Map.of("hintEveryN", 2, "wrongThreshold", 2));
        jdbc.update("update questions set transcript_excerpt = 'Excerpt of q1' where id = ?", w.q()[1]);

        var first = submitWithId(u, w, false, false, true, true);
        assertFalse(first.body().at("/forced").has("hardQuestions"), "mới 1 lượt, chưa đến N=2");
        getJson("/api/attempts/" + first.id() + "/scripts/" + w.q()[0], u).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("script_locked"));

        var second = submitWithId(u, w, false, true, true, true); // q0 sai 2 lần, q1 sai 1 lần
        var hard = second.body().at("/forced/hardQuestions");
        assertEquals(1, hard.size());
        assertEquals(w.q()[0], hard.get(0).get("questionId").asLong());
        assertEquals(2, hard.get(0).get("wrongCount").asInt());
        assertNoAnswerLeak(second.body());

        // script của nhóm (không có excerpt riêng): HTML đã làm sạch
        getJson("/api/attempts/" + second.id() + "/scripts/" + w.q()[0], u).andExpect(status().isOk())
                .andExpect(jsonPath("$.html").value("<p>SCRIPT-PART-1</p>")).andExpect(jsonPath("$.text").doesNotExist());
        getJson("/api/attempts/" + second.id() + "/scripts/" + w.q()[0], u).andExpect(status().isOk()); // xem lại không ghi thêm
        assertEquals(1, count("select count(*) from script_views where attempt_id = ? and question_id = ? and user_id = ?",
                second.id(), w.q()[0], u.id()));
        getJson("/api/attempts/" + second.id() + "/scripts/" + w.q()[1], u).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("script_not_hard"));
        assertEquals(0, count("select count(*) from script_views where question_id = ?", w.q()[1]), "bị từ chối thì không ghi nhận");

        // kết quả của một lượt không đổi khi xem lại sau này; lượt 3 chưa đến lượt N
        var third = submitWithId(u, w, false, false, true, true);
        assertFalse(third.body().at("/forced").has("hardQuestions"));
        assertEquals(1, get("/api/attempts/" + second.id(), u, 200).at("/forced/hardQuestions").size());
        assertFalse(get("/api/attempts/" + first.id(), u, 200).at("/forced").has("hardQuestions"));
        // lượt 4: q0 sai 4 lần, q1 sai 3 lần (đều >= 2)
        var fourth = submitWithId(u, w, false, false, true, true);
        assertEquals(2, fourth.body().at("/forced/hardQuestions").size());
        assertEquals(4, fourth.body().at("/forced/hardQuestions/0/wrongCount").asInt());
        getJson("/api/attempts/" + fourth.id() + "/scripts/" + w.q()[1], u).andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Excerpt of q1")).andExpect(jsonPath("$.html").doesNotExist());
    }

    @Test
    void scriptRulesRejectEveryOtherCase() throws Exception {
        var w = world("LOCKED", null);
        var other = world("LOCKED", null);
        var u = student(0);
        rule(w, Map.of("hintEveryN", 1, "wrongThreshold", 1));
        long open = startAttempt(u, w);
        getJson("/api/attempts/" + open + "/scripts/" + w.q()[0], u).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("attempt_not_submitted"));

        var done = submitWithId(u, w, false, true, true, true);
        getJson("/api/attempts/" + done.id() + "/scripts/" + w.q()[0], student(0)).andExpect(status().isNotFound()); // lượt người khác
        getJson("/api/attempts/" + done.id() + "/scripts/" + other.q()[0], u).andExpect(status().isNotFound()); // câu bộ khác
        getJson("/api/attempts/" + done.id() + "/scripts/abc", u).andExpect(status().isNotFound());
        getJson("/api/attempts/999999/scripts/" + w.q()[0], u).andExpect(status().isNotFound());
        getJson("/api/attempts/" + done.id() + "/scripts/" + w.q()[0], u).andExpect(status().isOk());

        var free = world("FREE", null); // lượt thường không có đường xem script
        var f = submitWithId(u, free, false, false, false, false);
        getJson("/api/attempts/" + f.id() + "/scripts/" + free.q()[0], u).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("script_not_available"));

        jdbc.update("update question_groups set transcript_html = null where id = ?", w.g1());
        var noScript = submitWithId(u, w, false, true, true, true);
        getJson("/api/attempts/" + noScript.id() + "/scripts/" + w.q()[0], u).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("script_not_found"));
        assertEquals(0, count("select count(*) from script_views where attempt_id = ?", noScript.id()));
    }

    @Test
    void editedAnswerKeyDoesNotCountOldWrongAnswersAsHard() throws Exception {
        var w = world("LOCKED", null);
        var u = student(0);
        rule(w, Map.of("hintEveryN", 2, "wrongThreshold", 2));
        submit(u, w, false, true, true, true);
        jdbc.update("update questions set answer_version = 2 where id = ?", w.q()[0]); // đáp án đã sửa: lần sai cũ không tính oan
        var second = submit(u, w, false, true, true, true);
        assertEquals(0, second.at("/forced/hardQuestions").size());
        assertTrue(second.at("/forced").has("hardQuestions"), "đến lượt N nên danh sách có mặt, chỉ là rỗng");
    }

    @Test
    void anotherStudentsHistoryDoesNotMixIntoMyCounts() throws Exception {
        var w = world("LOCKED", null);
        rule(w, Map.of("hintEveryN", 2, "wrongThreshold", 1));
        var a = student(0);
        var b = student(0);
        submit(a, w, false, false, false, false);
        submit(b, w, false, false, false, false);
        var second = submit(b, w, true, true, true, true);
        assertEquals(2, second.at("/forced/attemptNo").asInt());
        assertEquals(4, second.at("/forced/hardQuestions").size());
        assertEquals(1, second.at("/forced/hardQuestions/0/wrongCount").asInt(), "chỉ tính lượt của chính học viên");
    }
}
