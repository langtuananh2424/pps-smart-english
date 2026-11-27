package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * D-167: bảng band nhận thêm kỹ năng WRITING, SPEAKING. Tra CHECK skill theo nội dung (tên tự sinh khác nhau giữa PostgreSQL và H2)
 * rồi thay; chỉ nới nên bản chạy cũ vẫn tương thích.
 */
public class V55__exam_band_rules_skills extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();
        List<String> old = skillChecks(conn);
        try (Statement st = conn.createStatement()) {
            for (String name : old) {
                st.execute("ALTER TABLE exam_band_rules DROP CONSTRAINT \"" + name + "\"");
            }
            st.execute("ALTER TABLE exam_band_rules ADD CONSTRAINT ck_exam_band_rules_skill "
                    + "CHECK (skill IN ('LISTENING', 'READING', 'GRAMMAR', 'WRITING', 'SPEAKING'))");
        }
    }

    /** Tên các CHECK của exam_band_rules nhắc tới LISTENING (tức CHECK skill). */
    private static List<String> skillChecks(Connection conn) throws Exception {
        List<String> names = new ArrayList<>();
        String sql = "SELECT tc.constraint_name FROM information_schema.table_constraints tc "
                + "JOIN information_schema.check_constraints cc ON cc.constraint_name = tc.constraint_name "
                + "AND cc.constraint_schema = tc.constraint_schema "
                + "WHERE LOWER(tc.table_name) = 'exam_band_rules' AND LOWER(tc.table_schema) = LOWER(?) "
                + "AND tc.constraint_type = 'CHECK' AND LOWER(cc.check_clause) LIKE '%listening%'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, conn.getSchema());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString(1));
                }
            }
        }
        return names;
    }
}
