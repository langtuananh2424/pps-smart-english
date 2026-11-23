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
 * FR-EX-04, FR-EX-05: thêm 5 kiểu câu (CHOICE_MULTI, TRUE_FALSE, FILL_TEXT, WORD_BANK, MATCH_OPTIONS) và 2 kỹ năng (WRITING, SPEAKING).
 * CHECK của type ở V4 không đặt tên (H2 và PostgreSQL tự đặt tên khác nhau) và hẹp hơn giá trị mới, nên phải tra tên rồi bỏ
 * (khác V11/V12 vốn chỉ nới rộng nên giữ CHECK cũ cũng vô hại). Cùng cách làm với V20; chỉ thêm giá trị, dữ liệu cũ vẫn hợp lệ.
 */
public class V28__question_types_and_skills extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();
        List<String> old = new ArrayList<>(checksMatching(conn, "grammar_build"));
        old.addAll(checksMatching(conn, "listening"));
        try (Statement st = conn.createStatement()) {
            for (String name : old) {
                st.execute("ALTER TABLE questions DROP CONSTRAINT \"" + name + "\"");
            }
            st.execute("ALTER TABLE questions ADD CONSTRAINT ck_questions_type CHECK (type IN ("
                    + "'CONNECTIVE_MCQ', 'CONNECTIVE_MATCH', 'CONNECTIVE_CLOZE', 'CONNECTIVE_REWRITE', "
                    + "'GRAMMAR_IDENTIFY', 'GRAMMAR_SPOT_FIX', 'GRAMMAR_BUILD', 'GRAMMAR_REWRITE', 'WRITING', 'SPEAKING', "
                    + "'CHOICE_MULTI', 'TRUE_FALSE', 'FILL_TEXT', 'WORD_BANK', 'MATCH_OPTIONS'))");
            st.execute("ALTER TABLE questions ADD CONSTRAINT ck_questions_skill_v2 CHECK (skill IS NULL OR skill IN "
                    + "('LISTENING', 'READING', 'GRAMMAR', 'WRITING', 'SPEAKING'))");
        }
    }

    /** Tên các CHECK của bảng questions có điều kiện chứa từ khóa. */
    private static List<String> checksMatching(Connection conn, String keyword) throws Exception {
        List<String> names = new ArrayList<>();
        String sql = "SELECT tc.constraint_name FROM information_schema.table_constraints tc "
                + "JOIN information_schema.check_constraints cc ON cc.constraint_name = tc.constraint_name "
                + "AND cc.constraint_schema = tc.constraint_schema "
                + "WHERE LOWER(tc.table_name) = 'questions' AND LOWER(tc.table_schema) = LOWER(?) "
                + "AND tc.constraint_type = 'CHECK' AND LOWER(cc.check_clause) LIKE ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, conn.getSchema());
            ps.setString(2, "%" + keyword + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString(1));
                }
            }
        }
        return names;
    }
}
