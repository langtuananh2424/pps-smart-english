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
 * D-159 (FR-EX-04): kiểu câu VIDEO_CONNECTIVE và bảng tiến độ xem video + quiz theo (lượt, nhóm).
 * Nới CHECK của questions.type cùng cách V47 (tra theo nội dung, bỏ rồi tạo lại có tên); danh sách giữ nguyên các kiểu của V47 và
 * LISTEN_RECORD (V48). Chỉ nới CHECK và thêm bảng mới nên bản chạy cũ vẫn tương thích.
 */
public class V49__video_connective extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();
        List<String> old = typeChecks(conn);
        try (Statement st = conn.createStatement()) {
            for (String name : old) {
                st.execute("ALTER TABLE questions DROP CONSTRAINT \"" + name + "\"");
            }
            st.execute("ALTER TABLE questions ADD CONSTRAINT ck_questions_type_v4 CHECK (type IN ("
                    + "'CONNECTIVE_MCQ', 'CONNECTIVE_MATCH', 'CONNECTIVE_CLOZE', 'CONNECTIVE_REWRITE', "
                    + "'GRAMMAR_IDENTIFY', 'GRAMMAR_SPOT_FIX', 'GRAMMAR_BUILD', 'GRAMMAR_REWRITE', 'WRITING', 'SPEAKING', "
                    + "'CHOICE_MULTI', 'TRUE_FALSE', 'FILL_TEXT', 'WORD_BANK', 'MATCH_OPTIONS', "
                    + "'LETTER_ORDER', 'DICTATION', 'LISTEN_FORM', 'LISTEN_RECORD', "
                    + "'IELTS_TFNG', 'IELTS_YNNG', 'IELTS_MATCH_INFO', 'IELTS_MATCH_HEADINGS', 'IELTS_MATCH_FEATURES', "
                    + "'IELTS_MATCH_ENDINGS', 'IELTS_MATCHING', 'IELTS_SENTENCE', 'IELTS_SUMMARY', 'IELTS_SUMMARY_BANK', "
                    + "'IELTS_NOTE', 'IELTS_TABLE', 'IELTS_FLOWCHART', 'IELTS_FORM', 'IELTS_DIAGRAM', 'IELTS_MAP', "
                    + "'IELTS_SHORT', 'VIDEO_CONNECTIVE'))");
            // D-159: thời lượng video YouTube do CMS nhập (giây); video file lấy từ media_assets.duration_ms
            st.execute("ALTER TABLE question_groups ADD COLUMN video_duration_sec integer");
            st.execute("CREATE TABLE attempt_video_groups ("
                    + "id bigserial PRIMARY KEY, "
                    + "attempt_id bigint NOT NULL REFERENCES student_exercise_attempts (id) ON DELETE CASCADE, "
                    + "group_id bigint NOT NULL REFERENCES question_groups (id), "
                    + "duration_sec double precision, "
                    + "furthest_sec double precision NOT NULL DEFAULT 0, "
                    + "last_position_sec double precision NOT NULL DEFAULT 0, "
                    + "first_report_at timestamp with time zone, "
                    + "last_report_at timestamp with time zone, "
                    + "submits integer NOT NULL DEFAULT 0, "
                    + "finalized boolean NOT NULL DEFAULT false, "
                    + "passed boolean NOT NULL DEFAULT false, "
                    + "answers json, "
                    + "wrong_ids json, "
                    + "CONSTRAINT uq_attempt_video_groups UNIQUE (attempt_id, group_id))");
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
