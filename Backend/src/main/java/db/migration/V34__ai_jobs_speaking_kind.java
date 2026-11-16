package db.migration;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Thêm SPEAKING_GRADE vào CHECK của ai_jobs.kind (FR-AI-03). CHECK ở V12 không đặt tên, PostgreSQL và H2 sinh tên khác nhau,
 * nên tìm theo nội dung rồi thay bằng ràng buộc có tên. Chạy được cho cả hai CSDL.
 */
public class V34__ai_jobs_speaking_kind extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws SQLException {
        List<String> names = new ArrayList<>();
        try (var st = context.getConnection().createStatement();
             ResultSet rs = st.executeQuery("select tc.constraint_name from information_schema.table_constraints tc "
                     + "join information_schema.check_constraints cc on cc.constraint_name = tc.constraint_name "
                     + "and cc.constraint_schema = tc.constraint_schema "
                     + "where lower(tc.table_name) = 'ai_jobs' and tc.constraint_type = 'CHECK' and cc.check_clause like '%WRITING_GRADE%'")) {
            while (rs.next()) {
                names.add(rs.getString(1));
            }
        }
        try (var st = context.getConnection().createStatement()) {
            for (String n : names) {
                st.execute("ALTER TABLE ai_jobs DROP CONSTRAINT \"" + n.replace("\"", "") + "\"");
            }
            st.execute("ALTER TABLE ai_jobs ADD CONSTRAINT ck_ai_jobs_kind CHECK (kind IN "
                    + "('WRITING_GRADE', 'SCAFFOLD_FOLLOWUP', 'SCAFFOLD_DRAFT', 'REWRITE_GRADE', 'SPEAKING_GRADE'))");
        }
    }
}
