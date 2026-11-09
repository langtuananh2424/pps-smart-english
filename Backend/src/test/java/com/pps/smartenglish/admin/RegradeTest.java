package com.pps.smartenglish.admin;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.ai.AiTestSupport;
import com.pps.smartenglish.ai.dto.RewriteGrades;
import com.pps.smartenglish.ai.provider.AiCallException;
import com.pps.smartenglish.ai.service.RewriteResultSink;
import com.pps.smartenglish.exercise.service.RegradeRunner;
import com.pps.smartenglish.shared.event.AiGradingFinished;
import com.pps.smartenglish.shared.event.ExerciseScored;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.support.TransactionTemplate;

/** FR-ADM-02: sửa đáp án rồi chấm lại bài cũ (ADMIN xác nhận, worker chạy nền, không trừ lượt, không trừ XP). */
@Import(RegradeTest.Events.class)
class RegradeTest extends AiTestSupport {

    private static final String CMS = "/api/admin/cms";
    private static final String ADMIN = "/api/admin";

    @TestConfiguration
    static class Events {
        static final List<Object> SEEN = new CopyOnWriteArrayList<>();

        @EventListener
        public void on(AiGradingFinished e) { SEEN.add(e); }

        @EventListener
        public void on(ExerciseScored e) { SEEN.add(e); }
    }

    /** ExerciseScored phát khi chấm lại (initial = false); lần nộp bài ban đầu phát initial = true nên bị loại. */
    private static List<ExerciseScored> regradeScores() {
        return Events.SEEN.stream().filter(e -> e instanceof ExerciseScored).map(e -> (ExerciseScored) e).filter(e -> !e.initial()).toList();
    }

    @Autowired RegradeRunner regrader;
    @Autowired RewriteResultSink sink;
    @Autowired TransactionTemplate tx;

    /** DB H2 dùng chung giữa các test: run còn dở của test trước không được lọt vào tick của test này. */
    @BeforeEach
    void cleanRegrade() {
        Events.SEEN.clear();
        jdbc.update("update regrade_items set status = 'CANCELLED' where status in ('PENDING', 'AI_WAITING')");
        jdbc.update("update regrade_runs set status = 'DONE' where status = 'RUNNING'");
    }

    // ---- hàm dùng chung ----

