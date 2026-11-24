package com.pps.smartenglish.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.support.ApiTestBase;
import com.pps.smartenglish.support.MutableClockConfig;
import com.pps.smartenglish.support.MutableClockConfig.MutableClock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * FR-ADM-04, D-24: role và quyền dạng bảng; GRANT/REVOKE có hạn; quyền có hiệu lực ở lần gọi kế tiếp, không cần đăng nhập lại.
 */
@Import(MutableClockConfig.class)
class RbacTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";
    private static final String AUDIT = "/api/admin/audit-logs";
    private static final String CMS_COURSES = "/api/admin/cms/courses";

    @Autowired MutableClock clock;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private Account withRole(String role) throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = ?", u.id(), role);
        return login(u.email(), PASSWORD);
    }

    private ResultActions call(MockHttpServletRequestBuilder req, Account a, Object body) throws Exception {
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return a == null ? mvc.perform(req) : send(req, a.accessToken());
    }

    /** Mã role hợp lệ (chữ hoa, gạch dưới) và không trùng giữa các lần chạy. */
    private static String roleCode() {
        return "R" + UUID.randomUUID().toString().replaceAll("[^A-Za-z]", "").toUpperCase();
    }

    @Test
    void rolesScreenNeedsRbacPermission() throws Exception {
        call(get("/api/admin/roles"), null, null).andExpect(status().isUnauthorized());
        call(get("/api/admin/roles"), register(newEmail(), PASSWORD), null).andExpect(status().isForbidden());
        var admin = withRole("ADMIN");
        var body = json.readTree(call(get("/api/admin/roles"), admin, null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertTrue(hasRole(body, "STUDENT") && hasRole(body, "EDITOR") && hasRole(body, "ADMIN"));
        call(get("/api/admin/permissions"), admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code").value(org.hamcrest.Matchers.hasItem("cms.content")));
    }

    @Test
    void newRoleGrantsAndRevokesAccessWithoutReloginAndIsAudited() throws Exception {
        var admin = withRole("ADMIN");
        var student = register(newEmail(), PASSWORD);
        String code = roleCode();
        JsonNode created = json.readTree(call(MockMvcRequestBuilders.post("/api/admin/roles"), admin,
                Map.of("code", code, "name", "Trợ lý kiểm nhật ký", "permissions", List.of("audit.view")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long roleId = created.get("id").asLong();

        call(get(AUDIT), student, null).andExpect(status().isForbidden());
        call(MockMvcRequestBuilders.post("/api/admin/users/" + student.id() + "/roles"), admin, Map.of("roleCode", code))
                .andExpect(status().isOk());
        call(get(AUDIT), student, null).andExpect(status().isOk()); // cùng token, không đăng nhập lại
        call(delete("/api/admin/users/" + student.id() + "/roles/" + code), admin, null).andExpect(status().isOk());
        call(get(AUDIT), student, null).andExpect(status().isForbidden());

        var entries = json.readTree(call(get(AUDIT).param("targetType", "ROLE").param("targetId", String.valueOf(roleId)),
                admin, null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
        assertEquals("CREATE", entries.get(0).get("action").asText());
        assertEquals(admin.id(), entries.get(0).get("actorId").asLong());
    }

    @Test
    void invalidCodesAndUnknownPermissionsAreRejected() throws Exception {
        var admin = withRole("ADMIN");
        call(MockMvcRequestBuilders.post("/api/admin/roles"), admin, Map.of("code", "abc", "name", "x", "permissions", List.of()))
                .andExpect(status().isBadRequest());
        call(MockMvcRequestBuilders.post("/api/admin/roles"), admin,
                Map.of("code", roleCode(), "name", "x", "permissions", List.of("no.such")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("validation_failed"));
        call(MockMvcRequestBuilders.post("/api/admin/users/" + admin.id() + "/roles"), admin, Map.of("roleCode", "NOPE_ROLE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void grantWithExpiryEndsAtTheDeadline() throws Exception {
        var admin = withRole("ADMIN");
        var student = register(newEmail(), PASSWORD);
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        clock.set(now);
        call(put("/api/admin/users/" + student.id() + "/permissions/audit.view"), admin,
                Map.of("effect", "GRANT", "expiresAt", now.plusSeconds(3600).toString())).andExpect(status().isOk());
        call(get(AUDIT), student, null).andExpect(status().isOk());

        clock.set(now.plusSeconds(7200));
        call(get(AUDIT), student, null).andExpect(status().isForbidden());
    }

    @Test
    void overrideInThePastIsRejected() throws Exception {
        var admin = withRole("ADMIN");
        var student = register(newEmail(), PASSWORD);
        call(put("/api/admin/users/" + student.id() + "/permissions/audit.view"), admin,
                Map.of("effect", "GRANT", "expiresAt", Instant.now().minusSeconds(60).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void revokeOverrideTakesAwayAdminPermissionFromOneUser() throws Exception {
        var admin = withRole("ADMIN");
        var second = withRole("ADMIN");
        call(put("/api/admin/users/" + second.id() + "/permissions/cms.content"), admin, Map.of("effect", "REVOKE"))
                .andExpect(status().isOk());
        call(get(CMS_COURSES), second, null).andExpect(status().isForbidden());
        call(get(CMS_COURSES), admin, null).andExpect(status().isOk()); // người khác không bị ảnh hưởng

        call(delete("/api/admin/users/" + second.id() + "/permissions/cms.content"), admin, null).andExpect(status().isOk());
        call(get(CMS_COURSES), second, null).andExpect(status().isOk());
    }

    @Test
    void accessScreenShowsRolesOverridesAndEffectivePermissions() throws Exception {
        var admin = withRole("ADMIN");
        var student = register(newEmail(), PASSWORD);
        call(put("/api/admin/users/" + student.id() + "/permissions/audit.view"), admin, Map.of("effect", "GRANT"))
                .andExpect(status().isOk());
        var body = json.readTree(call(get("/api/admin/users/" + student.id() + "/access"), admin, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(List.of("STUDENT"), json.convertValue(body.get("roles"), List.class));
        assertTrue(body.get("effective").toString().contains("audit.view"));
        assertEquals("GRANT", body.get("overrides").get(0).get("effect").asText());
    }

    @Test
    void myPermissionsReturnsEffectiveSetAndNeedsLogin() throws Exception {
        call(get("/api/me/permissions"), null, null).andExpect(status().isUnauthorized());
        var student = register(newEmail(), PASSWORD);
        call(get("/api/me/permissions"), student, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions").isArray()).andExpect(jsonPath("$.permissions").isEmpty());
        var admin = withRole("ADMIN");
        call(get("/api/me/permissions"), admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions").value(org.hamcrest.Matchers.hasItems("rbac.manage", "audit.view")));
    }

    @Test
    void staffListFiltersPagesAndHidesPureStudents() throws Exception {
        var admin = withRole("ADMIN");
        call(get("/api/admin/users"), null, null).andExpect(status().isUnauthorized());
        call(get("/api/admin/users"), withRole("EDITOR"), null).andExpect(status().isForbidden());

        String tag = "zq" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        var editor = register(newEmail(), PASSWORD);
        var support = register(newEmail(), PASSWORD);
        var student = register(newEmail(), PASSWORD);
        for (var u : List.of(editor, support, student)) {
            jdbc.update("update user_profiles set display_name = ? where user_id = ?", "Tên " + tag + u.id(), u.id());
        }
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = 'EDITOR'", editor.id());
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = 'SUPPORT'", support.id());

        JsonNode all = json.readTree(call(get("/api/admin/users").param("q", tag.toUpperCase()), admin, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(2, all.get("data").size()); // học viên thuần không có
        assertEquals(support.id(), all.get("data").get(0).get("id").asLong()); // id giảm dần
        assertEquals("Tên " + tag + support.id(), all.get("data").get(0).get("displayName").asText());
        assertTrue(all.get("data").get(0).get("roles").toString().contains("SUPPORT"));

        call(get("/api/admin/users").param("q", tag).param("role", "EDITOR"), admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].id").value(editor.id()));
        call(get("/api/admin/users").param("q", tag + "%"), admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0)); // % được escape, không khớp mọi dòng

        JsonNode first = json.readTree(call(get("/api/admin/users").param("q", tag).param("limit", "1"), admin, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(1, first.get("data").size());
        JsonNode second = json.readTree(call(get("/api/admin/users").param("q", tag).param("limit", "1")
                .param("cursor", first.get("nextCursor").asText()), admin, null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals(editor.id(), second.get("data").get(0).get("id").asLong());
        assertTrue(second.get("nextCursor").isNull());
    }

    @Test
    void roleListCountsMembers() throws Exception {
        var admin = withRole("ADMIN");
        String code = roleCode();
        long roleId = json.readTree(call(MockMvcRequestBuilders.post("/api/admin/roles"), admin,
                Map.of("code", code, "name", "Đếm", "permissions", List.of())).andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberCount").value(0)).andReturn().getResponse().getContentAsString()).get("id").asLong();
        for (int i = 0; i < 2; i++) {
            var u = register(newEmail(), PASSWORD);
            call(MockMvcRequestBuilders.post("/api/admin/users/" + u.id() + "/roles"), admin, Map.of("roleCode", code))
                    .andExpect(status().isOk());
        }
        var body = json.readTree(call(get("/api/admin/roles"), admin, null).andReturn().getResponse().getContentAsString());
        for (JsonNode r : body) {
            if (r.get("id").asLong() == roleId) {
                assertEquals(2, r.get("memberCount").asInt());
            }
        }
    }

    private static boolean hasRole(JsonNode roles, String code) {
        for (JsonNode r : roles) {
            if (code.equals(r.get("code").asText())) {
                return true;
            }
        }
        return false;
    }
}
