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
 * D-154, D-157 (FR-EX-04, FR-EX-05): thêm 20 kiểu câu (LETTER_ORDER, DICTATION, LISTEN_FORM, 17 kiểu IELTS_*) và cột question_groups.layout.
 * Cùng cách V28: CHECK của type do V28 đặt tên nhưng H2 và PostgreSQL tra tên khác nhau nên vẫn tra theo nội dung rồi bỏ.
 * Chỉ nới CHECK và thêm cột null được nên bản chạy cũ vẫn tương thích.
 */
public class V47__question_types_ielts_layout extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();
        List<String> old = typeChecks(conn);
        try (Statement st = conn.createStatement()) {
            for (String name : old) {
                st.execute("ALTER TABLE questions DROP CONSTRAINT \"" + name + "\"");
            }
            st.execute("ALTER TABLE questions ADD CONSTRAINT ck_questions_type_v2 CHECK (type IN ("
                    + "'CONNECTIVE_MCQ', 'CONNECTIVE_MATCH', 'CONNECTIVE_CLOZE', 'CONNECTIVE_REWRITE', "
                    + "'GRAMMAR_IDENTIFY', 'GRAMMAR_SPOT_FIX', 'GRAMMAR_BUILD', 'GRAMMAR_REWRITE', 'WRITING', 'SPEAKING', "
                    + "'CHOICE_MULTI', 'TRUE_FALSE', 'FILL_TEXT', 'WORD_BANK', 'MATCH_OPTIONS', "
                    + "'LETTER_ORDER', 'DICTATION', 'LISTEN_FORM', "
                    + "'IELTS_TFNG', 'IELTS_YNNG', 'IELTS_MATCH_INFO', 'IELTS_MATCH_HEADINGS', 'IELTS_MATCH_FEATURES', "
                    + "'IELTS_MATCH_ENDINGS', 'IELTS_MATCHING', 'IELTS_SENTENCE', 'IELTS_SUMMARY', 'IELTS_SUMMARY_BANK', "
                    + "'IELTS_NOTE', 'IELTS_TABLE', 'IELTS_FLOWCHART', 'IELTS_FORM', 'IELTS_DIAGRAM', 'IELTS_MAP', 'IELTS_SHORT'))");
            st.execute("ALTER TABLE question_groups ADD COLUMN layout json");
        }
    }

    /** Tên các CHECK của questions có điều kiện nhắc tới GRAMMAR_BUILD (tức CHECK type). */
    private static List<String> typeChecks(Connection conn) throws Exception {
        List<String> names = new ArrayList<>();
        String sql = "SELECT tc.constraint_name FROM information_schema.table_constraints tc "
                + "JOIN information_schema.check_constraints cc ON cc.constraint_name = tc.constraint_name "
                + "AND cc.constraint_schema = tc.constraint_schema "
                + "WHERE LOWER(tc.table_name) = 'questions' AND LOWER(tc.table_schema) = LOWER(?) "
                + "AND tc.constraint_type = 'CHECK' AND LOWER(cc.check_clause) LIKE '%grammar_build%'";
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
