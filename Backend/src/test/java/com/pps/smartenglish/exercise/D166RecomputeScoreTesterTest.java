package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.exercise.repository.StudentAttemptRepository;
import com.pps.smartenglish.support.ApiTestBase;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tester D-166: recomputeScore (JPQL thật) giữ điểm giáo viên sửa mới nhất và cập nhật machine_score theo credit; lượt chưa sửa
 * điểm thì score = tổng credit, machine_score vẫn null. Không dùng FORMAT JSON nên chạy được cả H2 lẫn PostgreSQL
 * (-Dspring.datasource.url=jdbc:postgresql://... -Dspring.flyway.locations=classpath:db/migration,classpath:db/migration-pg).
 */
class D166RecomputeScoreTesterTest extends ApiTestBase {

    private static final String CMS = "/api/admin/cms";

    @Autowired private StudentAttemptRepository attempts;
    @Autowired private TransactionTemplate tx;

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private long post(String url, Account a, Object body) throws Exception {
        return json.readTree(send(MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)),
                a.accessToken()).andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private long attempt(long user, long bank, String score, String machine, boolean submitted, String... credits) {
        var cols = new HashMap<String, Object>(Map.of("user_id", user, "bank_id", bank, "score", new BigDecimal(score), "max_score", credits.length));
        if (machine != null) {
            cols.put("machine_score", new BigDecimal(machine));
        }
        if (submitted) {
            cols.put("submitted_at", Timestamp.from(Instant.now()));
        }
        long a = insert("student_exercise_attempts", cols);
        for (int i = 0; i < credits.length; i++) {
            insert("student_answers", Map.of("attempt_id", a, "question_id", questions.get(i), "credit", new BigDecimal(credits[i]), "is_correct", true));
        }
        return a;
    }

    private List<Long> questions;

    private void override(long attempt, long teacher, String score, String comment) {
        var cols = new HashMap<String, Object>(Map.of("attempt_id", attempt, "graded_by", teacher, "graded_at", Timestamp.from(Instant.now())));
        if (score != null) {
            cols.put("score", new BigDecimal(score));
        }
        if (comment != null) {
            cols.put("comment", comment);
        }
        insert("grade_overrides", cols);
    }

    private BigDecimal col(String c, long attempt) {
        return jdbc.queryForObject("select " + c + " from student_exercise_attempts where id = ?", BigDecimal.class, attempt);
    }

    private static void same(String expected, BigDecimal actual, String msg) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), msg + " (thực tế " + actual + ")");
    }

    @Test
    void recomputeKeepsLatestTeacherScoreAndRefreshesMachineScore() throws Exception {
        var u = register(newEmail(), "Password123!");
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = 'ADMIN'", u.id());
        var admin = login(u.email(), "Password123!");
        long course = insert("courses", Map.of("slug", "rc-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", course, "code", "A", "title", "L", "status", "PUBLISHED"));
        long topic = insert("topics", Map.of("level_id", lv, "title", "T", "status", "PUBLISHED"));
        long bank = post(CMS + "/topics/" + topic + "/banks", admin, Map.of("title", "Bộ " + UUID.randomUUID()));
        var mcq = Map.of("type", "CONNECTIVE_MCQ", "prompt", "x", "choices",
                List.of(Map.of("label", "A", "content", "x", "isCorrect", true), Map.of("label", "B", "content", "y", "isCorrect", false)));
        questions = List.of(post(CMS + "/banks/" + bank + "/questions", admin, mcq),
                post(CMS + "/banks/" + bank + "/questions", admin, Map.of("type", "CONNECTIVE_MCQ", "prompt", "y", "choices", mcq.get("choices"))));
        long student = register(newEmail(), "Password123!").id();

        long plain = attempt(student, bank, "1", null, true, "1", "0.5");
        long graded = attempt(student, bank, "5", "1", true, "1", "1");
        override(graded, u.id(), "1.5", null);
        override(graded, u.id(), "1.75", "sửa lần hai");
        override(graded, u.id(), null, "chỉ nhận xét sau cùng");
        long draft = attempt(student, bank, "0", null, false, "1", "1");

        tx.executeWithoutResult(s -> List.of(plain, graded, draft).forEach(attempts::recomputeScore));

        same("1.5", col("score", plain), "chưa sửa điểm: score = tổng credit");
        assertNull(col("machine_score", plain), "chưa sửa điểm: machine_score vẫn null");
        same("1.75", col("score", graded), "đã sửa: score = điểm sửa mới nhất có điểm");
        same("2", col("machine_score", graded), "đã sửa: machine_score = tổng credit mới");
        same("0", col("score", draft), "lượt chưa nộp không bị tính lại");
    }
}
