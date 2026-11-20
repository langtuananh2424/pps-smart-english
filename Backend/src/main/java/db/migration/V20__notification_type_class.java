package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** FR-CLS-03: thêm loại thông báo CLASS_ASSIGNED, CLASS_TRANSFERRED. Cùng cách làm với V17 (chạy được trên H2 và PostgreSQL). */
public class V20__notification_type_class extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();
        List<String> old = new ArrayList<>();
        String sql = "SELECT tc.constraint_name FROM information_schema.table_constraints tc "
                + "JOIN information_schema.check_constraints cc ON cc.constraint_name = tc.constraint_name "
                + "AND cc.constraint_schema = tc.constraint_schema "
                + "WHERE LOWER(tc.table_name) = 'notifications' AND LOWER(tc.table_schema) = LOWER(?) "
                + "AND tc.constraint_type = 'CHECK' AND LOWER(cc.check_clause) LIKE '%ticket_reply%'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, conn.getSchema());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    old.add(rs.getString(1));
                }
            }
        }
        try (Statement st = conn.createStatement()) {
            for (String name : old) {
                st.execute("ALTER TABLE notifications DROP CONSTRAINT \"" + name + "\"");
            }
            st.execute("ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN "
                    + "('STREAK_REMINDER', 'WRITING_GRADED', 'TICKET_REPLY', 'CLASS_ASSIGNED', 'CLASS_TRANSFERRED'))");
        }
    }
}
