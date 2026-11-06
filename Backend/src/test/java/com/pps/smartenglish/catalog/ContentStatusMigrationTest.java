package com.pps.smartenglish.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/** V17: dòng đã có trước migration thành PUBLISHED để không biến mất; dòng tạo sau đó mặc định DRAFT, ngoài hai giá trị thì bị CHECK chặn. */
class ContentStatusMigrationTest {

    private static final String URL = "jdbc:h2:mem:v17check;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(URL, "sa", "").locations("classpath:db/migration").target(target).load();
    }

    private String scalar(Connection c, String sql) throws SQLException {
        try (var st = c.createStatement(); var rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    @Test
    void existingRowsBecomePublishedAndNewRowsDefaultToDraft() throws Exception {
        flyway("16").migrate();
        try (Connection c = DriverManager.getConnection(URL, "sa", ""); var st = c.createStatement()) {
            st.execute("insert into courses (slug, title, status) values ('old', 'Cũ', 'PUBLISHED')");
            st.execute("insert into course_levels (course_id, code, title) values ((select id from courses where slug = 'old'), 'A', 'A')");
            st.execute("insert into topics (level_id, title) values ((select id from course_levels where code = 'A'), 'T')");
            st.execute("insert into question_banks (topic_id, title) values ((select id from topics where title = 'T'), 'B')");

            flyway("17").migrate();

            assertEquals("PUBLISHED", scalar(c, "select status from course_levels where code = 'A'"));
            assertEquals("PUBLISHED", scalar(c, "select status from topics where title = 'T'"));
            assertEquals("PUBLISHED", scalar(c, "select status from question_banks where title = 'B'"));

            st.execute("insert into course_levels (course_id, code, title) values ((select id from courses where slug = 'old'), 'B', 'B')");
            st.execute("insert into topics (level_id, title) values ((select id from course_levels where code = 'B'), 'T2')");
            st.execute("insert into question_banks (topic_id, title) values ((select id from topics where title = 'T2'), 'B2')");
            assertEquals("DRAFT", scalar(c, "select status from course_levels where code = 'B'"));
            assertEquals("DRAFT", scalar(c, "select status from topics where title = 'T2'"));
            assertEquals("DRAFT", scalar(c, "select status from question_banks where title = 'B2'"));
        }
    }
}
