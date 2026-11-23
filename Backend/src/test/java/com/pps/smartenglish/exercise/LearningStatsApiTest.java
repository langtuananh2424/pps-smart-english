package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.ai.AiTestSupport;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** FR-EX-08, FR-EX-09, D-137, NFR-SEC-05: kết quả cuối bài, hồ sơ năng lực, time_spent_ms, test tải. */
class LearningStatsApiTest extends AiTestSupport {

    /** Bộ luyện trong topic học thử (skill GRAMMAR_VOCAB): 3 câu MCQ, q1 và q2 gắn yếu tố ngữ pháp RELATIVE_CLAUSE. */
    private record World(long course, long bank, long[] q, long[] right, long[] wrong) {}

    private World world() {
        long c = insert("courses", Map.of("slug", "ls-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "A", "status", "PUBLISHED"));
        long t = insert("topics", Map.of("level_id", lv, "title", "Unit thống kê", "is_free_trial", true, "status", "PUBLISHED"));
        jdbc.update("update topics set skill = 'GRAMMAR_VOCAB' where id = ?", t);
        long b = insert("question_banks", Map.of("topic_id", t, "title", "Bộ", "is_trial", false, "status", "PUBLISHED"));
        long[] q = new long[3];
        long[] right = new long[3];
        long[] wrong = new long[3];
        for (int i = 0; i < 3; i++) {
            q[i] = question(b, "CONNECTIVE_MCQ", i + 1, "Câu " + i, "{}", "{}");
            right[i] = insert("question_choices", Map.of("question_id", q[i], "label", "B", "content", "đúng", "is_correct", true));
            wrong[i] = insert("question_choices", Map.of("question_id", q[i], "label", "A", "content", "sai", "is_correct", false));
        }
        long g = jdbc.queryForObject("select id from grammar_elements where code = 'RELATIVE_CLAUSE'", Long.class);
        for (int i = 0; i < 2; i++) {
            jdbc.update("insert into question_grammar_tags (question_id, grammar_element_id) values (?, ?)", q[i], g);
        }
        return new World(c, b, q, right, wrong);
    }

    private Map<String, Object> ans(long question, long choice, Long ms) {
        var m = new HashMap<String, Object>();
        m.put("questionId", question);
        m.put("answer", Map.of("choiceId", choice));
        m.put("timeSpentMs", ms);
        return m;
    }

    private long submit(Account u, World w, Map<String, Object>... answers) throws Exception {
        long id = idOf(postJson("/api/banks/" + w.bank() + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
        postJson("/api/attempts/" + id + "/submit", u, Map.of("answers", List.of(answers))).andExpect(status().isOk());
        return id;
    }

    private JsonNode get(String url, Account u, int expected) throws Exception {
        return json.readTree(getJson(url, u).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    private static JsonNode metric(JsonNode list, String key) {
        for (JsonNode n : list) {
            if (key.equals(n.get("key").asText())) {
                return n;
            }
        }
        throw new AssertionError("Không thấy " + key + " trong " + list);
    }

    @Test
    @SuppressWarnings("unchecked")
    void resultShowsBreakdownAndCumulativeWrongCounts() throws Exception {
        var w = world();
        var u = student(0);
        long a1 = submit(u, w, ans(w.q()[0], w.right()[0], 5000L), ans(w.q()[1], w.wrong()[1], 4000L), ans(w.q()[2], w.wrong()[2], null));
        submit(u, w, ans(w.q()[0], w.right()[0], null), ans(w.q()[1], w.wrong()[1], null), ans(w.q()[2], w.right()[2], null));

        var n = get("/api/attempts/" + a1, u, 200).get("breakdown");
        assertEquals(1, n.get("correct").asInt());
        assertEquals(2, n.get("wrong").asInt());
        assertEquals(0, n.get("ungraded").asInt());
        var mcq = metric(n.get("byType"), "CONNECTIVE_MCQ");
        assertEquals(3, mcq.get("answered").asInt());
        assertEquals(33, mcq.get("accuracy").asInt());
        assertEquals(3, metric(n.get("bySkill"), "GRAMMAR_VOCAB").get("answered").asInt());
        var rel = metric(n.get("byGrammar"), "RELATIVE_CLAUSE"); // q1 đúng, q2 sai
        assertEquals(2, rel.get("answered").asInt());
        assertEquals(50, rel.get("accuracy").asInt());
        // q2 sai ở cả hai lượt, q3 sai một lần (lượt 2 đúng); nhiều lần sai trước
        assertEquals(2, n.at("/wrongQuestions/0/wrongCount").asInt());
        assertEquals(w.q()[1], n.at("/wrongQuestions/0/questionId").asLong());
        assertEquals(1, n.at("/wrongQuestions/1/wrongCount").asInt());
        assertEquals(2, n.get("wrongQuestions").size());

        // sửa đáp án (answer_version tăng): lần sai cũ không tính oan; dòng đã chấm lại theo phiên bản mới thì tính
        jdbc.update("update questions set answer_version = 2 where id = ?", w.q()[1]);
        assertEquals(1, get("/api/attempts/" + a1, u, 200).at("/breakdown/wrongQuestions").size());
        jdbc.update("update student_answers set answer_version = 2 where question_id = ?", w.q()[1]);
        assertEquals(2, get("/api/attempts/" + a1, u, 200).at("/breakdown/wrongQuestions/0/wrongCount").asInt());
    }

    @Test
    @SuppressWarnings("unchecked")
    void absurdTimeSpentIsDroppedNotRejected() throws Exception {
        var w = world();
        var u = student(0);
        long id = submit(u, w, ans(w.q()[0], w.right()[0], 5000L), ans(w.q()[1], w.right()[1], 3_600_001L),
                ans(w.q()[2], w.right()[2], -1L));
        assertEquals(5000, count("select time_spent_ms from student_answers where attempt_id = ? and question_id = ?", id, w.q()[0]));
        assertNull(jdbc.queryForObject("select time_spent_ms from student_answers where attempt_id = ? and question_id = ?",
                Integer.class, id, w.q()[1]));
        assertNull(jdbc.queryForObject("select time_spent_ms from student_answers where attempt_id = ? and question_id = ?",
                Integer.class, id, w.q()[2]));
        assertEquals(3, count("select count(*) from student_answers where attempt_id = ? and answer_version = 1", id));
        // biên: 0 và đúng 3_600_000 hợp lệ
        long id2 = submit(u, w, ans(w.q()[0], w.right()[0], 0L), ans(w.q()[1], w.right()[1], 3_600_000L));
        assertEquals(3_600_000, count("select time_spent_ms from student_answers where attempt_id = ? and question_id = ?", id2, w.q()[1]));
    }

    @Test
    @SuppressWarnings("unchecked")
    void otherUsersCannotSeeAnAttemptResult() throws Exception {
        var w = world();
        var u = student(0);
        long id = submit(u, w, ans(w.q()[0], w.right()[0], null));
        get("/api/attempts/" + id, student(0), 404);
    }

    @Test
    void aiGradedAnswersAreReportedSeparatelyFromAccuracy() throws Exception {
        var w = world();
        var u = student(0);
        long wq = writingQuestion(w.bank());
        long att = attempt(u, w.bank(), OffsetDateTime.now());
        answer(att, w.q()[0], true);
        long ai = answer(att, wq, null);
        answer(att, w.q()[1], null); // chờ chấm, chưa có điểm AI
        jdbc.update("insert into student_answer_grading (answer_id, grader, score) values (?, 'AI', 6)", ai);

        var p = get("/api/me/learning-profile", u, 200);
        var writing = metric(p.get("byType"), "WRITING");
        assertEquals(0, writing.get("answered").asInt());
        assertTrue(writing.get("accuracy").isNull());
        assertEquals(1, writing.get("aiGraded").asInt());
        assertEquals(6.0, writing.get("aiAverageScore").asDouble());
        assertEquals(1, p.get("answered").asInt()); // chỉ q1 vào tỉ lệ
        assertEquals(100, p.get("accuracy").asInt());
        assertEquals(1, metric(p.get("byType"), "CONNECTIVE_MCQ").get("ungraded").asInt());
    }

    @Test
    void profileAggregatesDimensionsTrendAndImprovements() throws Exception {
        var w = world();
        var u = student(0);
        var other = student(0);
        // tuần này: 12 câu q1 đúng 3 (25%); 3 tuần trước: q3 đúng 1/1; 20 tuần trước (ngoài cửa sổ 12 tuần): q3 sai
        long now = attempt(u, w.bank(), OffsetDateTime.now());
        for (int i = 0; i < 12; i++) {
            long a = i == 0 ? now : attempt(u, w.bank(), OffsetDateTime.now());
            answer(a, w.q()[0], i < 3);
        }
        answer(attempt(u, w.bank(), OffsetDateTime.now().minusWeeks(3)), w.q()[2], true);
        answer(attempt(u, w.bank(), OffsetDateTime.now().minusWeeks(20)), w.q()[2], false);
        answer(attempt(u, w.bank(), null), w.q()[2], false); // chưa nộp: bỏ
        answer(attempt(other, w.bank(), OffsetDateTime.now()), w.q()[0], false); // người khác: bỏ

        var p = get("/api/me/learning-profile", u, 200);
        assertEquals(14, p.get("answered").asInt());
        assertEquals(4, p.get("correct").asInt());
        assertEquals(29, p.get("accuracy").asInt());
        assertEquals(14, metric(p.get("bySkill"), "GRAMMAR_VOCAB").get("answered").asInt());
        assertEquals(12, metric(p.get("byGrammar"), "RELATIVE_CLAUSE").get("answered").asInt());
        assertEquals(14, metric(p.get("byTopic"), "" + topicOf(w)).get("answered").asInt());
        assertEquals("Unit thống kê", metric(p.get("byTopic"), "" + topicOf(w)).get("label").asText());

        var trend = p.get("weeklyTrend");
        assertEquals(12, trend.size());
        assertEquals(java.time.DayOfWeek.MONDAY, LocalDate.parse(trend.get(11).get("weekStart").asText()).getDayOfWeek());
        long inTrend = 0;
        for (JsonNode t : trend) {
            inTrend += t.get("answered").asLong();
        }
        assertEquals(13, inTrend); // 20 tuần trước không nằm trong cửa sổ
        assertEquals(12, trend.get(11).get("answered").asInt());
        assertEquals(25, trend.get(11).get("accuracy").asInt());
        assertEquals(1, trend.get(8).get("answered").asInt());
        assertTrue(trend.get(0).get("accuracy").isNull());

        // cần cải thiện: chỉ mục đủ 10 câu (kỹ năng GRAMMAR_VOCAB 29% của 14, yếu tố RELATIVE_CLAUSE 25% của 12)
        var imp = p.get("improvements");
        assertEquals(2, imp.size());
        assertEquals("GRAMMAR", imp.get(0).get("dimension").asText());
        assertEquals(25, imp.get(0).get("accuracy").asInt());
        assertEquals("SKILL", imp.get(1).get("dimension").asText());
    }

    @Test
    void profileOfNewStudentIsEmpty() throws Exception {
        var p = get("/api/me/learning-profile", student(0), 200);
        assertEquals(0, p.get("answered").asInt());
        assertTrue(p.get("accuracy").isNull());
        assertEquals(0, p.get("improvements").size());
        assertEquals(12, p.get("weeklyTrend").size());
    }

    // ---- giáo viên xem hồ sơ trong lớp (NFR-SEC-05)

    private Account withRole(String role) throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = ?", u.id(), role);
        return login(u.email(), PASSWORD);
    }

    private long classWithTeacher(long course, Account teacher) {
        long cls = insert("classes", Map.of("course_id", course, "name", "Lớp " + UUID.randomUUID().toString().substring(0, 4),
                "starts_on", LocalDate.now().minusDays(10), "ends_on", LocalDate.now().plusDays(60), "mode", "ONLINE", "capacity", 20));
        jdbc.update("insert into class_staff (class_id, user_id, role, assigned_from) values (?, ?, 'TEACHER', ?)", cls, teacher.id(),
                LocalDate.now().minusDays(10));
        return cls;
    }

    private void join(long cls, long course, Account s) {
        jdbc.update("insert into class_members (class_id, course_id, user_id, status, live_key, joined_at) values (?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP)",
                cls, course, s.id(), course + ":" + s.id());
    }

    @Test
    void teacherSeesOnlyStudentsOfOwnClass() throws Exception {
        var w = world();
        var s = student(0);
        var stranger = student(0);
        long att = attempt(s, w.bank(), OffsetDateTime.now());
        answer(att, w.q()[0], true);
        answer(att, w.q()[1], false);
        var teacherA = withRole("TEACHER");
        var teacherB = withRole("TEACHER");
        long classA = classWithTeacher(w.course(), teacherA);
        long classB = classWithTeacher(w.course(), teacherB);
        join(classA, w.course(), s);
        join(classB, w.course(), stranger);

        var url = "/api/classes/" + classA + "/students/" + s.id() + "/learning-profile";
        var p = get(url, teacherA, 200);
        assertEquals(2, p.get("answered").asInt());
        assertEquals(50, p.get("accuracy").asInt());
        get(url, teacherB, 404); // giáo viên lớp khác
        get("/api/classes/" + classA + "/students/" + stranger.id() + "/learning-profile", teacherA, 404); // học viên lớp khác
        get(url, s, 404); // học viên không gọi được endpoint giáo viên
        get(url, stranger, 404);
        get("/api/classes/" + classB + "/students/" + stranger.id() + "/learning-profile", teacherA, 404);
        get("/api/classes/999999/students/" + s.id() + "/learning-profile", teacherA, 404);
    }

    // ---- test tải (checklist P0): vài nghìn câu trả lời của một học viên

    @Test
    void largeHistoryStaysFast() throws Exception {
        long c = insert("courses", Map.of("slug", "big-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "A", "status", "PUBLISHED"));
        long t = insert("topics", Map.of("level_id", lv, "title", "T", "is_free_trial", true, "status", "PUBLISHED"));
        long b = insert("question_banks", Map.of("topic_id", t, "title", "Bộ lớn", "is_trial", false, "status", "PUBLISHED"));
        int questions = 100;
        int attempts = 60; // 6000 câu trả lời
        long g = jdbc.queryForObject("select id from grammar_elements where code = 'PASSIVE'", Long.class);
        long[] q = new long[questions];
        for (int i = 0; i < questions; i++) {
            q[i] = question(b, i % 2 == 0 ? "CONNECTIVE_MCQ" : "WRITING", i + 1, "Câu " + i, "{}", "{}");
            if (i % 2 == 0) {
                insert("question_choices", Map.of("question_id", q[i], "label", "B", "content", "đúng", "is_correct", true));
            }
            if (i % 3 == 0) {
                jdbc.update("insert into question_grammar_tags (question_id, grammar_element_id) values (?, ?)", q[i], g);
            }
        }
        var u = student(0);
        long last = 0;
        for (int a = 0; a < attempts; a++) {
            last = attempt(u, b, OffsetDateTime.now().minusDays(a));
            List<Object[]> rows = new ArrayList<>();
            for (int i = 0; i < questions; i++) {
                rows.add(new Object[] {last, q[i], (a + i) % 3 != 0, 1, 1200});
            }
            jdbc.batchUpdate("insert into student_answers (attempt_id, question_id, is_correct, answer_version, time_spent_ms) values (?, ?, ?, ?, ?)", rows);
        }
        assertEquals(questions * attempts, count("select count(*) from student_answers a join student_exercise_attempts t on t.id = a.attempt_id where t.user_id = ?", u.id()));

        long t0 = System.nanoTime();
        var p = get("/api/me/learning-profile", u, 200);
        long profileMs = (System.nanoTime() - t0) / 1_000_000;
        assertEquals(questions * attempts, p.get("answered").asInt());
        t0 = System.nanoTime();
        var r = get("/api/attempts/" + last, u, 200);
        long resultMs = (System.nanoTime() - t0) / 1_000_000;
        assertEquals(questions, r.at("/breakdown/correct").asInt() + r.at("/breakdown/wrong").asInt());
        System.out.println("LOAD-TEST answers=" + questions * attempts + " profileMs=" + profileMs + " resultMs=" + resultMs);
        assertTrue(profileMs < 3000, "profile " + profileMs + "ms");
        assertTrue(resultMs < 3000, "result " + resultMs + "ms");
    }

    // ---- fixture trực tiếp bằng SQL

    private long topicOf(World w) {
        return jdbc.queryForObject("select topic_id from question_banks where id = ?", Long.class, w.bank());
    }

    private long attempt(Account u, long bank, OffsetDateTime submittedAt) {
        jdbc.update("insert into student_exercise_attempts (user_id, bank_id, submitted_at, score, max_score) values (?, ?, ?, 0, 0)",
                u.id(), bank, submittedAt == null ? null : Timestamp.from(submittedAt.toInstant()));
        return jdbc.queryForObject("select max(id) from student_exercise_attempts", Long.class);
    }

    private long answer(long attempt, long question, Boolean correct) {
        jdbc.update("insert into student_answers (attempt_id, question_id, is_correct, credit) values (?, ?, ?, ?)", attempt, question,
                correct, correct == null ? null : correct ? 1 : 0);
        return jdbc.queryForObject("select max(id) from student_answers", Long.class);
    }
}
