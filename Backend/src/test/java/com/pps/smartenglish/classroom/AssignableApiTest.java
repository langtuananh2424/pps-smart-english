package com.pps.smartenglish.classroom;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.ai.AiTestSupport;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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

/** FR-ASG-01, FR-CLS-05, FR-CLS-06: cây nội dung giao được, tên người mở sớm, số người nhận và số đã nộp; NFR-SEC-05. */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class AssignableApiTest extends AiTestSupport {

    @Autowired EntityManagerFactory emf;

    private record Fx(long course, long classId, long topicA, long topicB, long bankA, long bankB, long qWrite, long qSpeak, Account teacher) {}

    private Account withRole(String role) throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = ?", u.id(), role);
        return login(u.email(), PASSWORD);
    }

    private ResultActions call(HttpMethod method, String url, String token, Object body) throws Exception {
        var req = MockMvcRequestBuilders.request(method, url);
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return send(req, token);
    }

    private long bank(long topic, String status, String mode) {
        return insert("question_banks", Map.of("topic_id", topic, "title", "Bộ " + UUID.randomUUID().toString().substring(0, 4),
                "is_trial", false, "status", status, "mode", mode));
    }

    private long topic(long level, String title, String status, int order) {
        return insert("topics", Map.of("level_id", level, "title", title, "is_free_trial", false, "status", status, "sort_order", order));
    }

    private long course() {
        return insert("courses", Map.of("slug", "asb-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED",
                "delivery_mode", "CLASS_BASED", "sequential", true));
    }

    private long level(long course) {
        return insert("course_levels", Map.of("course_id", course, "code", "A", "title", "Level A", "status", "PUBLISHED", "sort_order", 0));
    }

    private long newClass(long course) {
        return insert("classes", Map.of("course_id", course, "name", "Lớp " + UUID.randomUUID().toString().substring(0, 4),
                "starts_on", LocalDate.now().minusDays(10), "ends_on", LocalDate.now().plusDays(60), "mode", "ONLINE", "capacity", 20));
    }

    private Account teacherOf(long classId, String name) throws Exception {
        var t = withRole("TEACHER");
        jdbc.update("insert into class_staff (class_id, user_id, role, assigned_from) values (?, ?, 'TEACHER', ?)", classId, t.id(), LocalDate.now().minusDays(10));
        jdbc.update("update user_profiles set display_name = ? where user_id = ?", name, t.id());
        return t;
    }

    private Fx fixture() throws Exception {
        long c = course();
        long lv = level(c);
        long ta = topic(lv, "Unit A", "PUBLISHED", 0);
        long tb = topic(lv, "Unit B", "PUBLISHED", 1);
        long bankA = bank(ta, "PUBLISHED", "PRACTICE");
        long qW = writingQuestion(bankA);
        long qS = question(bankA, "SPEAKING", 2, "Describe a trip.", "{\"part\":1,\"prompt\":\"Describe a trip.\"}", "{}");
        long qArchived = writingQuestion(bankA);
        jdbc.update("update questions set archived_at = CURRENT_TIMESTAMP where id = ?", qArchived);
        long bankB = bank(tb, "PUBLISHED", "PRACTICE");
        writingQuestion(bankB);
        // không hiện: bộ nháp, bộ rỗng, bộ thi (câu Viết không giao riêng), unit nháp
        writingQuestion(bank(ta, "DRAFT", "PRACTICE"));
        bank(ta, "PUBLISHED", "PRACTICE");
        writingQuestion(bank(tb, "PUBLISHED", "EXAM"));
        writingQuestion(bank(topic(lv, "Unit nháp", "DRAFT", 2), "PUBLISHED", "PRACTICE"));
        // khóa khác
        long other = course();
        writingQuestion(bank(topic(level(other), "Unit khác", "PUBLISHED", 0), "PUBLISHED", "PRACTICE"));
        long cls = newClass(c);
        return new Fx(c, cls, ta, tb, bankA, bankB, qW, qS, teacherOf(cls, "Cô Lan"));
    }

    private Account member(Fx f) throws Exception {
        var s = student(3);
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', TIMESTAMP '2020-01-01 00:00:00', TIMESTAMP '2099-01-01 00:00:00')", s.id(), f.course());
        jdbc.update("insert into class_members (class_id, course_id, user_id, status, live_key, joined_at) values (?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP)",
                f.classId(), f.course(), s.id(), f.course() + ":" + s.id());
        return s;
    }

    @Test
    void staffSeesOnlyPublishedAssignableContentOfTheClassCourse() throws Exception {
        var f = fixture();
        call(HttpMethod.GET, "/api/classes/" + f.classId() + "/assignable", f.teacher().accessToken(), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.courseId").value(f.course()))
                .andExpect(jsonPath("$.topics", hasSize(2)))
                .andExpect(jsonPath("$.topics[0].topicId").value(f.topicA()))
                .andExpect(jsonPath("$.topics[0].position").value(0))
                .andExpect(jsonPath("$.topics[0].banks", hasSize(1)))
                .andExpect(jsonPath("$.topics[0].banks[0].bankId").value(f.bankA()))
                .andExpect(jsonPath("$.topics[0].banks[0].kind").value("PRACTICE"))
                .andExpect(jsonPath("$.topics[0].banks[0].questionCount").value(2)) // 3 câu, 1 đã lưu trữ
                .andExpect(jsonPath("$.topics[0].aiQuestions", hasSize(2)))
                .andExpect(jsonPath("$.topics[0].aiQuestions[0].questionId").value(f.qWrite()))
                .andExpect(jsonPath("$.topics[0].aiQuestions[0].type").value("WRITING"))
                .andExpect(jsonPath("$.topics[0].aiQuestions[1].type").value("SPEAKING"))
                .andExpect(jsonPath("$.topics[1].topicId").value(f.topicB()))
                .andExpect(jsonPath("$.topics[1].banks", hasSize(2))) // gồm bộ thi (giao cả bộ được), nhưng câu Viết của bộ thi không giao riêng
                .andExpect(jsonPath("$.topics[1].aiQuestions", hasSize(1)));
    }

    @Test
    void outsidersGet404() throws Exception {
        var a = fixture();
        var b = fixture();
        var s = member(a);
        call(HttpMethod.GET, "/api/classes/" + a.classId() + "/assignable", b.teacher().accessToken(), null).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("class_not_found"));
        call(HttpMethod.GET, "/api/classes/" + a.classId() + "/assignable", s.accessToken(), null).andExpect(status().isNotFound());
    }

    @Test
    void assignableQueryCountDoesNotGrowWithTopics() throws Exception {
        var f = fixture();
        call(HttpMethod.GET, "/api/classes/" + f.classId() + "/assignable", f.teacher().accessToken(), null).andExpect(status().isOk());
        var stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        call(HttpMethod.GET, "/api/classes/" + f.classId() + "/assignable", f.teacher().accessToken(), null).andExpect(status().isOk());
        long before = stats.getPrepareStatementCount();
        long lv = jdbc.queryForObject("select id from course_levels where course_id = ?", Long.class, f.course());
        for (int i = 0; i < 10; i++) writingQuestion(bank(topic(lv, "T" + i, "PUBLISHED", 10 + i), "PUBLISHED", "PRACTICE"));
        stats.clear();
        call(HttpMethod.GET, "/api/classes/" + f.classId() + "/assignable", f.teacher().accessToken(), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.topics", hasSize(12)));
        assertEquals(before, stats.getPrepareStatementCount());
    }

    @Test
    void unlockViewCarriesGrantorName() throws Exception {
        var f = fixture();
        String url = "/api/classes/" + f.classId() + "/unlocks";
        call(HttpMethod.POST, url, f.teacher().accessToken(), Map.of("topicId", f.topicB())).andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].grantedByName").value("Cô Lan"));
        call(HttpMethod.GET, url, f.teacher().accessToken(), null).andExpect(jsonPath("$[0].grantedByName").value("Cô Lan"));
    }

    @Test
    void staffListHasRecipientAndSubmittedCountsStudentGetsNull() throws Exception {
        var f = fixture();
        var s1 = member(f);
        var s2 = member(f);
        member(f);
        String base = "/api/classes/" + f.classId() + "/assignments";
        Instant due = Instant.now().plus(2, ChronoUnit.DAYS);
        long whole = idOf(call(HttpMethod.POST, base, f.teacher().accessToken(), Map.of("bankId", f.bankA(), "dueAt", due.toString()))
                .andExpect(status().isCreated()), "id");
        long some = idOf(call(HttpMethod.POST, base, f.teacher().accessToken(),
                Map.of("bankId", f.bankB(), "dueAt", due.toString(), "studentIds", List.of(s1.id(), s2.id()))).andExpect(status().isCreated()), "id");
        insert("student_exercise_attempts", Map.of("user_id", s1.id(), "bank_id", f.bankA(), "assignment_id", whole,
                "submitted_at", Timestamp.from(Instant.now()), "score", 1, "max_score", 1));
        // id giảm dần: bài giao một số học viên (some) đứng trước
        call(HttpMethod.GET, base, f.teacher().accessToken(), null).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(some)).andExpect(jsonPath("$[0].recipients").value(2)).andExpect(jsonPath("$[0].submitted").value(0))
                .andExpect(jsonPath("$[1].id").value(whole)).andExpect(jsonPath("$[1].recipients").value(3)).andExpect(jsonPath("$[1].submitted").value(1));
        call(HttpMethod.GET, base, s1.accessToken(), null).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].recipients").doesNotExist()).andExpect(jsonPath("$[0].submitted").doesNotExist());
    }
}
