package com.pps.smartenglish.exercise;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.support.ApiTestBase;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

class ExerciseTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";

    /** Bank mẫu: 6 câu tự chấm (q[0..5]) rồi 1 câu AI (q[6]); mcqRight/mcqWrong là id lựa chọn. */
    private record Bank(long id, long[] q, long mcqRight, long mcqWrong) {}

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private long bankRow(boolean topicFree, boolean bankTrial) {
        long c = insert("courses", Map.of("slug", "ex-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A"));
        long t = insert("topics", Map.of("level_id", lv, "title", "Topic", "is_free_trial", topicFree));
        return insert("question_banks", Map.of("topic_id", t, "title", "Bộ câu hỏi", "is_trial", bankTrial));
    }

    /** H2: json phải chèn bằng FORMAT JSON, nếu không sẽ thành chuỗi JSON. */
    private long question(long bankId, String type, int order, String payload, String key, String explanation) {
        var kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("insert into questions (bank_id, type, prompt, payload, answer_key, explanation, sort_order)"
                    + " values (?, ?, ?, ? format json, ? format json, ?, ?)", new String[] {"id"});
            ps.setLong(1, bankId);
            ps.setString(2, type);
            ps.setString(3, "Đề " + type);
            ps.setString(4, payload);
            ps.setString(5, key);
            ps.setString(6, explanation);
            ps.setInt(7, order);
            return ps;
        }, kh);
        return kh.getKey().longValue();
    }

    private Bank fullBank(boolean topicFree, boolean bankTrial) {
        long b = bankRow(topicFree, bankTrial);
        long[] q = new long[7];
        q[0] = question(b, "CONNECTIVE_MCQ", 1, "{}", "{}", "EXPL-SECRET-1");
        long right = insert("question_choices", Map.of("question_id", q[0], "label", "B", "content", "Nevertheless", "is_correct", true, "explanation", "CHOICE-EXPL-SECRET"));
        long wrong = insert("question_choices", Map.of("question_id", q[0], "label", "A", "content", "Therefore", "is_correct", false));
        q[1] = question(b, "CONNECTIVE_MATCH", 2, "{\"left\":[{\"id\":\"l1\",\"text\":\"x\"},{\"id\":\"l2\",\"text\":\"y\"}],\"right\":[{\"id\":\"r1\",\"text\":\"p\"},{\"id\":\"r2\",\"text\":\"q\"}]}",
                "{\"pairs\":{\"l1\":\"r2\",\"l2\":\"r1\"}}", "EXPL-SECRET-2");
        q[2] = question(b, "CONNECTIVE_CLOZE", 3, "{\"text\":\"A {b1} B {b2}\",\"blanks\":[\"b1\",\"b2\"]}",
                "{\"blanks\":{\"b1\":[\"because\",\"since\"],\"b2\":[\"however\"]}}", "EXPL-SECRET-3");
        q[3] = question(b, "GRAMMAR_IDENTIFY", 4, "{\"tokens\":[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\"]}",
                "{\"spans\":[{\"start\":2,\"end\":5,\"element\":\"RELATIVE_CLAUSE\"},{\"start\":0,\"end\":1,\"element\":\"PASSIVE\"}]}", "EXPL-SECRET-4");
        q[4] = question(b, "GRAMMAR_SPOT_FIX", 5, "{\"tokens\":[\"a\",\"b\",\"c\",\"d\"]}",
                "{\"position\":3,\"corrections\":[\"written\"]}", "EXPL-SECRET-5");
        q[5] = question(b, "GRAMMAR_BUILD", 6, "{\"blocks\":[{\"id\":\"b2\",\"text\":\"m\"},{\"id\":\"b1\",\"text\":\"n\"},{\"id\":\"b3\",\"text\":\"o\"}]}",
                "{\"order\":[\"b1\",\"b2\",\"b3\"]}", "EXPL-SECRET-6");
        q[6] = question(b, "CONNECTIVE_REWRITE", 7, "{\"sentence\":\"s\"}", "{}", null);
        return new Bank(b, q, right, wrong);
    }

    private static Map<String, Object> ans(long questionId, Object answer) {
        return Map.of("questionId", questionId, "answer", answer);
    }

    private List<Object> correctAnswers(Bank b) {
        return List.of(
                ans(b.q[0], Map.of("choiceId", b.mcqRight)),
                ans(b.q[1], Map.of("pairs", Map.of("l1", "r2", "l2", "r1"))),
                ans(b.q[2], Map.of("blanks", Map.of("b1", "  Since ", "b2", "HOWEVER"))),
                ans(b.q[3], Map.of("spans", List.of(Map.of("start", 0, "end", 1, "element", "PASSIVE"),
                        Map.of("start", 2, "end", 5, "element", "RELATIVE_CLAUSE")))),
                ans(b.q[4], Map.of("position", 3, "correction", " Written ")),
                ans(b.q[5], Map.of("order", List.of("b1", "b2", "b3"))),
                ans(b.q[6], Map.of("text", "My rewrite")));
    }

    private List<Object> wrongAnswers(Bank b) {
        return List.of(
                ans(b.q[0], Map.of("choiceId", b.mcqWrong)),
                ans(b.q[1], Map.of("pairs", Map.of("l1", "r1", "l2", "r2"))),
                ans(b.q[2], Map.of("blanks", Map.of("b1", "although", "b2", "however"))),
                ans(b.q[3], Map.of("spans", List.of(Map.of("start", 2, "end", 5, "element", "RELATIVE_CLAUSE")))),
                ans(b.q[4], Map.of("position", 2, "correction", "written")),
                ans(b.q[5], Map.of("order", List.of("b3", "b2", "b1"))),
                ans(b.q[6], Map.of("text", "anything")));
    }

    private ResultActions postAuth(String url, String token, Object body) throws Exception {
        var req = MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return token == null ? mvc.perform(req) : send(req, token);
    }

    private ResultActions getAuth(String url, String token) throws Exception {
        return send(get(url), token);
    }

    private long startAttempt(long bankId, String token) throws Exception {
        String body = postAuth("/api/banks/" + bankId + "/attempts", token, Map.of()).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private ResultActions submit(long attemptId, String token, List<?> answers) throws Exception {
        return postAuth("/api/attempts/" + attemptId + "/submit", token, Map.of("answers", answers));
    }

    @Test
    void bankNeverLeaksAnswerKeysOrExplanations() throws Exception {
        var bank = fullBank(true, false);
        var u = register(newEmail(), PASSWORD);
        var res = getAuth("/api/banks/" + bank.id, u.accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.questions", hasSize(7)))
                .andExpect(jsonPath("$.questions[0].type").value("CONNECTIVE_MCQ"))
                .andExpect(jsonPath("$.questions[0].choices", hasSize(2)))
                .andExpect(jsonPath("$.questions[0].choices[0].label").value("A")) // sắp theo nhãn, không lộ đáp án qua thứ tự id
                .andExpect(jsonPath("$.questions[1].payload.left", hasSize(2)))
                .andExpect(jsonPath("$.questions[6].type").value("CONNECTIVE_REWRITE"));
        for (String leaked : List.of("answerKey", "answer_key", "isCorrect", "is_correct", "correct", "explanation",
                "EXPL-SECRET", "CHOICE-EXPL", "pairs", "corrections", "\"order\"", "\"spans\"", "since", "written")) {
            res.andExpect(content().string(not(containsString(leaked))));
        }
    }

    @Test
    void everyGraderAcceptsCorrectAndRejectsWrongAnswers() throws Exception {
        var bank = fullBank(true, false);
        var u = register(newEmail(), PASSWORD);

        long good = startAttempt(bank.id, u.accessToken());
        var ok = submit(good, u.accessToken(), correctAnswers(bank)).andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(6)).andExpect(jsonPath("$.maxScore").value(6))
                .andExpect(jsonPath("$.pendingJobs", hasSize(0)));
        for (int i = 0; i < 6; i++) {
            ok.andExpect(jsonPath("$.results[" + i + "].isCorrect").value(true))
                    .andExpect(jsonPath("$.results[" + i + "].questionId").value(bank.q[i]))
                    .andExpect(jsonPath("$.results[" + i + "].explanation").value("EXPL-SECRET-" + (i + 1)));
        }
        ok.andExpect(jsonPath("$.results[0].correctAnswer.choiceId").value(bank.mcqRight))
                .andExpect(jsonPath("$.results[1].correctAnswer.pairs.l1").value("r2"))
                .andExpect(jsonPath("$.results[4].correctAnswer.position").value(3));

        long bad = startAttempt(bank.id, u.accessToken());
        var no = submit(bad, u.accessToken(), wrongAnswers(bank)).andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(0)).andExpect(jsonPath("$.maxScore").value(6));
        for (int i = 0; i < 6; i++) {
            no.andExpect(jsonPath("$.results[" + i + "].isCorrect").value(false));
        }
    }

    @Test
    void aiGradedQuestionsAreStoredButNotScored() throws Exception {
        var bank = fullBank(true, false);
        var u = register(newEmail(), PASSWORD);
        long attempt = startAttempt(bank.id, u.accessToken());
        submit(attempt, u.accessToken(), correctAnswers(bank)).andExpect(status().isOk())
                .andExpect(jsonPath("$.maxScore").value(6))
                .andExpect(jsonPath("$.results[6].isCorrect").value(nullValue()))
                .andExpect(jsonPath("$.results[6].correctAnswer").doesNotExist());
        assertEquals(1, count("select count(*) from student_answers where attempt_id = ? and is_correct is null", attempt));
        assertEquals(7, count("select count(*) from student_answers where attempt_id = ?", attempt));
    }

    @Test
    void malformedAnswersAreWrongNeverServerErrors() throws Exception {
        var bank = fullBank(true, false);
        var u = register(newEmail(), PASSWORD);
        List<Object> variants = List.of("abc", 5, List.of(1, 2), Map.of("x", 1), Map.of("choiceId", "nope", "pairs", 3,
                "blanks", List.of(), "spans", "s", "order", Map.of(), "position", "p", "correction", 9));
        for (Object v : variants) {
            long attempt = startAttempt(bank.id, u.accessToken());
            List<Object> answers = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                answers.add(ans(bank.q[i], v));
            }
            submit(attempt, u.accessToken(), answers).andExpect(status().isOk()).andExpect(jsonPath("$.score").value(0));
        }
    }

    @Test
    void unansweredQuestionsCountWrongAndAreStored() throws Exception {
        var bank = fullBank(true, false);
        var u = register(newEmail(), PASSWORD);
        long attempt = startAttempt(bank.id, u.accessToken());
        var withNull = new HashMap<String, Object>();
        withNull.put("questionId", bank.q[1]);
        withNull.put("answer", null);
        submit(attempt, u.accessToken(), List.of(ans(bank.q[0], Map.of("choiceId", bank.mcqRight)), withNull))
                .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(1)).andExpect(jsonPath("$.maxScore").value(6))
                .andExpect(jsonPath("$.results[1].isCorrect").value(false))
                .andExpect(jsonPath("$.results[5].isCorrect").value(false));
        assertEquals(7, count("select count(*) from student_answers where attempt_id = ?", attempt));
    }

    @Test
    void invalidQuestionIdsReturn400() throws Exception {
        var bank = fullBank(true, false);
        var other = fullBank(true, false);
        var u = register(newEmail(), PASSWORD);
        long attempt = startAttempt(bank.id, u.accessToken());
        submit(attempt, u.accessToken(), List.of(ans(other.q[0], Map.of("choiceId", 1)))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_failed"));
        submit(attempt, u.accessToken(), List.of(ans(bank.q[0], Map.of("choiceId", 1)), ans(bank.q[0], Map.of("choiceId", 2))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("validation_failed"));
        postAuth("/api/attempts/" + attempt + "/submit", u.accessToken(), Map.of()).andExpect(status().isBadRequest());
        // lỗi 400 không làm hỏng lượt làm: vẫn nộp được
        submit(attempt, u.accessToken(), List.of()).andExpect(status().isOk()).andExpect(jsonPath("$.score").value(0));
    }

    @Test
    void lockedBankReturns403OnReadAndStart() throws Exception {
        var locked = fullBank(false, false);
        var u = register(newEmail(), PASSWORD);
        getAuth("/api/banks/" + locked.id, u.accessToken()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("bank_locked"))
                .andExpect(content().string(not(containsString("Nevertheless"))));
        postAuth("/api/banks/" + locked.id + "/attempts", u.accessToken(), Map.of()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("bank_locked"));
        assertEquals(0, count("select count(*) from student_exercise_attempts where bank_id = ?", locked.id));
    }

    @Test
    void trialBankInLockedTopicAndAnyBankInFreeTopicAreOpen() throws Exception {
        var trial = fullBank(false, true);
        var freeTopic = fullBank(true, false);
        var u = register(newEmail(), PASSWORD);
        getAuth("/api/banks/" + trial.id, u.accessToken()).andExpect(status().isOk()).andExpect(jsonPath("$.isTrial").value(true));
        getAuth("/api/banks/" + freeTopic.id, u.accessToken()).andExpect(status().isOk());
    }

    @Test
    void bankOfDraftCourseReturns404ButExistingAttemptStillWorks() throws Exception {
        var bank = fullBank(true, true);
        var u = register(newEmail(), PASSWORD);
        long attempt = startAttempt(bank.id, u.accessToken());
        jdbc.update("update courses set status = 'DRAFT' where id = (select lv.course_id from course_levels lv"
                + " join topics t on t.level_id = lv.id join question_banks b on b.topic_id = t.id where b.id = ?)", bank.id);
        getAuth("/api/banks/" + bank.id, u.accessToken()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("bank_not_found"));
        postAuth("/api/banks/" + bank.id + "/attempts", u.accessToken(), Map.of()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("bank_not_found"));
        getAuth("/api/attempts/" + attempt, u.accessToken()).andExpect(status().isOk());
        submit(attempt, u.accessToken(), correctAnswers(bank)).andExpect(status().isOk()).andExpect(jsonPath("$.score").value(6));
    }

    @Test
    void unknownBankReturns404() throws Exception {
        var u = register(newEmail(), PASSWORD);
        for (String id : List.of("999999999", "abc")) {
            getAuth("/api/banks/" + id, u.accessToken()).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("bank_not_found"));
            postAuth("/api/banks/" + id + "/attempts", u.accessToken(), Map.of()).andExpect(status().isNotFound());
        }
    }

    @Test
    void otherUsersAttemptLooksNonexistent() throws Exception {
        var bank = fullBank(true, false);
        var owner = register(newEmail(), PASSWORD);
        var intruder = register(newEmail(), PASSWORD);
        long attempt = startAttempt(bank.id, owner.accessToken());
        submit(attempt, intruder.accessToken(), List.of()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("attempt_not_found"));
        getAuth("/api/attempts/" + attempt, intruder.accessToken()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("attempt_not_found"));
        getAuth("/api/attempts/999999999", owner.accessToken()).andExpect(status().isNotFound());
        getAuth("/api/attempts/abc", owner.accessToken()).andExpect(status().isNotFound());
        // lượt của owner không bị intruder đụng tới
        assertEquals(0, count("select count(*) from student_answers where attempt_id = ?", attempt));
    }

    @Test
    void resubmitReturns409AndKeepsFirstResult() throws Exception {
        var bank = fullBank(true, false);
        var u = register(newEmail(), PASSWORD);
        long attempt = startAttempt(bank.id, u.accessToken());
        submit(attempt, u.accessToken(), correctAnswers(bank)).andExpect(status().isOk());
        submit(attempt, u.accessToken(), wrongAnswers(bank)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("attempt_already_submitted"));
        getAuth("/api/attempts/" + attempt, u.accessToken()).andExpect(jsonPath("$.score").value(6));
        assertEquals(7, count("select count(*) from student_answers where attempt_id = ?", attempt));
    }

    @Test
    void concurrentSubmitsSucceedExactlyOnce() throws Exception {
        var bank = fullBank(true, false);
        var u = register(newEmail(), PASSWORD);
        long attempt = startAttempt(bank.id, u.accessToken());
        int n = 6;
        var pool = Executors.newFixedThreadPool(n);
        List<Integer> statuses = new ArrayList<>();
        try {
            List<Callable<Integer>> calls = IntStream.range(0, n)
                    .<Callable<Integer>>mapToObj(i -> () -> submit(attempt, u.accessToken(), correctAnswers(bank))
                            .andReturn().getResponse().getStatus())
                    .toList();
            for (var f : pool.invokeAll(calls)) {
                statuses.add(f.get());
            }
        } finally {
            pool.shutdown();
        }
        assertEquals(1, statuses.stream().filter(s -> s == 200).count(), "kết quả: " + statuses);
        assertEquals(n - 1, statuses.stream().filter(s -> s == 409).count(), "kết quả: " + statuses);
        assertEquals(7, count("select count(*) from student_answers where attempt_id = ?", attempt));
    }

    @Test
    void getAttemptHidesAnswersUntilSubmitted() throws Exception {
        var bank = fullBank(true, false);
        var u = register(newEmail(), PASSWORD);
        long attempt = startAttempt(bank.id, u.accessToken());
        getAuth("/api/attempts/" + attempt, u.accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(attempt)).andExpect(jsonPath("$.bankId").value(bank.id))
                .andExpect(jsonPath("$.results").doesNotExist()).andExpect(jsonPath("$.score").doesNotExist())
                .andExpect(content().string(not(containsString("EXPL-SECRET"))))
                .andExpect(content().string(not(containsString("correctAnswer"))));
        submit(attempt, u.accessToken(), correctAnswers(bank)).andExpect(status().isOk());
        getAuth("/api/attempts/" + attempt, u.accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(6)).andExpect(jsonPath("$.maxScore").value(6))
                .andExpect(jsonPath("$.results", hasSize(7)))
                .andExpect(jsonPath("$.results[0].correctAnswer.choiceId").value(bank.mcqRight))
                .andExpect(jsonPath("$.results[6].isCorrect").value(nullValue()));
    }

    @Test
    void myAttemptsPaginateNewestFirstWithOpaqueCursor() throws Exception {
        var bank = fullBank(true, false);
        var u = register(newEmail(), PASSWORD);
        var other = register(newEmail(), PASSWORD);
        startAttempt(bank.id, other.accessToken());
        List<Long> created = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            created.add(startAttempt(bank.id, u.accessToken()));
        }
        List<Long> seen = new ArrayList<>();
        String cursor = null;
        for (int page = 0; page < 3; page++) {
            String url = "/api/me/attempts?limit=2" + (cursor == null ? "" : "&cursor=" + cursor);
            JsonNode body = json.readTree(getAuth(url, u.accessToken()).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            body.get("data").forEach(n -> seen.add(n.get("id").asLong()));
            JsonNode next = body.get("nextCursor");
            cursor = next.isNull() ? null : next.asText();
            assertEquals(page < 2, cursor != null, "trang " + page);
        }
        List<Long> expected = new ArrayList<>(created);
        java.util.Collections.reverse(expected);
        assertEquals(expected, seen);
    }

    @Test
    void myAttemptsDefaultsTo20AndValidatesParams() throws Exception {
        var bank = fullBank(true, false);
        var u = register(newEmail(), PASSWORD);
        for (int i = 0; i < 21; i++) {
            jdbc.update("insert into student_exercise_attempts (user_id, bank_id) values (?, ?)", u.id(), bank.id);
        }
        getAuth("/api/me/attempts", u.accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(20))).andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andExpect(jsonPath("$.data[0].submittedAt").value(nullValue()));
        getAuth("/api/me/attempts?limit=1000", u.accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(21))).andExpect(jsonPath("$.nextCursor").value(nullValue()));
        getAuth("/api/me/attempts?limit=0", u.accessToken()).andExpect(status().isBadRequest());
        getAuth("/api/me/attempts?limit=abc", u.accessToken()).andExpect(status().isBadRequest());
        getAuth("/api/me/attempts?cursor=%21%21", u.accessToken()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_failed"));
    }

    @Test
    void everyEndpointRequiresToken() throws Exception {
        mvc.perform(get("/api/banks/1")).andExpect(status().isUnauthorized());
        postAuth("/api/banks/1/attempts", null, Map.of()).andExpect(status().isUnauthorized());
        postAuth("/api/attempts/1/submit", null, Map.of("answers", List.of())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/attempts/1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/attempts")).andExpect(status().isUnauthorized());
    }
}
