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
 * D-156: video làm đề (mp4 tải lên hoặc link YouTube) gắn ở nhóm câu, tối đa một trong hai.
 * CHECK của media_assets.kind ở V26 không đặt tên (H2 và PostgreSQL tự đặt tên khác nhau) và hẹp hơn giá trị VIDEO, nên tra tên
 * thật rồi bỏ, tạo lại có tên (cùng cách V28). Chỉ nới CHECK và thêm cột null được nên bản chạy cũ vẫn tương thích.
 */
public class V46__group_video extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();
        List<String> old = kindChecks(conn);
        try (Statement st = conn.createStatement()) {
            for (String name : old) {
                st.execute("ALTER TABLE media_assets DROP CONSTRAINT \"" + name + "\"");
            }
            st.execute("ALTER TABLE media_assets ADD CONSTRAINT ck_media_assets_kind CHECK (kind IN ('IMAGE', 'AUDIO', 'VIDEO'))");
            st.execute("ALTER TABLE question_groups ADD COLUMN video_asset_id bigint REFERENCES media_assets (id)");
            st.execute("ALTER TABLE question_groups ADD COLUMN video_youtube_id varchar(11)");
            st.execute("ALTER TABLE question_groups ADD CONSTRAINT ck_question_groups_one_video "
                    + "CHECK (video_asset_id IS NULL OR video_youtube_id IS NULL)");
        }
    }

    /** Tên các CHECK của media_assets nhắc tới audio (tức CHECK kind cũ). */
    private static List<String> kindChecks(Connection conn) throws Exception {
        List<String> names = new ArrayList<>();
        String sql = "SELECT tc.constraint_name FROM information_schema.table_constraints tc "
                + "JOIN information_schema.check_constraints cc ON cc.constraint_name = tc.constraint_name "
                + "AND cc.constraint_schema = tc.constraint_schema "
                + "WHERE LOWER(tc.table_name) = 'media_assets' AND LOWER(tc.table_schema) = LOWER(?) "
                + "AND tc.constraint_type = 'CHECK' AND LOWER(cc.check_clause) LIKE '%audio%'";
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
