package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** D-167 (V54, V55): bảng band cũ theo số câu đúng đổi sang % theo số câu của kỹ năng (làm tròn xuống), nhận thêm kỹ năng Viết/Nói. */
class V54ExamBandPercentMigrationTest {

    private final DriverManagerDataSource ds = new DriverManagerDataSource(
            "jdbc:h2:mem:v54-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
    private final JdbcTemplate jdbc = new JdbcTemplate(ds);

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(ds).locations("classpath:db/migration").target(target).load();
    }

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private long bank() {
        long c = insert("courses", Map.of("slug", "v54-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED"));
        long t = insert("topics", Map.of("level_id", lv, "title", "Topic", "status", "PUBLISHED"));
        return insert("question_banks", Map.of("topic_id", t, "title", "V54", "mode", "EXAM"));
    }

    private void questions(long bank, String skill, int n, boolean archived) {
        for (int i = 0; i < n; i++) {
            long q = insert("questions", Map.of("bank_id", bank, "type", "CONNECTIVE_MCQ", "prompt", "Câu", "skill", skill));
            if (archived) jdbc.update("update questions set archived_at = CURRENT_TIMESTAMP where id = ?", q);
        }
    }

    private void rule(long bank, String skill, int minCorrect, double band) {
        jdbc.update("insert into exam_band_rules (bank_id, skill, min_correct, band) values (?, ?, ?, ?)", bank, skill, minCorrect, band);
    }

    private List<Integer> percents(long bank, String skill) {
        return jdbc.queryForList("select min_percent from exam_band_rules where bank_id = ? and skill = ? order by min_percent", Integer.class, bank, skill);
    }

    @Test
    void convertsCountsToPercentRoundingDownAndWidensSkills() {
        flyway("53").migrate();
        long a = bank();
        questions(a, "READING", 3, false);
        questions(a, "READING", 2, true); // câu đã lưu trữ không tính vào số câu
        rule(a, "READING", 0, 4.0);
        rule(a, "READING", 1, 5.0);
        rule(a, "READING", 2, 6.0);
        rule(a, "READING", 3, 7.0);
        rule(a, "GRAMMAR", 2, 5.0); // kỹ năng không còn câu nào: mốc không với tới
        long dup = bank();
        questions(dup, "LISTENING", 150, false);
        rule(dup, "LISTENING", 0, 4.0);
        rule(dup, "LISTENING", 1, 5.0); // 1/150 làm tròn xuống 0%, trùng mốc 0 -> giữ dòng cao hơn
        rule(dup, "LISTENING", 75, 6.0);

        flyway("55").migrate();

        assertEquals(List.of(0, 33, 66, 100), percents(a, "READING")); // 1/3 = 33, 2/3 = 66 (xuống), 3/3 = 100
        assertEquals(List.of(100), percents(a, "GRAMMAR"));
        assertEquals(List.of(0, 50), percents(dup, "LISTENING"));
        assertEquals(5.0, jdbc.queryForObject("select band from exam_band_rules where bank_id = ? and min_percent = 0", Double.class, dup));

        // sau migrate: dòng mới không cần min_correct, nhận Viết/Nói, mốc phải trong 0..100
        jdbc.update("insert into exam_band_rules (bank_id, skill, min_percent, band) values (?, 'WRITING', 60, 5.0), (?, 'SPEAKING', 0, 3.0)", a, a);
        assertNull(jdbc.queryForObject("select min_correct from exam_band_rules where bank_id = ? and skill = 'WRITING'", Integer.class, a));
    }
}
