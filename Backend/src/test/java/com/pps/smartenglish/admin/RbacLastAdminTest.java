package com.pps.smartenglish.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.support.ApiTestBase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * FR-ADM-04: luôn còn ít nhất một người có rbac.manage. DB dùng chung giữa các test nên mỗi test khóa mọi tài khoản
 * ACTIVE đã có để chỉ còn những quản trị viên do test tạo, rồi mở lại sau đó.
 */
class RbacLastAdminTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";
    private List<Long> locked = List.of();

    private Account withRole(String role) throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = ?", u.id(), role);
        return login(u.email(), PASSWORD);
    }

    private ResultActions call(MockHttpServletRequestBuilder req, Account a, Object body) throws Exception {
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return send(req, a.accessToken());
    }

    /** Khóa mọi tài khoản ACTIVE hiện có trừ các tài khoản test giữ lại. */
    private void isolate(Account... keep) {
        var keepIds = java.util.Arrays.stream(keep).map(Account::id).toList();
        locked = jdbc.queryForList("select id from users where status = 'ACTIVE'", Long.class).stream()
                .filter(id -> !keepIds.contains(id)).toList();
        locked.forEach(id -> jdbc.update("update users set status = 'LOCKED' where id = ?", id));
    }

    @BeforeEach
    void noneLocked() {
        locked = List.of();
    }

    @AfterEach
    void unlock() {
        locked.forEach(id -> jdbc.update("update users set status = 'ACTIVE' where id = ?", id));
    }

    private static String roleUrl(long user, String code) {
        return "/api/admin/users/" + user + "/roles/" + code;
    }

    private static String permUrl(long user, String code) {
        return "/api/admin/users/" + user + "/permissions/" + code;
    }

    @Test
    void lastAdminRoleCannotBeRemovedButOneOfTwoCan() throws Exception {
        var a = withRole("ADMIN");
        var b = withRole("ADMIN");
        isolate(a, b);
        call(delete(roleUrl(a.id(), "ADMIN")), b, null).andExpect(status().isOk());
        call(delete(roleUrl(b.id(), "ADMIN")), b, null).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("last_admin"));
        call(get("/api/admin/users/" + b.id() + "/access"), b, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").value(org.hamcrest.Matchers.hasItem("ADMIN"))); // đã rollback
    }

    @Test
    void lastRevokeOfRbacManageIsRejected() throws Exception {
        var a = withRole("ADMIN");
        var b = withRole("ADMIN");
        isolate(a, b);
        call(put(permUrl(a.id(), "rbac.manage")), b, Map.of("effect", "REVOKE")).andExpect(status().isOk());
        call(put(permUrl(b.id(), "rbac.manage")), b, Map.of("effect", "REVOKE")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("last_admin"));
    }

    @Test
    void lastHolderCannotLoseGrantOverride() throws Exception {
        var admin = withRole("ADMIN");
        var s = register(newEmail(), PASSWORD);
        isolate(admin, s);
        call(put(permUrl(s.id(), "rbac.manage")), admin, Map.of("effect", "GRANT")).andExpect(status().isOk());
        call(delete(roleUrl(admin.id(), "ADMIN")), admin, null).andExpect(status().isOk()); // s vẫn giữ quyền
        call(delete(permUrl(s.id(), "rbac.manage")), s, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("last_admin"));
    }

    @Test
    void roleCannotBeStrippedOfRbacManageWhenItIsTheLastSource() throws Exception {
        var a = withRole("ADMIN");
        isolate(a);
        var roles = json.readTree(call(get("/api/admin/roles"), a, null).andReturn().getResponse().getContentAsString());
        long adminRole = 0;
        List<String> without = new java.util.ArrayList<>();
        for (var r : roles) {
            if ("ADMIN".equals(r.get("code").asText())) {
                adminRole = r.get("id").asLong();
                r.get("permissions").forEach(p -> {
                    if (!"rbac.manage".equals(p.asText())) {
                        without.add(p.asText());
                    }
                });
            }
        }
        call(put("/api/admin/roles/" + adminRole + "/permissions"), a, Map.of("permissions", without))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("last_admin"));
        assertEquals(1, count("select count(*) from role_permissions rp join permissions p on p.id = rp.permission_id "
                + "where rp.role_id = ? and p.code = 'rbac.manage'", adminRole));
    }
}
