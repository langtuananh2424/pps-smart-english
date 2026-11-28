package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Tester D-174 (FR-ASG-03/04, NFR-SEC-05, D-142): giáo viên xem từng câu của lượt nộp bài giao bộ câu. */
class D174ReviewTesterTest extends ListeningTestSupport {

    private static final List<String> WORDS = List.of("a1", "b2", "c3", "d4", "e5", "f6", "g7", "h8");

    private record Fx(World w, long cls, Account teacher) {}

    private Fx fixture(String playback) throws Exception {
        var w = world(playback, null);
        jdbc.update("update courses set delivery_mode = 'CLASS_BASED' where id = ?", w.course());
        long cls = insert("classes", Map.of("course_id", w.course(), "name", "Lớp D174", "starts_on", LocalDate.now().minusDays(1),
                "ends_on", LocalDate.now().plusDays(60), "mode", "ONLINE", "capacity", 20));
        var teacher = withRole("TEACHER");
        jdbc.update("insert into class_staff (class_id, user_id, role, assigned_from) values (?, ?, 'TEACHER', ?)", cls, teacher.id(),
                LocalDate.now().minusDays(2));
        return new Fx(w, cls, teacher);
    }

    private Account member(Fx f) throws Exception {
        var s = student(0);
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', "
                + "TIMESTAMP '2020-01-01 00:00:00', TIMESTAMP '2099-01-01 00:00:00')", s.id(), f.w().course());
        jdbc.update("insert into class_members (class_id, course_id, user_id, status, live_key, joined_at) values (?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP)",
                f.cls(), f.w().course(), s.id(), f.w().course() + ":" + s.id());
        return s;
    }

    private String base(Fx f) {
        return "/api/classes/" + f.cls() + "/assignments";
    }

    private long assign(Fx f, String mode) throws Exception {
        var body = new HashMap<String, Object>(Map.of("bankId", f.w().bank(), "dueAt", Instant.now().plus(48, ChronoUnit.HOURS).toString()));
        if (mode != null) {
            body.put("mode", mode);
        }
        return idOf(postJson(base(f), f.teacher(), body).andExpect(status().isCreated()), "id");
    }

    private long start(Fx f, long asg, Account s) throws Exception {
        return idOf(postJson(base(f) + "/" + asg + "/start", s, Map.of()).andExpect(status().isOk()), "id");
    }

    private void submitAttempt(Account s, long attempt, List<Map<String, Object>> answers) throws Exception {
        guard.resetRateLimit(s.id());
        postJson("/api/attempts/" + attempt + "/submit", s, Map.of("answers", answers)).andExpect(status().isOk());
    }

    private long submit(Fx f, long asg, Account s, boolean... correct) throws Exception {
        long id = start(f, asg, s);
        submitAttempt(s, id, answers(f.w(), correct));
        return id;
    }

    private String url(Fx f, long asg, Object attempt) {
        return base(f) + "/" + asg + "/attempts/" + attempt + "/result";
    }

    private JsonNode review(Fx f, long asg, long attempt, Account who) throws Exception {
        return get(url(f, asg, attempt), who, 200);
    }

    private static List<Long> ids(JsonNode questions) {
        var out = new ArrayList<Long>();
        questions.forEach(q -> out.add(q.get("id").asLong()));
        return out;
    }

    private static boolean noHints(JsonNode questions) {
        for (JsonNode q : questions) {
            if (q.has("hints") && !q.get("hints").isNull() && q.get("hints").size() > 0) {
                return false;
            }
        }
        return true;
    }

    /** NFR-SEC-05: giáo vụ có quyền với mọi lớp, như các API chấm khác. */
    @Test
    void trainingManagerCanRead() throws Exception {
        var f = fixture("FREE");
        var s = member(f);
        long asg = assign(f, null);
        long attempt = submit(f, asg, s, true, false);
        var mgr = withRole("TRAINING_MANAGER");
        var node = review(f, asg, attempt, mgr);
        assertEquals(get("/api/attempts/" + attempt, s, 200), node.get("attempt"));
        var plain = withRole("TEACHER"); // giáo viên không được phân công lớp này
        get(url(f, asg, attempt), plain, 404);
    }

    /**
     * Học viên rút khỏi lớp (hoặc lớp kết thúc, COMPLETED) sau khi nộp: giáo viên vẫn sửa điểm và xem lịch sử chấm được (PUT grade,
     * GET grades không kiểm người nhận), nên xem bài làm cũng phải được, như AssignmentSubmissionReader của bài Viết/Nói ("kể cả khi
     * học viên đã rút").
     */
    @Test
    void attemptOfWithdrawnOrCompletedStudentStaysReadable() throws Exception {
        var f = fixture("FREE");
        var s = member(f);
        var s2 = member(f);
        long asg = assign(f, null);
        long attempt = submit(f, asg, s, true, false);
        long attempt2 = submit(f, asg, s2, true, true);
        jdbc.update("update class_members set status = 'WITHDRAWN', live_key = null, left_at = CURRENT_TIMESTAMP where class_id = ? and user_id = ?", f.cls(), s.id());
        jdbc.update("update class_members set status = 'COMPLETED', live_key = null, left_at = CURRENT_TIMESTAMP where class_id = ? and user_id = ?", f.cls(), s2.id());

        getJson(base(f) + "/" + asg + "/attempts/" + attempt + "/grades", f.teacher()).andExpect(status().isOk());
        get(url(f, asg, attempt), f.teacher(), 200);
        get(url(f, asg, attempt2), f.teacher(), 200);
    }

    @Test
    void archivedAnsweredQuestionStaysArchivedUnansweredHides() throws Exception {
        var f = fixture("FREE");
        var s = member(f);
        long asg = assign(f, null);
        long attempt = start(f, asg, s);
        submitAttempt(s, attempt, answers(f.w(), true, false)); // trả lời q0, q1
        jdbc.update("update questions set archived_at = CURRENT_TIMESTAMP where id in (?, ?)", f.w().q()[1], f.w().q()[2]);

        var node = review(f, asg, attempt, f.teacher());
        var shown = ids(node.at("/bank/questions"));
        assertTrue(shown.contains(f.w().q()[1]), "câu đã lưu trữ đã trả lời phải còn: " + shown);
        // nộp bài lưu dòng cho mọi câu của bộ (câu bỏ trống có answer rỗng), nên q2 vẫn hiện như màn kết quả của học viên
        var inResults = new ArrayList<Long>();
        node.at("/attempt/results").forEach(r -> inResults.add(r.get("questionId").asLong()));
        assertEquals(new java.util.HashSet<>(inResults), new java.util.HashSet<>(shown), "đề và kết quả lệch câu");
        assertEquals(get("/api/attempts/" + attempt, s, 200), node.get("attempt"));
    }

    /** Bộ PRACTICE có gợi ý: học viên vẫn thấy gợi ý qua GET /api/banks (hồi quy tách BankService), màn chấm không có. */
    @Test
    void hintsStayForStudentButNotInReview() throws Exception {
        var f = fixture("FREE");
        jdbc.update("update question_banks set mode = 'PRACTICE' where id = ?", f.w().bank());
        insert("question_hints", Map.of("question_id", f.w().q()[0], "sort_order", 0, "content", "HINT-D174"));
        var s = member(f);
        var before = get("/api/banks/" + f.w().bank(), s, 200);
        assertFalse(noHints(before.get("questions")), "học viên mất gợi ý sau khi tách BankService: " + before);

        long asg = assign(f, null);
        long attempt = submit(f, asg, s, true);
        var node = review(f, asg, attempt, f.teacher());
        assertTrue(noHints(node.at("/bank/questions")), node.toString());
        assertFalse(node.toString().contains("HINT-D174"));
    }

    /** D-146: hộp từ trên màn chấm theo đúng thứ tự học viên đã thấy trong lượt đó. */
    @Test
    void wordBankOrderMatchesStudentAttempt() throws Exception {
        var f = fixture("FREE");
        long wq = question(f.w().bank(), "WORD_BANK", 9, "Điền từ",
                json.writeValueAsString(Map.of("text", "{1} {2}", "blanks", List.of("1", "2"), "words", WORDS)),
                json.writeValueAsString(Map.of("blanks", Map.of("1", "a1", "2", "b2"))));
        var s = member(f);
        long asg = assign(f, null);
        long attempt = start(f, asg, s);
        var seen = words(get("/api/banks/" + f.w().bank(), s, 200).get("questions"), wq);
        var expected = new ArrayList<>(WORDS);
        Collections.shuffle(expected, new Random(attempt * 1_000_003L + wq));
        assertEquals(expected, seen, "học viên: seed theo lượt mở");
        var ans = new ArrayList<>(answers(f.w(), true));
        ans.add(Map.of("questionId", wq, "answer", Map.of("blanks", Map.of("1", "a1", "2", "c3"))));
        submitAttempt(s, attempt, ans);

        var node = review(f, asg, attempt, f.teacher());
        assertEquals(seen, words(node.at("/bank/questions"), wq));
        for (JsonNode a : node.get("answers")) {
            if (a.get("questionId").asLong() == wq) {
                assertEquals("c3", a.at("/answer/blanks/2").asText());
            }
        }
    }

    private static List<String> words(JsonNode questions, long qid) {
        var out = new ArrayList<String>();
        for (JsonNode q : questions) {
            if (q.get("id").asLong() == qid) {
                q.get("payload").get("words").forEach(w -> out.add(w.asText()));
            }
        }
        return out;
    }

    /** D-155/D-162: bài giao chế độ Thi: màn chấm không lộ hơn học viên nhận sau nộp, không gợi ý. */
    @Test
    void examModeAssignmentShowsNoMoreThanStudentGets() throws Exception {
        var f = fixture("FREE");
        insert("question_hints", Map.of("question_id", f.w().q()[0], "sort_order", 0, "content", "HINT-EXAM"));
        var s = member(f);
        long asg = assign(f, "EXAM");
        long attempt = submit(f, asg, s, true, false, true, true);
        var node = review(f, asg, attempt, f.teacher());
        assertEquals(get("/api/attempts/" + attempt, s, 200), node.get("attempt"));
        assertFalse(node.toString().contains("HINT-EXAM"));
        assertFalse(node.at("/bank/questions").toString().contains("is_correct"));
        assertFalse(node.at("/bank/questions").toString().contains("isCorrect"));
    }

    /** D-142: lượt Nghe ép không có đúng/sai, điểm từng câu ở answers; attempt như học viên nhận (không điểm). */
    @Test
    void forcedListeningHidesPerQuestionScore() throws Exception {
        var f = fixture("LOCKED");
        var s = member(f);
        long asg = assign(f, null);
        long attempt = start(f, asg, s);
        for (long g : new long[] {f.w().g1(), f.w().g2()}) { // nghe hết từng phần trước khi nộp
            play(s, attempt, g);
            playDone(s, attempt, g);
        }
        submitAttempt(s, attempt, answers(f.w(), true, false, true, true));
        var node = review(f, asg, attempt, f.teacher());
        assertEquals(get("/api/attempts/" + attempt, s, 200), node.get("attempt"));
        assertTrue(node.at("/attempt/score").isMissingNode() || node.at("/attempt/score").isNull(), node.get("attempt").toString());
        assertEquals(4, node.get("answers").size());
        for (JsonNode a : node.get("answers")) {
            assertFalse(a.has("isCorrect") || a.has("credit"), a.toString());
        }
        var results = node.at("/attempt/results");
        if (results.isArray()) {
            for (JsonNode r : results) {
                assertTrue(r.path("isCorrect").isMissingNode() || r.path("isCorrect").isNull(), "Nghe ép lộ đúng/sai: " + r);
            }
        }
    }

    @Test
    void malformedIdsAre404() throws Exception {
        var f = fixture("FREE");
        var s = member(f);
        long asg = assign(f, null);
        submit(f, asg, s, true);
        getJson(url(f, asg, "abc"), f.teacher()).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("attempt_not_found"));
        getJson(url(f, asg, "-1"), f.teacher()).andExpect(status().isNotFound());
        getJson(base(f) + "/x/attempts/1/result", f.teacher()).andExpect(status().isNotFound());
    }
}
