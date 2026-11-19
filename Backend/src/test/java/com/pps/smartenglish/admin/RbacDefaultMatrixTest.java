package com.pps.smartenglish.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.support.ApiTestBase;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** D-37, FR-ADM-04: ma trận quyền mặc định (V1) và việc bỏ role STAFF. */
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
        assertEquals(Set.of("report.view", "order.view", "report.teaching"), permissionsOf("ACCOUNTANT"));
        assertEquals(Set.of("student.view", "order.view", "activation.manage", "student.support"), permissionsOf("SUPPORT"));
        assertEquals(Set.of("cms.content", "regrade.manage"), permissionsOf("EDITOR"));
        assertEquals(Set.of("regrade.manage", "student.view", "class.teach"), permissionsOf("TEACHER"));
        // D-99, D-109: trợ giảng và quản lý đào tạo
        assertEquals(Set.of("class.teach", "student.view"), permissionsOf("ASSISTANT"));
        assertEquals(Set.of("class.manage", "report.teaching", "student.view"), permissionsOf("TRAINING_MANAGER"));
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
}