    private Account withRole(String role) throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("update users set role = ? where id = ?", role, u.id());
        return login(u.email(), PASSWORD);
    }

    private ResultActions call(MockHttpServletRequestBuilder req, Account a, Object body) throws Exception {
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return a == null ? mvc.perform(req) : send(req, a.accessToken());
    }

    private long createQuestion(Account admin, long bank, Map<String, Object> body) throws Exception {
        return idOf(call(MockMvcRequestBuilders.post(CMS + "/banks/" + bank + "/questions"), admin, body).andExpect(status().isCreated()), "id");
    }

    private ResultActions editQuestion(Account admin, long q, Map<String, Object> body) throws Exception {
        return call(patch(CMS + "/questions/" + q), admin, body);
    }

    private static Map<String, Object> choice(String label, boolean correct) {
        return Map.of("label", label, "content", "Lựa chọn " + label, "isCorrect", correct);
    }

    private static Map<String, Object> mcqBody(String correct) {
        return Map.of("type", "CONNECTIVE_MCQ", "prompt", "Chọn từ nối", "choices", List.of(choice("A", "A".equals(correct)), choice("B", "B".equals(correct))));
    }

    private static Map<String, Object> mcqChoices(String correct) {
        return Map.of("choices", List.of(choice("A", "A".equals(correct)), choice("B", "B".equals(correct))));
    }

    private static Map<String, Object> clozeBody(String word) {
        return Map.of("type", "CONNECTIVE_CLOZE", "prompt", "Điền từ", "payload", Map.of("text", "I like {b1}.", "blanks", List.of("b1")),
                "answerKey", Map.of("blanks", Map.of("b1", List.of(word))));
    }

    private static Map<String, Object> rewriteBody(String... accepted) {
        return Map.of("type", "CONNECTIVE_REWRITE", "prompt", "Viết lại với although",
                "payload", Map.of("sentence", "It was hard. We won.", "requiredWord", "although"),
                "answerKey", Map.of("acceptedAnswers", List.of(accepted)));
    }

    private long choiceId(long q, String label) {
        return jdbc.queryForObject("select id from question_choices where question_id = ? and label = ?", Long.class, q, label);
    }

    private Map<String, Object> pick(long q, long choice) {
        return Map.of("questionId", q, "answer", Map.of("choiceId", choice));
    }

    private Map<String, Object> text(long q, String text) {
        return Map.of("questionId", q, "answer", Map.of("text", text));
    }

    /** Học viên làm và nộp bài; trả attemptId. */
    private long submit(Account u, long bank, List<Map<String, Object>> answers) throws Exception {
        long attempt = idOf(postJson("/api/banks/" + bank + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
        postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers", answers)).andExpect(status().isOk());
        return attempt;
    }

    private int score(long attempt) {
        return count("select score from student_exercise_attempts where id = ?", attempt);
    }

    private Boolean correct(long attempt, long q) {
        return jdbc.queryForObject("select is_correct from student_answers where attempt_id = ? and question_id = ?", Boolean.class, attempt, q);
    }

    private long xp(Account u) {
        return jdbc.queryForObject("select xp from user_profiles where user_id = ?", Long.class, u.id());
    }

    private int xpEvents(Account u, String source) {
        return count("select count(*) from xp_events where user_id = ? and source = ?", u.id(), source);
    }

    private int streak(Account u) {
        return count("select streak_current from user_profiles where user_id = ?", u.id());
    }

    private ResultActions regrade(Account admin, long q, int version) throws Exception {
        return call(MockMvcRequestBuilders.post(ADMIN + "/questions/" + q + "/regrade"), admin, Map.of("answerVersion", version));
    }

    private long runOf(long q) {
        return jdbc.queryForObject("select max(id) from regrade_runs where question_id = ?", Long.class, q);
    }

    private JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private JsonNode run(Account admin, long q) throws Exception {
        return body(call(get(ADMIN + "/regrades/" + runOf(q)), admin, null).andExpect(status().isOk()));
    }

    private JsonNode itemOf(Account admin, long q, long attempt) throws Exception {
        var items = body(call(get(ADMIN + "/regrades/" + runOf(q) + "/items"), admin, null).andExpect(status().isOk())).get("data");
        for (JsonNode n : items) {
            if (n.get("attemptId").asLong() == attempt) {
                return n;
            }
        }
        throw new AssertionError("Không có item cho attempt " + attempt);
    }

    private String itemStatus(long attempt) {
        return jdbc.queryForObject("select status from regrade_items where attempt_id = ? order by id desc limit 1", String.class, attempt);
    }

    /**
     * Điều kiện chọn job chấm lại của CHÍNH câu hỏi q. ai_jobs dùng chung giữa các test nên không đếm toàn bảng: job chấm lại
     * của test khác (đã FAILED/DONE) vẫn nằm lại đó.
     */
    private static final String JOBS_OF_QUESTION = "regrade_item_id in (select i.id from regrade_items i "
            + "join regrade_runs r on r.id = i.run_id where r.question_id = ?)";

    private int regradeJobs(long q) {
        return count("select count(*) from ai_jobs where " + JOBS_OF_QUESTION, q);
    }

    // ---- quyền ----

    @Test
    void onlyAdminMayPreviewRegradeAndReadRuns() throws Exception {
        var admin = withRole("ADMIN");
        var editor = withRole("EDITOR");
        var stu = student(0);
        long bank = bank(true);
        long q = createQuestion(admin, bank, mcqBody("A"));
        submit(stu, bank, List.of(pick(q, choiceId(q, "A"))));
        editQuestion(admin, q, mcqChoices("B")).andExpect(status().isOk());

        String preview = ADMIN + "/questions/" + q + "/regrade-preview";
        for (Account denied : new Account[] {editor, stu}) {
            call(get(preview), denied, null).andExpect(status().isForbidden());
            regrade(denied, q, 2).andExpect(status().isForbidden());
            call(get(ADMIN + "/regrades"), denied, null).andExpect(status().isForbidden());
            call(get(ADMIN + "/regrades/1"), denied, null).andExpect(status().isForbidden());
            call(get(ADMIN + "/regrades/1/items"), denied, null).andExpect(status().isForbidden());
        }
        call(get(preview), null, null).andExpect(status().isUnauthorized());
        call(get(preview), admin, null).andExpect(status().isOk());
        regrade(admin, q, 2).andExpect(status().isAccepted());
        call(get(ADMIN + "/regrades?questionId=" + q), admin, null).andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(1)));
        call(get(ADMIN + "/regrades/" + runOf(q)), admin, null).andExpect(status().isOk());
        call(get(ADMIN + "/regrades/" + runOf(q) + "/items"), admin, null).andExpect(status().isOk());
        call(get(ADMIN + "/regrades/999999999"), admin, null).andExpect(status().isNotFound());
    }

    // ---- sửa câu và phiên bản ----

    @Test
    void editingAnswersBumpsVersionOnlyOnRealChangeAndKeepsChoiceIds() throws Exception {
        var admin = withRole("ADMIN");
        var stu = student(0);
        long bank = bank(true);
        long q = createQuestion(admin, bank, mcqBody("A"));
        long a = choiceId(q, "A");
        long b = choiceId(q, "B");
        submit(stu, bank, List.of(pick(q, a)));

        editQuestion(admin, q, mcqChoices("B")).andExpect(status().isOk()).andExpect(jsonPath("$.answerVersion").value(2))
                .andExpect(jsonPath("$.regradeRequired").value(true));
        assertEquals(a, choiceId(q, "A")); // id lựa chọn không đổi: student_answers lưu choiceId
        assertEquals(b, choiceId(q, "B"));
        editQuestion(admin, q, mcqChoices("B")).andExpect(jsonPath("$.answerVersion").value(2)); // gửi lại cùng dữ liệu
        editQuestion(admin, q, Map.of("prompt", "Đề mới")).andExpect(jsonPath("$.answerVersion").value(2));

        Map<String, Object> three = Map.of("choices", List.of(choice("A", false), choice("B", true), choice("C", false)));
        editQuestion(admin, q, three).andExpect(status().isOk()).andExpect(jsonPath("$.answerVersion").value(3));
        assertEquals(a, choiceId(q, "A"));
        assertEquals(b, choiceId(q, "B"));
        // đã có bài nộp: không bỏ nhãn đang có, không đổi dạng
        editQuestion(admin, q, mcqChoices("B")).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("has_submissions"));
        editQuestion(admin, q, Map.of("type", "WRITING", "payload", Map.of("task", "t", "requiredWords", 5), "choices", List.of()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("has_submissions"));

        // chưa có bài nộp thì sửa tự do, kể cả bỏ nhãn
        long fresh = createQuestion(admin, bank, mcqBody("A"));
        editQuestion(admin, fresh, Map.of("choices", List.of(choice("A", true), choice("C", false)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.regradeRequired").value(false));
    }

    @Test
    void writingCanBeEditedFreelyWithoutVersionOrRegrade() throws Exception {
        var admin = withRole("ADMIN");
        long bank = bank(true);
        long w = createQuestion(admin, bank, Map.of("type", "WRITING", "prompt", "Viết", "payload", Map.of("task", "t", "requiredWords", 50)));
        var stu = student(0);
        submit(stu, bank, List.of()); // có bài nộp (không trả lời)
        editQuestion(admin, w, Map.of("payload", Map.of("task", "Đề mới", "requiredWords", 80))).andExpect(status().isOk())
                .andExpect(jsonPath("$.answerVersion").value(1)).andExpect(jsonPath("$.regradeRequired").value(false));
        call(get(ADMIN + "/questions/" + w + "/regrade-preview"), admin, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("not_regradable"));
        regrade(admin, w, 1).andExpect(status().isBadRequest());
        assertEquals(0, count("select count(*) from regrade_runs where question_id = ?", w));
    }

    // ---- xem trước và xác nhận ----

    @Test
    void previewCountsSubmittedAnswersAndAiUpperBound() throws Exception {
        var admin = withRole("ADMIN");
        long bank = bank(true);
        long q = createQuestion(admin, bank, Map.of("type", "GRAMMAR_REWRITE", "prompt", "Viết lại bị động",
                "payload", Map.of("sentence", "They built it.", "requiredElement", "PASSIVE")));
        var s1 = student(1);
        var s2 = student(1);
        var s3 = student(1);
        var s4 = student(1);
        submit(s1, bank, List.of(text(q, "It was built.")));
        submit(s2, bank, List.of(text(q, "   ")));
        submit(s3, bank, List.of()); // bỏ trống
        postJson("/api/banks/" + bank + "/attempts", s4, Map.of()).andExpect(status().isCreated()); // chưa nộp: không tính
        runner.runDueNow();
        call(get(ADMIN + "/questions/" + q + "/regrade-preview"), admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.questionId").value(q)).andExpect(jsonPath("$.answerVersion").value(1))
                .andExpect(jsonPath("$.affectedAnswers").value(3)).andExpect(jsonPath("$.affectedAttempts").value(3))
                .andExpect(jsonPath("$.maxAiCalls").value(2)).andExpect(jsonPath("$.regradeRequired").value(true)); // "   " vẫn tính: cận trên

        long mcq = createQuestion(admin, bank, mcqBody("A"));
        call(get(ADMIN + "/questions/" + mcq + "/regrade-preview"), admin, null).andExpect(jsonPath("$.affectedAnswers").value(0))
                .andExpect(jsonPath("$.maxAiCalls").value(0)).andExpect(jsonPath("$.regradeRequired").value(false));
        call(get(ADMIN + "/questions/999999999/regrade-preview"), admin, null).andExpect(status().isNotFound());
    }

    @Test
    void confirmingNeedsTheCurrentVersionAndIsIdempotent() throws Exception {
        var admin = withRole("ADMIN");
        var stu = student(0);
        long bank = bank(true);
        long q = createQuestion(admin, bank, mcqBody("A"));
        submit(stu, bank, List.of(pick(q, choiceId(q, "A"))));
        long empty = createQuestion(admin, bank, mcqBody("A")); // thêm sau lần nộp: chưa có bài nộp nào
        regrade(admin, empty, 1).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("no_submissions"));
        editQuestion(admin, q, mcqChoices("B")).andExpect(status().isOk());

        regrade(admin, q, 1).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("stale_version"));
        assertEquals(0, count("select count(*) from regrade_runs where question_id = ?", q));
        regrade(admin, q, 2).andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("RUNNING")).andExpect(jsonPath("$.total").value(1));
        long runId = runOf(q);
        regrade(admin, q, 2).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(runId)); // đang chạy: trả run cũ
        regrader.tick();
        regrade(admin, q, 2).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(runId)).andExpect(jsonPath("$.status").value("DONE"));
        assertEquals(1, count("select count(*) from regrade_runs where question_id = ?", q));
        assertEquals(1, count("select count(*) from regrade_items where run_id = ?", runId));
        call(get(ADMIN + "/questions/" + q + "/regrade-preview"), admin, null).andExpect(jsonPath("$.regradeRequired").value(false));
    }

    // ---- chấm lại tự động ----

    @Test
    void mcqFlipsBothWaysScoresMoveByDeltaAndItemsKeepOldValues() throws Exception {
        var admin = withRole("ADMIN");
        var s1 = student(0);
        var s2 = student(0);
        long bank = bank(true);
        long q1 = createQuestion(admin, bank, mcqBody("A"));
        long q2 = createQuestion(admin, bank, mcqBody("A"));
        long a1 = submit(s1, bank, List.of(pick(q1, choiceId(q1, "A")), pick(q2, choiceId(q2, "A")))); // 2/2
        long a2 = submit(s2, bank, List.of(pick(q1, choiceId(q1, "B")), pick(q2, choiceId(q2, "A")))); // 1/2
        editQuestion(admin, q1, mcqChoices("B")).andExpect(status().isOk());
        regrade(admin, q1, 2).andExpect(status().isAccepted());
        regrader.tick();

        assertEquals(1, score(a1)); // chỉ q1 đổi: điểm cộng dồn theo delta, q2 giữ nguyên
        assertEquals(2, score(a2));
        assertEquals(Boolean.FALSE, correct(a1, q1));
        assertEquals(Boolean.TRUE, correct(a2, q1));
        assertEquals(Boolean.TRUE, correct(a1, q2));
        var run = run(admin, q1);
        assertEquals("DONE", run.get("status").asText());
        assertEquals(2, run.get("total").asInt());
        assertEquals(2, run.get("changed").asInt());
        assertEquals(0, run.get("failed").asInt());
        var item1 = itemOf(admin, q1, a1);
        assertEquals("DONE", item1.get("status").asText());
        assertTrue(item1.get("oldIsCorrect").asBoolean());
        assertFalse(item1.get("newIsCorrect").asBoolean());
        assertEquals(2, item1.get("oldScore").asInt());
        assertEquals(1, item1.get("newScore").asInt());
        assertEquals(s1.id(), item1.get("userId").asLong());
        var item2 = itemOf(admin, q1, a2);
        assertEquals(1, item2.get("oldScore").asInt());
        assertEquals(2, item2.get("newScore").asInt());

        // học viên chỉ thấy điểm mới, không có dữ liệu điểm cũ
        send(get("/api/attempts/" + a1), s1.accessToken()).andExpect(status().isOk()).andExpect(jsonPath("$.score").value(1))
                .andExpect(content().string(not(containsString("old")))).andExpect(content().string(not(containsString("regrade"))));
        // điểm tăng thì phát ExerciseScored initial=false, điểm giảm thì không
        var scored = regradeScores();
        assertEquals(1, scored.size());
        assertEquals(a2, scored.get(0).attemptId());
        assertEquals(2, scored.get(0).score());
        assertEquals(0, count("select count(*) from notifications where user_id in (?, ?)", s1.id(), s2.id())); // không thông báo
    }

    @Test
    void clozeKeyChangeRegradesBothWays() throws Exception {
        var admin = withRole("ADMIN");
        var s1 = student(0);
        var s2 = student(0);
        long bank = bank(true);
        long q = createQuestion(admin, bank, clozeBody("tea"));
        long a1 = submit(s1, bank, List.of(Map.of("questionId", q, "answer", Map.of("blanks", Map.of("b1", "tea")))));
        long a2 = submit(s2, bank, List.of(Map.of("questionId", q, "answer", Map.of("blanks", Map.of("b1", "coffee")))));
        assertEquals(1, score(a1));
        editQuestion(admin, q, Map.of("answerKey", Map.of("blanks", Map.of("b1", List.of("coffee"))))).andExpect(status().isOk());
        regrade(admin, q, 2).andExpect(status().isAccepted());
        regrader.tick();
        assertEquals(0, score(a1));
        assertEquals(1, score(a2));
        assertEquals(Boolean.TRUE, correct(a2, q));
        assertEquals(2, run(admin, q).get("changed").asInt());
    }

    @Test
    void firstTimeHighScoreGetsXpOnceDroppingScoreNeverTakesXpAndStreakStays() throws Exception {
        var admin = withRole("ADMIN");
        var stu = student(0);
        long bank = bank(true);
        long q = createQuestion(admin, bank, mcqBody("A"));
        long a = submit(stu, bank, List.of(pick(q, choiceId(q, "B")))); // sai: 0/1
        assertEquals(10, xp(stu));
        assertEquals(0, xpEvents(stu, "EXERCISE_HIGH"));
        int streakBefore = streak(stu);

        editQuestion(admin, q, mcqChoices("B")).andExpect(status().isOk());
        regrade(admin, q, 2).andExpect(status().isAccepted());
        regrader.tick();
        assertEquals(1, score(a));
        assertEquals(20, xp(stu)); // EXERCISE_HIGH cộng đúng một lần
        assertEquals(1, xpEvents(stu, "EXERCISE_HIGH"));
        assertEquals(1, xpEvents(stu, "EXERCISE"));
        assertEquals(streakBefore, streak(stu));

        editQuestion(admin, q, mcqChoices("A")).andExpect(status().isOk());
        regrade(admin, q, 3).andExpect(status().isAccepted());
        regrader.tick();
        assertEquals(0, score(a));
        assertEquals(20, xp(stu)); // điểm giảm: không trừ XP, không thu hồi thưởng
        assertEquals(1, xpEvents(stu, "EXERCISE_HIGH"));

        editQuestion(admin, q, mcqChoices("B")).andExpect(status().isOk());
        regrade(admin, q, 4).andExpect(status().isAccepted());
        regrader.tick();
        assertEquals(1, score(a));
        assertEquals(20, xp(stu)); // đạt lại: không cộng trùng
        assertEquals(1, xpEvents(stu, "EXERCISE_HIGH"));
        assertEquals(streakBefore, streak(stu));
    }

    // ---- câu viết lại: luật và AI ----

    @Test
    void rewriteUsesRulesThenAiWithoutTouchingQuotaOrNotifyingStudents() throws Exception {
        var admin = withRole("ADMIN");
        long bank = bank(true);
        long q = createQuestion(admin, bank, rewriteBody("Although it was hard, we won."));
        var s1 = student(1);
        var s2 = student(1);
        var s3 = student(1);
        long a1 = submit(s1, bank, List.of(text(q, "Although it was hard, we won."))); // luật: đúng
        long a2 = submit(s2, bank, List.of(text(q, "Although tough, we won.")));       // AI: đúng
        long a3 = submit(s3, bank, List.of(text(q, "Although wrong thing.")));         // AI: sai
        runner.runDueNow();
        assertEquals(1, score(a1));
        assertEquals(1, score(a2));
        assertEquals(0, score(a3));
        int ledger = count("select count(*) from ai_quota_ledger");
        int b2 = balance(s2);
        int b3 = balance(s3);
        Events.SEEN.clear();

        editQuestion(admin, q, Map.of("answerKey", Map.of("acceptedAnswers", List.of("Although it was hard, we won.", "Although wrong thing."))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.answerVersion").value(2));
        call(get(ADMIN + "/questions/" + q + "/regrade-preview"), admin, null).andExpect(jsonPath("$.maxAiCalls").value(3));
        regrade(admin, q, 2).andExpect(status().isAccepted());
        regrader.tick();

        // a1 và a3 do luật quyết ngay; a2 không khớp câu mẫu nào nên chờ AI
        assertEquals(1, score(a3));
        assertEquals("DONE", itemStatus(a1));
        assertEquals("AI_WAITING", itemStatus(a2));
        assertEquals(1, regradeJobs(q));
        var job = jdbc.queryForMap("select charged, user_id, kind, attempt_id from ai_jobs where " + JOBS_OF_QUESTION, q);
        assertEquals(false, job.get("CHARGED"));
        assertEquals("REWRITE_GRADE", job.get("KIND"));
        assertEquals(admin.id(), ((Number) job.get("USER_ID")).longValue());
        assertEquals(a2, ((Number) job.get("ATTEMPT_ID")).longValue());
        send(get("/api/attempts/" + a3), s3.accessToken()).andExpect(jsonPath("$.results[0].feedback").value("Chính xác."))
                .andExpect(jsonPath("$.results[0].isCorrect").value(true));
        send(get("/api/attempts/" + a2), s2.accessToken()).andExpect(jsonPath("$.pendingJobs", hasSize(0))); // học viên không thấy việc của Admin
        assertEquals(0, count("select count(*) from student_answer_grading where answer_id = (select id from student_answers where attempt_id = ?) "
                + "and grader = 'AI'", a3)); // nhận xét AI cũ bị thay bằng kết quả luật
        assertTrue(jdbc.queryForObject("select cast(old_result as varchar(10000)) from regrade_items where attempt_id = ?", String.class, a3)
                .contains("AI"));

        // AI chấm lại câu của a2 ra sai
        fake.override(RewriteGrades.class, new RewriteGrades(List.of(new RewriteGrades.Item(q, false, "Chưa đúng.", "Mẫu"))));
        assertEquals(1, runner.runDueNow());
        regrader.tick();
        assertEquals(0, score(a2));
        assertEquals(Boolean.FALSE, correct(a2, q));
        var run = run(admin, q);
        assertEquals("DONE", run.get("status").asText());
        assertEquals(3, run.get("total").asInt());
        assertEquals(2, run.get("changed").asInt());

        // không trừ lượt, không hoàn lượt, không sổ cái mới; usage vẫn được ghi để báo cáo chi phí
        assertEquals(b2, balance(s2));
        assertEquals(b3, balance(s3));
        assertEquals(ledger, count("select count(*) from ai_quota_ledger"));
        long jobId = jdbc.queryForObject("select id from ai_jobs where " + JOBS_OF_QUESTION, Long.class, q);
        assertEquals(1, count("select count(*) from ai_grading_token_usage where job_id = ?", jobId));
        // job chấm lại không phát AiGradingFinished; điểm a3 tăng nên có đúng một ExerciseScored initial=false
        assertTrue(Events.SEEN.stream().noneMatch(e -> e instanceof AiGradingFinished));
        assertEquals(1, regradeScores().size());
        assertEquals(0, count("select count(*) from notifications where user_id in (?, ?, ?)", s1.id(), s2.id(), s3.id()));

        // kết quả AI đến lần hai cho item đã xong: không áp lại
        var late = new RewriteGrades.Item(q, true, "Tốt.", "Mẫu");
        long item = jdbc.queryForObject("select id from regrade_items where attempt_id = ?", Long.class, a2);
        tx.executeWithoutResult(s -> sink.applyRegrade(item, List.of(late)));
        assertEquals(0, score(a2));
        assertEquals(Boolean.FALSE, correct(a2, q));
    }

    @Test
    void regradeJobsDoNotCountAgainstStudentCapacityOrRateLimit() throws Exception {
        var admin = withRole("ADMIN");
        var stu = student(2);
        long bank = bank(true);
        long q = createQuestion(admin, bank, rewriteBody("Although it was hard, we won."));
        submit(stu, bank, List.of(text(q, "Although tough, we won.")));
        runner.runDueNow();
        editQuestion(admin, q, Map.of("answerKey", Map.of("acceptedAnswers", List.of("Although it was hard, we won.", "x although y")))).andExpect(status().isOk());
        regrade(admin, q, 2).andExpect(status().isAccepted());
        long item = jdbc.queryForObject("select id from regrade_items where run_id = ?", Long.class, runOf(q));
        // hàng đợi toàn hệ thống đầy việc chấm lại (vượt xa app.ai.max-pending = 200)
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < 205; i++) {
            rows.add(new Object[] {admin.id(), item});
        }
        jdbc.batchUpdate("insert into ai_jobs (user_id, kind, status, regrade_item_id, charged) values (?, 'REWRITE_GRADE', 'PENDING', ?, false)", rows);
        guard.requireCapacity(); // không ném 503
        guard.resetRateLimit(stu.id());
        long bank2 = bank(true);
        long q2 = createQuestion(admin, bank2, rewriteBody("Although it was hard, we won."));
        long attempt = idOf(postJson("/api/banks/" + bank2 + "/attempts", stu, Map.of()).andExpect(status().isCreated()), "id");
        postJson("/api/attempts/" + attempt + "/submit", stu, Map.of("answers", List.of(text(q2, "Although tough, we won."))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pendingJobs", hasSize(1))); // học viên vẫn nộp được, không 503
        jdbc.update("update ai_jobs set status = 'FAILED' where " + JOBS_OF_QUESTION, q);
    }

    @Test
    void maxAiPendingIsRespectedAndWorkResumesWhenRoomFrees() throws Exception {
        var admin = withRole("ADMIN");
        var stu = student(1);
        long bank = bank(true);
        long q = createQuestion(admin, bank, rewriteBody("Although it was hard, we won."));
        long a = submit(stu, bank, List.of(text(q, "Although tough, we won.")));
        runner.runDueNow();
        editQuestion(admin, q, Map.of("answerKey", Map.of("acceptedAnswers", List.of("Although it was hard, we won.", "Although other, we won.")))).andExpect(status().isOk());
        regrade(admin, q, 2).andExpect(status().isAccepted());
        long item = jdbc.queryForObject("select id from regrade_items where run_id = ?", Long.class, runOf(q));
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < 20; i++) { // đúng bằng app.regrade.max-ai-pending
            rows.add(new Object[] {admin.id(), item});
        }
        jdbc.batchUpdate("insert into ai_jobs (user_id, kind, status, regrade_item_id, charged) values (?, 'REWRITE_GRADE', 'PENDING', ?, false)", rows);
        regrader.tick();
        assertEquals(20, regradeJobs(q)); // không tạo thêm job
        assertEquals("PENDING", itemStatus(a));
        jdbc.update("update ai_jobs set status = 'FAILED' where " + JOBS_OF_QUESTION, q);
        regrader.tick();
        assertEquals("AI_WAITING", itemStatus(a));
        assertEquals(21, regradeJobs(q));
        jdbc.update("update regrade_items set status = 'CANCELLED' where id = ?", item); // dọn: không để job rác chạy ở test khác
    }

    @Test
    void failedAiJobFailsTheItemKeepsScoreAndConfirmingAgainRerunsOnlyThatPart() throws Exception {
        var admin = withRole("ADMIN");
        var stu = student(1);
        long bank = bank(true);
        long q = createQuestion(admin, bank, rewriteBody("Although it was hard, we won."));
        long a = submit(stu, bank, List.of(text(q, "Although tough, we won.")));
        runner.runDueNow();
        assertEquals(1, score(a));
        editQuestion(admin, q, Map.of("answerKey", Map.of("acceptedAnswers", List.of("Although it was hard, we won.", "Although other, we won."))))
                .andExpect(status().isOk());
        regrade(admin, q, 2).andExpect(status().isAccepted());
        regrader.tick();
        assertEquals("AI_WAITING", itemStatus(a));

        fake.failNext(5, new AiCallException(false, "boom", null, null));
        runner.runDueNow();
        regrader.tick();
        assertEquals("FAILED", itemStatus(a));
        assertEquals(1, score(a)); // điểm không đổi
        var failedRun = run(admin, q);
        assertEquals("DONE", failedRun.get("status").asText());
        assertEquals(1, failedRun.get("failed").asInt());
        assertEquals(balance(stu), 0);

        fake.reset();
        regrade(admin, q, 2).andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("RUNNING")); // chạy lại phần lỗi
        assertEquals("PENDING", itemStatus(a));
        regrade(admin, q, 2).andExpect(status().isOk()); // bấm tiếp khi đang chạy: không tạo gì thêm
        regrader.tick();
        assertEquals("AI_WAITING", itemStatus(a));
        runner.runDueNow();
        regrader.tick();
        assertEquals("DONE", itemStatus(a));
        var done = run(admin, q);
        assertEquals("DONE", done.get("status").asText());
        assertEquals(0, done.get("failed").asInt());
        assertEquals(1, count("select count(*) from regrade_runs where question_id = ?", q));
        regrade(admin, q, 2).andExpect(status().isOk()); // DONE không lỗi: trả run cũ
    }

    @Test
    void editingAgainMidwaySupersedesTheRunAndLateAiResultChangesNothing() throws Exception {
        var admin = withRole("ADMIN");
        var stu = student(1);
        long bank = bank(true);
        long q = createQuestion(admin, bank, rewriteBody("Although it was hard, we won."));
        long a = submit(stu, bank, List.of(text(q, "Although tough, we won.")));
        runner.runDueNow();
        editQuestion(admin, q, Map.of("answerKey", Map.of("acceptedAnswers", List.of("Although it was hard, we won.", "Although other, we won.")))).andExpect(status().isOk());
        regrade(admin, q, 2).andExpect(status().isAccepted());
        regrader.tick();
        assertEquals("AI_WAITING", itemStatus(a));

        editQuestion(admin, q, Map.of("answerKey", Map.of("acceptedAnswers", List.of("Although it was hard, we won.", "Although third, we won."))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.answerVersion").value(3));
        regrader.tick(); // thấy phiên bản đã cũ: run SUPERSEDED, item CANCELLED
        assertEquals("SUPERSEDED", run(admin, q).get("status").asText());
        assertEquals("CANCELLED", itemStatus(a));

        fake.override(RewriteGrades.class, new RewriteGrades(List.of(new RewriteGrades.Item(q, false, "Chưa đúng.", "Mẫu"))));
        assertEquals(1, runner.runDueNow()); // job đến muộn: AI vẫn tốn token nhưng không đổi điểm
        regrader.tick();
        assertEquals(1, score(a));
        assertEquals(Boolean.TRUE, correct(a, q));
        assertEquals("CANCELLED", itemStatus(a));
        long jobId = jdbc.queryForObject("select id from ai_jobs where " + JOBS_OF_QUESTION, Long.class, q);
        assertEquals(1, count("select count(*) from ai_grading_token_usage where job_id = ?", jobId));
        call(get(ADMIN + "/questions/" + q + "/regrade-preview"), admin, null).andExpect(jsonPath("$.regradeRequired").value(true)); // chưa chấm v3

        regrade(admin, q, 3).andExpect(status().isAccepted()); // run mới cho phiên bản mới
        assertEquals(2, count("select count(*) from regrade_runs where question_id = ?", q));
    }

    @Test
    void attemptWithFirstGradingJobStillRunningIsDeferred() throws Exception {
        var admin = withRole("ADMIN");
        var stu = student(1);
        long bank = bank(true);
        long q = createQuestion(admin, bank, rewriteBody("Although it was hard, we won."));
        long a = submit(stu, bank, List.of(text(q, "Although tough, we won."))); // job chấm lần đầu đang PENDING
        editQuestion(admin, q, Map.of("answerKey", Map.of("acceptedAnswers", List.of("Although tough, we won.")))).andExpect(status().isOk());
        regrade(admin, q, 2).andExpect(status().isAccepted());
        regrader.tick();
        assertEquals("PENDING", itemStatus(a)); // hoãn: hai bên không cùng ghi
        assertEquals(0, regradeJobs(q));
        assertEquals("RUNNING", run(admin, q).get("status").asText());

        runner.runDueNow(); // chấm lần đầu xong
        assertEquals(1, score(a));
        regrader.tick();
        assertEquals("DONE", itemStatus(a)); // câu mẫu mới khớp: luật quyết, không cần AI
        assertEquals(1, score(a));
        assertEquals(0, regradeJobs(q));
        assertEquals("DONE", run(admin, q).get("status").asText());
    }

    @Test
    void tickIsIdempotentAndNeverAppliesAnItemTwice() throws Exception {
        var admin = withRole("ADMIN");
        var stu = student(0);
        long bank = bank(true);
        long q = createQuestion(admin, bank, mcqBody("A"));
        long a = submit(stu, bank, List.of(pick(q, choiceId(q, "A"))));
        editQuestion(admin, q, mcqChoices("B")).andExpect(status().isOk());
        regrade(admin, q, 2).andExpect(status().isAccepted());
        for (int i = 0; i < 3; i++) {
            regrader.tick();
        }
        assertEquals(0, score(a)); // trừ đúng một lần
        Map<String, Integer> byStatus = new HashMap<>();
        jdbc.queryForList("select status, count(*) c from regrade_items where run_id = ? group by status", runOf(q))
                .forEach(r -> byStatus.put((String) r.get("STATUS"), ((Number) r.get("C")).intValue()));
        assertEquals(Map.of("DONE", 1), byStatus);
        assertEquals(0, regradeScores().size()); // điểm giảm: không phát sự kiện
    }
}
