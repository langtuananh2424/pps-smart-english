package com.pps.smartenglish.classroom;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.ai.AiTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * Tester (FR-ASG-02, checklist P2 "N+1"): đo số câu SQL của GET /api/me/assignments theo số bài.
 * Hiện views() → myStatus() chạy 1 truy vấn mỗi bài (latestSubmitted / latestOf), tới ~500 câu mỗi lần gọi ở trần MINE_CAP.
 * Test chỉ chặn tệ hơn tuyến tính và đặt ngân sách thời gian; số đo in ra để theo dõi nợ kỹ thuật.
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class AssignmentMineQueryCountTesterTest extends AiTestSupport {

    @Autowired EntityManagerFactory emf;

    private record Fx(long classId, long courseId, long bank, long qW, long qS, Account teacher) {}

    private Fx fixture() throws Exception {
        long c = insert("courses", Map.of("slug", "mt-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED",
                "delivery_mode", "CLASS_BASED", "sequential", false));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED", "sort_order", 0));
        long t = insert("topics", Map.of("level_id", lv, "title", "Unit", "is_free_trial", false, "status", "PUBLISHED", "sort_order", 0));
        long bank = insert("question_banks", Map.of("topic_id", t, "title", "Bộ", "is_trial", false, "status", "PUBLISHED", "mode", "PRACTICE"));
        long qW = writingQuestion(bank);
        long qS = question(bank, "SPEAKING", 2, "Describe a trip.", "{\"part\":1,\"prompt\":\"Describe a trip.\"}", "{}");
        long cls = insert("classes", Map.of("course_id", c, "name", "Lớp", "starts_on", LocalDate.now().minusDays(10),
                "ends_on", LocalDate.now().plusDays(600), "mode", "ONLINE", "capacity", 20));
        var tu = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = 'TEACHER'", tu.id());
        var teacher = login(tu.email(), PASSWORD);
        jdbc.update("insert into class_staff (class_id, user_id, role, assigned_from) values (?, ?, 'TEACHER', ?)", cls, teacher.id(), LocalDate.now().minusDays(10));
        return new Fx(cls, c, bank, qW, qS, teacher);
    }

    private Account member(Fx f) throws Exception {
        var s = student(3);
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', TIMESTAMP '2020-01-01 00:00:00', TIMESTAMP '2099-01-01 00:00:00')", s.id(), f.courseId());
        jdbc.update("insert into class_members (class_id, course_id, user_id, status, live_key, joined_at) values (?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP)",
                f.classId(), f.courseId(), s.id(), f.courseId() + ":" + s.id());
        return s;
    }

    /** Bài giao cả bộ, chèn thẳng DB (nhanh, không qua giới hạn tạo). */
    private long rawAssignment(Fx f, Instant due) {
        return insert("assignments", Map.of("class_id", f.classId(), "bank_id", f.bank(), "due_at", Timestamp.from(due),
                "status", "ACTIVE", "created_by", f.teacher().id()));
    }

    private void rawSubmit(long asg, Fx f, Account s) {
        insert("student_exercise_attempts", Map.of("user_id", s.id(), "bank_id", f.bank(), "assignment_id", asg,
                "submitted_at", Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)), "score", 1, "max_score", 1));
    }

    private List<Long> mineIds(Account s) throws Exception {
        String body = getJson("/api/me/assignments", s).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Long> out = new ArrayList<>();
        json.readTree(body).forEach(n -> out.add(n.get("id").asLong()));
        return out;
    }

    private long[] measure(int n) throws Exception {
        var f = fixture();
        var s = member(f);
        Instant base = Instant.now().plus(1, ChronoUnit.DAYS);
        for (int i = 0; i < n; i++) rawAssignment(f, base.plus(i, ChronoUnit.MINUTES));
        mineIds(s); // khởi động
        var stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        long t0 = System.nanoTime();
        assertEquals(n, mineIds(s).size());
        long ms = (System.nanoTime() - t0) / 1_000_000;
        return new long[] {stats.getPrepareStatementCount(), ms};
    }

    @Test
    void mineListQueryCountGrowsAtMostLinearlyAndStaysWithinBudgetAtCap() throws Exception {
        long[] small = measure(10);
        long[] cap = measure(500);
        System.out.printf("[tester] /api/me/assignments: 10 bai -> %d cau SQL, %d ms; 500 bai -> %d cau SQL, %d ms%n", small[0], small[1], cap[0], cap[1]);
        assertTrue(cap[0] - small[0] <= 490 + 10, "tệ hơn 1 câu/bài: " + small[0] + " -> " + cap[0]);
        assertTrue(cap[1] < 5000, "500 bài mất " + cap[1] + " ms");
    }
}
