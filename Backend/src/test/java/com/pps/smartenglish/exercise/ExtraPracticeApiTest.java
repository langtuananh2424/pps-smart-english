package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.ai.AiTestSupport;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** FR-EX-07, D-152: Luyện thêm. Câu chọn đúng nguồn, trần 15%, ưu tiên câu sai, quyền theo bản ghi, không tính tiến độ. */
class ExtraPracticeApiTest extends AiTestSupport {

    private static final String MCQ = "CONNECTIVE_MCQ";

    private long course;
    private long level;

    private void newCourse(boolean sequential) {
        course = insert("courses", Map.of("slug", "ex-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED", "sequential", sequential));
        level = insert("course_levels", Map.of("course_id", course, "code", "A", "title", "Level A", "status", "PUBLISHED"));
    }

    private long topic(String title, int order, boolean trial) {
        return insert("topics", Map.of("level_id", level, "title", title, "sort_order", order, "is_free_trial", trial, "status", "PUBLISHED"));
    }

    private long bank(long topic, String mode, String status) {
        return insert("question_banks", Map.of("topic_id", topic, "title", "Bộ", "is_trial", false, "status", status, "mode", mode));
    }

    private List<Long> questions(long bank, int n, String type) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ids.add(question(bank, type, i + 1, "Đề " + i, "{}", "{}"));
        }
        return ids;
    }

    private long topicWithQuestions(String title, int order, int n) {
        long t = topic(title, order, false);
        questions(bank(t, "PRACTICE", "PUBLISHED"), n, MCQ);
        return t;
    }

    private void enroll(Account u) {
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', ?, null)",
                u.id(), course, java.time.OffsetDateTime.parse("2020-01-01T00:00:00Z"));
    }

    /** Học viên đã nộp một lượt trên bộ của topic (đủ để topic tính là đã hoàn thành khi không còn bài giảng nào). */
    private void complete(Account u, long topic) {
        long bank = jdbc.queryForObject("select id from question_banks where topic_id = ? order by id limit 1", Long.class, topic);
        long q = jdbc.queryForObject("select id from questions where bank_id = ? order by id limit 1", Long.class, bank);
        long attempt = insert("student_exercise_attempts", Map.of("user_id", u.id(), "bank_id", bank,
                "started_at", java.time.OffsetDateTime.now(), "submitted_at", java.time.OffsetDateTime.now(), "score", 1, "max_score", 1));
        insert("student_answers", Map.of("attempt_id", attempt, "question_id", q, "is_correct", true, "credit", 1));
    }

    private com.fasterxml.jackson.databind.JsonNode overview(Account u) throws Exception {
        return json.readTree(getJson("/api/me/courses/" + course + "/extra-practice", u).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private com.fasterxml.jackson.databind.JsonNode card(Account u, String section, long topic) throws Exception {
        for (var c : overview(u).get(section)) {
            if (c.get("topicId").asLong() == topic) {
                return c;
            }
        }
        throw new AssertionError("Không có thẻ topic " + topic + " ở " + section);
    }

    private org.springframework.test.web.servlet.ResultActions start(Account u, Map<String, Object> body) throws Exception {
        return postJson("/api/me/courses/" + course + "/extra-practice/attempts", u, body);
    }

    private long startOk(Account u, Map<String, Object> body) throws Exception {
        return idOf(start(u, body).andExpect(status().isCreated()), "id");
    }

    private Set<Long> attemptQuestions(long attempt) {
        return new HashSet<>(jdbc.queryForList("select question_id from attempt_questions where attempt_id = ?", Long.class, attempt));
    }

    @Test
    void challengeSizeIsCappedByRatio() throws Exception {
        newCourse(false);
        var u = student(0);
        enroll(u);
        long small = topicWithQuestions("Ít câu", 1, 20); // ceil(15% * 20) = 3
        long big = topicWithQuestions("Nhiều câu", 2, 100); // min(12, 15) = 12
        long tiny = topicWithQuestions("Rất ít", 3, 2); // ceil(0.3) = 1
        assertEquals(3, attemptQuestions(startOk(u, Map.of("scope", "CHALLENGE", "topicId", small))).size());
        // phải nộp hoặc bỏ lượt mở trước khi bắt đầu lượt khác
        jdbc.update("update student_exercise_attempts set submitted_at = CURRENT_TIMESTAMP where user_id = ?", u.id());
        assertEquals(12, attemptQuestions(startOk(u, Map.of("scope", "CHALLENGE", "topicId", big))).size());
        jdbc.update("update student_exercise_attempts set submitted_at = CURRENT_TIMESTAMP where user_id = ?", u.id());
        assertEquals(1, attemptQuestions(startOk(u, Map.of("scope", "CHALLENGE", "topicId", tiny))).size());
    }

    @Test
    void challengePrefersQuestionsTheStudentGotWrong() throws Exception {
        newCourse(false);
        var u = student(0);
        enroll(u);
        long t = topic("Sai nhiều", 1, false);
        long bank = bank(t, "PRACTICE", "PUBLISHED");
        var qs = questions(bank, 30, MCQ); // cap = ceil(4.5) = 5
        // lịch sử sai nằm ở lượt trên bộ khác: 3 câu sai khi kiểm tra (D-150), 2 câu sai khi nộp
        long other = bank(topic("Khác", 9, false), "PRACTICE", "PUBLISHED");
        long old = insert("student_exercise_attempts", Map.of("user_id", u.id(), "bank_id", other,
                "started_at", java.time.OffsetDateTime.now(), "submitted_at", java.time.OffsetDateTime.now(), "score", 0, "max_score", 1));
        for (int i = 0; i < 3; i++) {
            insert("attempt_question_checks", Map.of("attempt_id", old, "question_id", qs.get(i), "wrong_count", 2));
        }
        for (int i = 3; i < 5; i++) {
            insert("student_answers", Map.of("attempt_id", old, "question_id", qs.get(i), "is_correct", false, "credit", 0));
        }
        long attempt = startOk(u, Map.of("scope", "CHALLENGE", "topicId", t));
        assertEquals(new HashSet<>(qs.subList(0, 5)), attemptQuestions(attempt));
    }

    @Test
    void reviewByKindIsCappedAndOnlyUsesCompletedTopics() throws Exception {
        newCourse(false);
        var u = student(5);
        enroll(u);
        long done = topicWithQuestions("Đã học", 1, 40); // 40 câu tự chấm => ceil(6) = 6
        long fresh = topicWithQuestions("Chưa học", 2, 40);
        complete(u, done);
        assertEquals("COMPLETED", card(u, "review", done).get("state").asText());
        assertEquals(40, card(u, "review", done).get("available").get("AUTO").asInt());
        assertEquals("OPEN", card(u, "challenge", fresh).get("state").asText());
        long attempt = startOk(u, Map.of("scope", "REVIEW", "kind", "AUTO"));
        var ids = attemptQuestions(attempt);
        assertEquals(6, ids.size());
        assertTrue(ids.stream().allMatch(id -> count("select count(*) from questions q join question_banks b on b.id = q.bank_id where q.id = ? and b.topic_id = ?", id, done) == 1));
        // chưa có Topic nào hoàn thành ở dạng Viết: không có câu
        jdbc.update("update student_exercise_attempts set submitted_at = CURRENT_TIMESTAMP where id = ?", attempt);
        start(u, Map.of("scope", "REVIEW", "kind", "WRITING")).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("extra_no_questions"));
        // Ôn lại một Topic chưa hoàn thành bị từ chối
        start(u, Map.of("scope", "REVIEW", "topicId", fresh)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("extra_topic_not_available"));
    }

    @Test
    void sourcesExcludeExamLockedGroupedArchivedDraftAndOpenAttemptQuestions() throws Exception {
        newCourse(false);
        var u = student(0);
        enroll(u);
        long t = topic("Nguồn", 1, false);
        var good = questions(bank(t, "PRACTICE", "PUBLISHED"), 4, MCQ);
        questions(bank(t, "EXAM", "PUBLISHED"), 5, MCQ); // bài thi
        questions(bank(t, "PRACTICE", "DRAFT"), 5, MCQ); // chưa xuất bản
        long locked = bank(t, "PRACTICE", "PUBLISHED");
        jdbc.update("update question_banks set playback_mode = 'LOCKED' where id = ?", locked); // Nghe ép
        questions(locked, 5, MCQ);
        long mixed = bank(t, "PRACTICE", "PUBLISHED");
        var qs = questions(mixed, 6, MCQ);
        long group = insert("question_groups", Map.of("bank_id", mixed, "sort_order", 1));
        jdbc.update("update questions set group_id = ? where id = ?", group, qs.get(0)); // câu thuộc nhóm bài đọc/audio
        jdbc.update("update questions set archived_at = CURRENT_TIMESTAMP where id = ?", qs.get(1)); // câu lưu trữ
        long busy = bank(t, "PRACTICE", "PUBLISHED");
        var busyQs = questions(busy, 5, MCQ);
        insert("student_exercise_attempts", Map.of("user_id", u.id(), "bank_id", busy, "started_at", java.time.OffsetDateTime.now())); // lượt mở
        int expected = good.size() + 4; // bộ mixed còn 4 câu hợp lệ
        assertEquals(expected, card(u, "challenge", t).get("available").get("AUTO").asInt());
        long attempt = startOk(u, Map.of("scope", "CHALLENGE", "topicId", t));
        var picked = attemptQuestions(attempt);
        assertTrue(picked.stream().noneMatch(busyQs::contains));
        assertTrue(picked.stream().noneMatch(id -> id.equals(qs.get(0)) || id.equals(qs.get(1))));
        assertEquals(0, count("select count(*) from questions q join question_banks b on b.id = q.bank_id where q.id in (select question_id from attempt_questions where attempt_id = ?) and (b.mode = 'EXAM' or b.status <> 'PUBLISHED' or b.playback_mode = 'LOCKED')", attempt));
    }

    @Test
    void permissionsFollowEnrollmentTrialAndSequentialLock() throws Exception {
        newCourse(true);
        var stranger = student(5);
        var paid = student(5);
        enroll(paid);
        long t1 = topicWithQuestions("Một", 1, 10);
        long t2 = topicWithQuestions("Hai", 2, 10);
        long trial = topic("Học thử", 3, true);
        var trialAuto = questions(bank(trial, "PRACTICE", "PUBLISHED"), 5, MCQ);
        questions(jdbc.queryForObject("select id from question_banks where topic_id = ?", Long.class, trial), 3, "WRITING");

        // chưa mua: topic thường là PAID, không bắt đầu được; topic học thử chỉ dạng tự chấm
        assertEquals("PAID", card(stranger, "challenge", t1).get("state").asText());
        start(stranger, Map.of("scope", "CHALLENGE", "topicId", t1)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("extra_topic_not_available"));
        var tc = card(stranger, "challenge", trial);
        assertEquals("OPEN", tc.get("state").asText());
        assertEquals(true, tc.get("trialOnly").asBoolean());
        assertEquals(5, tc.get("available").get("AUTO").asInt());
        assertEquals(0, tc.get("available").get("WRITING").asInt());
        start(stranger, Map.of("scope", "CHALLENGE", "topicId", trial, "kind", "WRITING")).andExpect(status().isConflict());
        long attempt = startOk(stranger, Map.of("scope", "CHALLENGE", "topicId", trial));
        assertTrue(trialAuto.containsAll(attemptQuestions(attempt)));

        // đã mua nhưng học tuần tự: topic 2 khóa tới khi xong topic 1
        var locked = card(paid, "challenge", t2);
        assertEquals("LOCKED", locked.get("state").asText());
        assertEquals(t1, locked.get("blockedByTopicId").asLong());
        start(paid, Map.of("scope", "CHALLENGE", "topicId", t2)).andExpect(status().isForbidden());
        startOk(paid, Map.of("scope", "CHALLENGE", "topicId", t1));

        // lượt của người khác: 404; thiếu topic hoặc scope sai: 400
        getJson("/api/me/extra-practice/attempts/" + attempt, paid).andExpect(status().isNotFound());
        getJson("/api/me/extra-practice/attempts/" + attempt, stranger).andExpect(status().isOk());
        start(paid, Map.of("scope", "CHALLENGE")).andExpect(status().isBadRequest());
        start(paid, Map.of("scope", "NOPE")).andExpect(status().isBadRequest());
        start(paid, Map.of("scope", "REVIEW", "kind", "NOPE")).andExpect(status().isBadRequest());
    }

    @Test
    void attemptRunsThroughNormalFlowWithoutTouchingTopicProgress() throws Exception {
        newCourse(false);
        var u = student(5);
        enroll(u);
        long t = topicWithQuestions("Thử sức", 1, 30);
        long attempt = startOk(u, Map.of("scope", "CHALLENGE", "topicId", t));
        // chỉ một lượt Luyện thêm mở mỗi lần
        start(u, Map.of("scope", "CHALLENGE", "topicId", t)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("attempt_in_progress")).andExpect(jsonPath("$.error.details.attemptId").value(attempt));
        // đề không lộ đáp án
        var view = getJson("/api/me/extra-practice/attempts/" + attempt, u).andExpect(status().isOk())
                .andExpect(jsonPath("$.questions.length()").value(5)).andReturn().getResponse().getContentAsString();
        assertTrue(!view.contains("answerKey") && !view.contains("isCorrect"));
        // lưu nháp, kiểm tra từng câu, nộp
        long q0 = json.readTree(view).get("questions").get(0).get("id").asLong();
        send(MockMvcRequestBuilders.put("/api/attempts/" + attempt + "/draft").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("answers", List.of(Map.of("questionId", q0, "answer", Map.of("choiceId", 1)))))), u.accessToken())
                .andExpect(status().isOk());
        postJson("/api/attempts/" + attempt + "/questions/" + q0 + "/check", u, Map.of("answer", Map.of("choiceId", 1))).andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(false)).andExpect(jsonPath("$.checksLeft").value(2));
        postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers", List.of())).andExpect(status().isOk())
                .andExpect(jsonPath("$.maxScore").value(5)).andExpect(jsonPath("$.passPct").doesNotExist());
        // kết quả lưu như lượt thường (vào hồ sơ năng lực) nhưng Topic vẫn chưa hoàn thành và không có Ôn lại
        assertEquals(5, count("select count(*) from student_answers where attempt_id = ?", attempt));
        assertEquals("OPEN", card(u, "challenge", t).get("state").asText());
        assertEquals(0, overview(u).get("review").size());
        getJson("/api/me/extra-practice/attempts/" + attempt, u).andExpect(status().isOk());
        getJson("/api/attempts/" + attempt, u).andExpect(status().isOk()).andExpect(jsonPath("$.results.length()").value(5));
    }

    @Test
    void writingQuestionRunsInsideTheExtraAttemptAndOutsideQuestionsAreRejected() throws Exception {
        newCourse(false);
        var u = student(5);
        enroll(u);
        long t = topic("Viết", 1, false);
        long bank = bank(t, "PRACTICE", "PUBLISHED");
        long wq = writingQuestion(bank);
        long outside = writingQuestion(bank(topic("Khác", 2, false), "PRACTICE", "PUBLISHED"));
        long attempt = startOk(u, Map.of("scope", "CHALLENGE", "topicId", t, "kind", "WRITING"));
        assertEquals(Set.of(wq), attemptQuestions(attempt));
        guard.resetRateLimit(u.id());
        long session = idOf(postJson("/api/writing/sessions", u, Map.of("questionId", wq, "attemptId", attempt)).andExpect(status().isCreated()), "sessionId");
        guard.resetRateLimit(u.id());
        postJson("/api/writing/sessions", u, Map.of("questionId", outside, "attemptId", attempt)).andExpect(status().isBadRequest());
        // câu đang nằm trong lượt Luyện thêm mở: không luyện riêng (D-147 mục 11)
        guard.resetRateLimit(u.id());
        postJson("/api/writing/sessions", u, Map.of("questionId", wq)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("question_in_open_attempt"));
        postJson("/api/writing/sessions/" + session + "/submit", u, Map.of("finalText", "I agree with online learning because it is flexible. However, students need discipline."),
                UUID.randomUUID().toString()).andExpect(status().isAccepted());
        assertEquals(1, count("select count(*) from ai_jobs where user_id = ? and attempt_id = ?", u.id(), attempt)); // trừ lượt như luyện thường
    }
}
