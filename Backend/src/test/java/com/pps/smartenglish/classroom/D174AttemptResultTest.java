package com.pps.smartenglish.classroom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.ai.AiTestSupport;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** D-174 (FR-ASG-03/04, NFR-SEC-05): giáo viên xem kết quả từng câu của lượt nộp bài giao bộ câu; chỉ đọc, ngoài phạm vi thì 404. */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class D174AttemptResultTest extends AiTestSupport {

    @Autowired EntityManagerFactory emf;

    private record Bank(long id, List<Long> questions, List<Long> rights) {}

    private record Fx(long course, long cls, Bank bank, Account teacher) {}

    private ResultActions call(HttpMethod method, String url, Account who, Object body) throws Exception {
        var req = MockMvcRequestBuilders.request(method, url);
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return send(req, who.accessToken());
    }

    private Account staff(long cls, String role, LocalDate to) throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = 'TEACHER'", u.id());
        jdbc.update("insert into class_staff (class_id, user_id, role, assigned_from, assigned_to) values (?, ?, ?, ?, ?)",
                cls, u.id(), role, LocalDate.now().minusDays(10), to);
        return login(u.email(), PASSWORD);
    }

    private Bank bank(long topic, int n) {
        long bank = insert("question_banks", Map.of("topic_id", topic, "title", "Bộ " + UUID.randomUUID().toString().substring(0, 4),
                "is_trial", false, "status", "PUBLISHED", "mode", "PRACTICE"));
        var qs = new ArrayList<Long>();
        var rights = new ArrayList<Long>();
        for (int i = 0; i < n; i++) {
            long q = question(bank, "CONNECTIVE_MCQ", i + 1, "Chọn từ nối " + i, "{}", "{}");
            rights.add(insert("question_choices", Map.of("question_id", q, "label", "A", "content", "although", "is_correct", true)));
            insert("question_choices", Map.of("question_id", q, "label", "B", "content", "so", "is_correct", false));
            qs.add(q);
        }
        return new Bank(bank, qs, rights);
    }

    private long classOf(long course) {
        return insert("classes", Map.of("course_id", course, "name", "Lớp " + UUID.randomUUID().toString().substring(0, 4),
                "starts_on", LocalDate.now().minusDays(10), "ends_on", LocalDate.now().plusDays(60), "mode", "ONLINE", "capacity", 20));
    }

    private Fx fixture(int questions) throws Exception {
        long c = insert("courses", Map.of("slug", "d174-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED",
                "delivery_mode", "CLASS_BASED", "sequential", false));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED", "sort_order", 0));
        long t = insert("topics", Map.of("level_id", lv, "title", "Unit A", "is_free_trial", false, "status", "PUBLISHED", "sort_order", 0));
        long cls = classOf(c);
        return new Fx(c, cls, bank(t, questions), staff(cls, "TEACHER", null));
    }

    private Account member(Fx f) throws Exception {
        var s = student(3);
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', TIMESTAMP '2020-01-01 00:00:00', TIMESTAMP '2099-01-01 00:00:00')",
                s.id(), f.course());
        jdbc.update("insert into class_members (class_id, course_id, user_id, status, live_key, joined_at) values (?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP)",
                f.cls(), f.course(), s.id(), f.course() + ":" + s.id());
        return s;
    }

    private String base(Fx f) {
        return "/api/classes/" + f.cls() + "/assignments";
    }

    private long assign(Fx f, Map<String, Object> target) throws Exception {
        var body = new HashMap<String, Object>(target);
        body.put("dueAt", Instant.now().plus(48, ChronoUnit.HOURS).toString());
        return idOf(call(HttpMethod.POST, base(f), f.teacher(), body).andExpect(status().isCreated()), "id");
    }

    /** Học viên làm bài giao: câu đầu đúng, các câu sau chọn sai. */
    private long submit(Fx f, Account s, long asg) throws Exception {
        long attempt = idOf(call(HttpMethod.POST, base(f) + "/" + asg + "/start", s, null).andExpect(status().isOk()), "id");
        var answers = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < f.bank().questions().size(); i++) {
            long right = f.bank().rights().get(i);
            answers.add(Map.of("questionId", f.bank().questions().get(i), "answer", Map.of("choiceId", i == 0 ? right : right + 1)));
        }
        guard.resetRateLimit(s.id());
        postJson("/api/attempts/" + attempt + "/submit", s, Map.of("answers", answers)).andExpect(status().isOk());
        return attempt;
    }

    private String url(long cls, long asg, long attempt) {
        return "/api/classes/" + cls + "/assignments/" + asg + "/attempts/" + attempt + "/result";
    }

    private long rawSelfAttempt(Fx f, Account s) {
        return insert("student_exercise_attempts", Map.of("user_id", s.id(), "bank_id", f.bank().id(),
                "submitted_at", Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)), "score", 1, "max_score", 1));
    }

    @Test
    void teacherSeesSameResultAsStudentEvenWhenCancelled() throws Exception {
        var f = fixture(2);
        var s = member(f);
        long asg = assign(f, Map.of("bankId", f.bank().id()));
        long attempt = submit(f, s, asg);
        var aide = staff(f.cls(), "ASSISTANT", null);
        var s2 = member(f); // học viên thứ hai làm cùng bài giao: câu trả lời không lẫn sang lượt của s
        submit(f, s2, asg);

        String mine = getJson("/api/attempts/" + attempt, s).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (var who : List.of(f.teacher(), aide)) {
            String seen = call(HttpMethod.GET, url(f.cls(), asg, attempt), who, null).andExpect(status().isOk())
                    .andExpect(jsonPath("$.attempt.results.length()").value(2)).andExpect(jsonPath("$.attempt.results[0].isCorrect").value(true))
                    .andExpect(jsonPath("$.attempt.results[1].isCorrect").value(false)).andExpect(jsonPath("$.bank.questions.length()").value(2))
                    .andExpect(jsonPath("$.bank.questions[0].choices.length()").value(2)).andExpect(jsonPath("$.bank.questions[0].correctAnswer").doesNotExist())
                    .andReturn().getResponse().getContentAsString();
            var node = json.readTree(seen);
            assertEquals(json.readTree(mine), node.get("attempt"));
            assertEquals(2, node.get("answers").size());
            for (var ans : node.get("answers")) { // câu trả lời đúng của học viên này, không lẫn người khác
                int i = f.bank().questions().indexOf(ans.get("questionId").asLong());
                long right = f.bank().rights().get(i);
                assertEquals(i == 0 ? right : right + 1, ans.at("/answer/choiceId").asLong());
                assertEquals(i == 0, ans.get("isCorrect").asBoolean());
                assertEquals(i == 0 ? 1.0 : 0.0, ans.get("credit").asDouble());
            }
        }
        postJson(base(f) + "/" + asg + "/cancel", f.teacher(), Map.of()).andExpect(status().isOk());
        call(HttpMethod.GET, url(f.cls(), asg, attempt), f.teacher(), null).andExpect(status().isOk());
    }

    @Test
    void withdrawnStudentAttemptStaysReadable() throws Exception {
        var f = fixture(1);
        var s = member(f);
        long asg = assign(f, Map.of("bankId", f.bank().id()));
        long attempt = submit(f, s, asg);
        jdbc.update("update class_members set status = 'WITHDRAWN', live_key = null where class_id = ? and user_id = ?", f.cls(), s.id());
        call(HttpMethod.GET, url(f.cls(), asg, attempt), f.teacher(), null).andExpect(status().isOk());
    }

    @Test
    void outsideScopeIs404() throws Exception {
        var f = fixture(2);
        var s = member(f);
        long asg = assign(f, Map.of("bankId", f.bank().id()));
        long attempt = submit(f, s, asg);
        String ok = url(f.cls(), asg, attempt);

        var other = fixture(1);
        var otherStudent = member(other);
        var expired = staff(f.cls(), "ASSISTANT", LocalDate.now().minusDays(1));
        for (var who : List.of(other.teacher(), otherStudent, s, expired)) {
            call(HttpMethod.GET, ok, who, null).andExpect(status().isNotFound());
        }
        // bài giao khác cùng lớp (cũng của chính học viên này): attemptId không thuộc bài giao trong đường dẫn
        long asg2 = assign(f, Map.of("bankId", f.bank().id()));
        call(HttpMethod.GET, url(f.cls(), asg2, attempt), f.teacher(), null).andExpect(status().isNotFound());
        // lượt tự luyện không thuộc bài giao nào
        call(HttpMethod.GET, url(f.cls(), asg, rawSelfAttempt(f, s)), f.teacher(), null).andExpect(status().isNotFound());
        // lượt chưa nộp
        long open = idOf(call(HttpMethod.POST, base(f) + "/" + asg2 + "/start", s, null).andExpect(status().isOk()), "id");
        call(HttpMethod.GET, url(f.cls(), asg2, open), f.teacher(), null).andExpect(status().isNotFound());
        // bài giao của lớp khác dùng id lớp này
        call(HttpMethod.GET, url(f.cls(), 999999, attempt), f.teacher(), null).andExpect(status().isNotFound());
        call(HttpMethod.GET, ok, f.teacher(), null).andExpect(status().isOk());
    }

    @Test
    void writingAssignmentIsRejected() throws Exception {
        var f = fixture(1);
        long q = writingQuestion(f.bank().id());
        long asg = assign(f, Map.of("questionId", q));
        call(HttpMethod.GET, url(f.cls(), asg, rawSelfAttempt(f, member(f))), f.teacher(), null).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("attempt_not_found"));
    }

    @Test
    void queryCountDoesNotGrowWithQuestions() throws Exception {
        long small = measure(2);
        long big = measure(12);
        System.out.printf("[D-174] GET result: 2 cau -> %d SQL, 12 cau -> %d SQL%n", small, big);
        assertTrue(big - small <= 3, "SQL tăng theo số câu: " + small + " -> " + big);
    }

    private long measure(int n) throws Exception {
        var f = fixture(n);
        var s = member(f);
        long asg = assign(f, Map.of("bankId", f.bank().id()));
        long attempt = submit(f, s, asg);
        call(HttpMethod.GET, url(f.cls(), asg, attempt), f.teacher(), null).andExpect(status().isOk()); // khởi động
        var stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        call(HttpMethod.GET, url(f.cls(), asg, attempt), f.teacher(), null).andExpect(status().isOk());
        return stats.getPrepareStatementCount();
    }
}
