package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Tester D-166 (V53): lượt đã sửa điểm trước D-166 có machine_score = điểm máy cũ, score = điểm sửa mới nhất (bỏ dòng chỉ có nhận
 * xét); lượt không sửa điểm giữ nguyên. Mặc định chạy H2; chạy trên PostgreSQL thật bằng -Dv53.url=jdbc:postgresql://...
 * -Dv53.user -Dv53.password -Dv53.locations=classpath:db/migration,classpath:db/migration-pg (DB trống).
 */
class V53MachineScoreBackfillTesterTest {

    private final DriverManagerDataSource ds = new DriverManagerDataSource(
            System.getProperty("v53.url", "jdbc:h2:mem:v53-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1"),
            System.getProperty("v53.user", "sa"), System.getProperty("v53.password", ""));
    private final JdbcTemplate jdbc = new JdbcTemplate(ds);

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(ds).locations(System.getProperty("v53.locations", "classpath:db/migration").split(","))
                .target(target).load();
    }

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private long user() {
        return insert("users", Map.of("email", "u-" + UUID.randomUUID() + "@x.vn"));
    }

    private long attempt(long user, long bank, String score) {
        return insert("student_exercise_attempts", Map.of("user_id", user, "bank_id", bank, "submitted_at", Timestamp.from(Instant.now()),
                "score", new BigDecimal(score), "max_score", 10));
    }

    private void override(long attempt, long teacher, String score, String comment) {
        jdbc.update("insert into grade_overrides (attempt_id, score, comment, graded_by, graded_at) values (?, ?, ?, ?, CURRENT_TIMESTAMP)",
                attempt, score == null ? null : new BigDecimal(score), comment, teacher);
    }

    private BigDecimal col(String c, long attempt) {
        return jdbc.queryForObject("select " + c + " from student_exercise_attempts where id = ?", BigDecimal.class, attempt);
    }

    private static void same(String expected, BigDecimal actual, String msg) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), msg + " (thực tế " + actual + ")");
    }

    @Test
    void backfillCopiesMachineScoreAndAppliesLatestOverride() {
        flyway("51").migrate();
        long student = user();
        long teacher = user();
        long bank = insert("question_banks", Map.of("title", "V53", "purpose", "EXTRA", "mode", "PRACTICE"));

        long graded = attempt(student, bank, "7");
        override(graded, teacher, "8", null);
        override(graded, teacher, null, "nhận xét giữa chừng");
        override(graded, teacher, "9.5", "sửa lần hai");
        override(graded, teacher, null, "chỉ nhận xét, sau cùng");
        long untouched = attempt(student, bank, "5");
        long commentOnly = attempt(student, bank, "6");
        override(commentOnly, teacher, null, "chỉ nhận xét");
        long zero = attempt(student, bank, "3");
        override(zero, teacher, "0", null);

        flyway("53").migrate();

        same("7", col("machine_score", graded), "machine_score phải là điểm máy trước lần sửa");
        same("9.5", col("score", graded), "score phải là điểm sửa mới nhất có điểm");
        assertNull(col("machine_score", untouched));
        same("5", col("score", untouched), "lượt không sửa điểm giữ nguyên");
        assertNull(col("machine_score", commentOnly), "chỉ có nhận xét không tính là sửa điểm");
        same("6", col("score", commentOnly), "chỉ có nhận xét giữ điểm");
        same("3", col("machine_score", zero), "machine_score khi sửa về 0");
        same("0", col("score", zero), "điểm sửa bằng 0 vẫn được áp");
    }
}
