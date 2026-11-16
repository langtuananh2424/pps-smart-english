package com.pps.smartenglish.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.support.ApiTestBase;
import java.sql.DriverManager;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/** D-37, FR-ADM-04: ma trận quyền mặc định (V27) và việc bỏ role STAFF. */
class RbacDefaultMatrixTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";

    private Set<String> permissionsOf(String role) {
        return new TreeSet<>(jdbc.queryForList("select p.code from role_permissions rp join roles r on r.id = rp.role_id "
                + "join permissions p on p.id = rp.permission_id where r.code = ?", String.class, role));
    }

    private Account withRole(String role) throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = ?", u.id(), role);
        return login(u.email(), PASSWORD);
    }

    private int code(String role, String url) throws Exception {
        var acc = role.equals("STUDENT") ? register(newEmail(), PASSWORD) : withRole(role);
        return send(get(url), acc.accessToken()).andReturn().getResponse().getStatus();
    }

    @Test
    void defaultPermissionsMatchD37() {
        assertEquals(new TreeSet<>(jdbc.queryForList("select code from permissions", String.class)), permissionsOf("ADMIN"));
        assertEquals(Set.of("report.view", "audit.view", "order.view"), permissionsOf("DIRECTOR"));
        assertEquals(Set.of("report.view", "order.view"), permissionsOf("ACCOUNTANT"));
        assertEquals(Set.of("student.view", "order.view", "activation.manage"), permissionsOf("SUPPORT"));
        assertEquals(Set.of("cms.content", "regrade.manage"), permissionsOf("EDITOR"));
        assertEquals(Set.of("regrade.manage", "student.view"), permissionsOf("TEACHER"));
        assertEquals(Set.of(), permissionsOf("STUDENT"));
        for (String p : List.of("product.manage", "order.view", "student.view")) {
            assertEquals(1, count("select count(*) from role_permissions rp join roles r on r.id = rp.role_id "
                    + "join permissions x on x.id = rp.permission_id where r.code = 'ADMIN' and x.code = ?", p));
        }
        assertEquals(0, count("select count(*) from roles where code = 'STAFF'"));
    }

    @Test
    void accountantSeesOrdersButNotCms() throws Exception {
        assertEquals(200, code("ACCOUNTANT", "/api/admin/orders"));
        assertEquals(403, code("ACCOUNTANT", "/api/admin/cms/courses"));
    }

    @Test
    void supportSeesOrdersButNotReports() throws Exception {
        assertEquals(200, code("SUPPORT", "/api/admin/orders"));
        assertEquals(403, code("SUPPORT", "/api/admin/reports/dashboard"));
    }

    @Test
    void directorSeesOrdersAndAuditButEditorAndTeacherDoNot() throws Exception {
        assertEquals(200, code("DIRECTOR", "/api/admin/orders"));
        assertEquals(200, code("DIRECTOR", "/api/admin/audit-logs"));
        assertEquals(403, code("EDITOR", "/api/admin/orders"));
        assertEquals(403, code("TEACHER", "/api/admin/orders"));
        assertEquals(403, code("STUDENT", "/api/admin/orders"));
    }

    /** V27 trên dữ liệu thật: người giữ STAFF được chuyển sang SUPPORT, người đã có cả hai không bị trùng, STAFF biến mất. */
    @Test
    void staffUsersMoveToSupportWithoutDuplicates() throws Exception {
        String url = "jdbc:h2:mem:v27check;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").target("26").load().migrate();
        try (var c = DriverManager.getConnection(url, "sa", ""); var st = c.createStatement()) {
            st.execute("insert into users (email, status) values ('a@x.com', 'ACTIVE'), ('b@x.com', 'ACTIVE')");
            for (String email : List.of("a@x.com", "b@x.com")) {
                st.execute("insert into user_roles (user_id, role_id) select u.id, r.id from users u, roles r "
                        + "where u.email = '" + email + "' and r.code = 'STAFF'");
            }
            st.execute("insert into user_roles (user_id, role_id) select u.id, r.id from users u, roles r "
                    + "where u.email = 'b@x.com' and r.code = 'SUPPORT'");

            Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").load().migrate();

            try (var rs = st.executeQuery("select u.email, count(*) from user_roles ur join users u on u.id = ur.user_id "
                    + "join roles r on r.id = ur.role_id where r.code = 'SUPPORT' and u.email in ('a@x.com', 'b@x.com') "
                    + "group by u.email order by u.email")) {
                rs.next();
                assertEquals(1, rs.getInt(2));
                rs.next();
                assertEquals(1, rs.getInt(2));
            }
            try (var rs = st.executeQuery("select count(*) from roles where code = 'STAFF'")) {
                rs.next();
                assertEquals(0, rs.getInt(1));
            }
        }
    }
}
