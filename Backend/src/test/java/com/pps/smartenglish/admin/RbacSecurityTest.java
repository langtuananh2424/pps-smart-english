package com.pps.smartenglish.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
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

/** Tester #56 (FR-ADM-04/05): phân quyền, đầu vào độc và last_admin của API quản trị nhân sự / nhật ký. */
@Import(MutableClockConfig.class)
class RbacSecurityTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";
    private static final String USERS = "/api/admin/users";
    private static final String AUDIT = "/api/admin/audit-logs";

    @Autowired MutableClock clock;
    private List<Long> locked = List.of();

    @AfterEach
    void restore() {
        clock.reset();
        locked.forEach(id -> jdbc.update("update users set status = 'ACTIVE' where id = ?", id));
        locked = List.of();
    }

    private Account withRole(String role) throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = ?", u.id(), role);
        return login(u.email(), PASSWORD);
    }

    /** Tài khoản chỉ có đúng các quyền cho trước (qua role riêng). */
    private Account withPermissions(String... perms) throws Exception {
        String code = "T" + UUID.randomUUID().toString().replaceAll("[^A-Za-z]", "").toUpperCase();
        jdbc.update("insert into roles (code, name) values (?, ?)", code, code);
        for (String p : perms) {
            jdbc.update("insert into role_permissions (role_id, permission_id) select r.id, p.id from roles r, permissions p "
                    + "where r.code = ? and p.code = ?", code, p);
        }
        return withRole(code);
    }

    private ResultActions call(MockHttpServletRequestBuilder req, Account a, Object body) throws Exception {
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return a == null ? mvc.perform(req) : send(req, a.accessToken());
    }

    private JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private void isolate(Account... keep) {
        var keepIds = java.util.Arrays.stream(keep).map(Account::id).toList();
        locked = jdbc.queryForList("select id from users where status = 'ACTIVE'", Long.class).stream()
                .filter(id -> !keepIds.contains(id)).toList();
        locked.forEach(id -> jdbc.update("update users set status = 'LOCKED' where id = ?", id));
    }

    private static String cursorOf(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    // ---- Phân quyền theo chức năng ----

    private List<MockHttpServletRequestBuilder> rbacEndpoints(long target) {
        return List.of(
                get("/api/admin/permissions"), get("/api/admin/roles"), get(USERS), get(USERS + "/" + target + "/access"),
                MockMvcRequestBuilders.post("/api/admin/roles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"HACK_ROLE\",\"name\":\"x\",\"permissions\":[\"rbac.manage\"]}"),
                put("/api/admin/roles/1/permissions").contentType(MediaType.APPLICATION_JSON).content("{\"permissions\":[]}"),
                MockMvcRequestBuilders.post(USERS + "/" + target + "/roles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCode\":\"ADMIN\"}"),
                delete(USERS + "/" + target + "/roles/ADMIN"),
                put(USERS + "/" + target + "/permissions/rbac.manage").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effect\":\"GRANT\"}"),
                delete(USERS + "/" + target + "/permissions/rbac.manage"));
    }

    @Test
    void everyRbacEndpointRejectsGuestsAndUsersWithoutRbacManage() throws Exception {
        var victim = register(newEmail(), PASSWORD);
        var student = register(newEmail(), PASSWORD);
        var auditor = withPermissions("audit.view");
        var editor = withRole("EDITOR");
        for (var req : rbacEndpoints(victim.id())) {
            mvc.perform(req).andExpect(status().isUnauthorized());
        }
        for (var who : List.of(student, auditor, editor)) {
            for (var req : rbacEndpoints(who.id())) { // kể cả tự cấp quyền cho chính mình
                send(req, who.accessToken()).andExpect(status().isForbidden());
            }
        }
        assertEquals(0, count("select count(*) from roles where code = 'HACK_ROLE'"));
        assertEquals(0, count("select count(*) from user_roles ur join roles r on r.id = ur.role_id where r.code = 'ADMIN' and ur.user_id in (?, ?, ?)",
                student.id(), auditor.id(), editor.id()));
    }

    @Test
    void auditLogNeedsAuditViewNotRbacManage() throws Exception {
        call(get(AUDIT), null, null).andExpect(status().isUnauthorized());
        call(get(AUDIT), withPermissions("rbac.manage"), null).andExpect(status().isForbidden());
        call(get(AUDIT), withPermissions("audit.view"), null).andExpect(status().isOk());
        // lách khớp đường dẫn
        call(get(AUDIT + "/"), withPermissions("rbac.manage"), null).andExpect(status().is4xxClientError());
        call(get("/api/admin/users/../audit-logs"), withPermissions("rbac.manage"), null).andExpect(status().is4xxClientError());
    }

    @Test
    void auditOnlyUserCannotListStaffButAdminWithOnlyRbacCan() throws Exception {
        call(get(USERS), withPermissions("audit.view"), null).andExpect(status().isForbidden());
        call(get(USERS), withPermissions("rbac.manage"), null).andExpect(status().isOk());
    }

    // ---- /me/permissions ----

    @Test
    void myPermissionsIsAlwaysTheCallersOwnAndFollowsOverridesAndExpiry() throws Exception {
        var admin = withRole("ADMIN");
        var a = register(newEmail(), PASSWORD);
        var b = register(newEmail(), PASSWORD);
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        clock.set(now);
        call(put(USERS + "/" + a.id() + "/permissions/audit.view"), admin,
                Map.of("effect", "GRANT", "expiresAt", now.plusSeconds(60).toString())).andExpect(status().isOk());

        call(get("/api/me/permissions").param("userId", String.valueOf(admin.id())), b, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions").isEmpty()); // tham số lạ bị bỏ qua
        call(get("/api/me/permissions"), a, null).andExpect(status().isOk()).andExpect(jsonPath("$.permissions[0]").value("audit.view"))
                .andExpect(jsonPath("$.permissions.length()").value(1));
        assertEquals(1, body(call(get("/api/me/permissions"), a, null)).size()); // chỉ một trường

        clock.set(now.plusSeconds(120));
        call(get("/api/me/permissions"), a, null).andExpect(jsonPath("$.permissions").isEmpty());

        var second = withRole("ADMIN");
        call(put(USERS + "/" + second.id() + "/permissions/rbac.manage"), admin, Map.of("effect", "REVOKE")).andExpect(status().isOk());
        var perms = body(call(get("/api/me/permissions"), second, null)).get("permissions").toString();
        assertFalse(perms.contains("rbac.manage"));
        assertTrue(perms.contains("audit.view"));
    }

    @Test
    void rolePermissionChangeTakesEffectOnTheNextRequestWithSameToken() throws Exception {
        var admin = withRole("ADMIN");
        String code = "R" + UUID.randomUUID().toString().replaceAll("[^A-Za-z]", "").toUpperCase();
        long roleId = body(call(MockMvcRequestBuilders.post("/api/admin/roles"), admin,
                Map.of("code", code, "name", "Kiểm", "permissions", List.of("audit.view"))).andExpect(status().isCreated())).get("id").asLong();
        var member = register(newEmail(), PASSWORD);
        call(MockMvcRequestBuilders.post(USERS + "/" + member.id() + "/roles"), admin, Map.of("roleCode", code)).andExpect(status().isOk());
        call(get(AUDIT), member, null).andExpect(status().isOk());

        call(put("/api/admin/roles/" + roleId + "/permissions"), admin, Map.of("permissions", List.of())).andExpect(status().isOk());
        call(get(AUDIT), member, null).andExpect(status().isForbidden());
        call(get("/api/me/permissions"), member, null).andExpect(jsonPath("$.permissions").isEmpty());
    }

    // ---- /admin/users: lộ dữ liệu, injection, phân trang ----

    @Test
    void staffListNeverShowsPureStudentsOrSensitiveFields() throws Exception {
        var admin = withRole("ADMIN");
        String tag = "st" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        var student = register(tag + "@example.com", PASSWORD);
        var granted = register(newEmail(), PASSWORD); // học viên chỉ có override GRANT, không có role nhân sự
        jdbc.update("update user_profiles set display_name = ? where user_id = ?", tag, granted.id());
        call(put(USERS + "/" + granted.id() + "/permissions/audit.view"), admin, Map.of("effect", "GRANT")).andExpect(status().isOk());

        call(get(USERS).param("q", tag), admin, null).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        call(get(USERS).param("q", tag).param("role", "STUDENT"), admin, null).andExpect(jsonPath("$.data.length()").value(0));
        call(get(USERS).param("q", student.email()), admin, null).andExpect(jsonPath("$.data.length()").value(0));

        JsonNode first = body(call(get(USERS).param("limit", "5"), admin, null).andExpect(status().isOk())).get("data").get(0);
        var fields = new java.util.TreeSet<String>();
        first.fieldNames().forEachRemaining(fields::add);
        assertEquals(new java.util.TreeSet<>(List.of("id", "email", "displayName", "roles")), fields);
    }

    @Test
    void likeWildcardsAndSqlMetacharactersInQAreLiteral() throws Exception {
        var admin = withRole("ADMIN");
        String tag = "lk" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        var editor = register(newEmail(), PASSWORD);
        jdbc.update("update user_profiles set display_name = ? where user_id = ?", tag + "x9", editor.id());
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = 'EDITOR'", editor.id());

        call(get(USERS).param("q", tag + "x"), admin, null).andExpect(jsonPath("$.data.length()").value(1));
        for (String q : List.of(tag + "_9", tag + "%9", tag + "!x", tag + "\\x", tag + "[x]")) {
            call(get(USERS).param("q", q), admin, null).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        }
        for (String q : List.of("' OR 1=1 --", "'; drop table users; --", "\" or \"\"=\"", "%' or '1'='1")) {
            call(get(USERS).param("q", q), admin, null).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
            call(get(USERS).param("role", q), admin, null).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        }
        assertTrue(count("select count(*) from users") > 0);
    }

    @Test
    void literalPercentAndUnderscoreInANameAreFound() throws Exception {
        var admin = withRole("ADMIN");
        String tag = "pc" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        var editor = register(newEmail(), PASSWORD);
        jdbc.update("update user_profiles set display_name = ? where user_id = ?", tag + "_50%!", editor.id());
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = 'EDITOR'", editor.id());
        try {
            call(get(USERS).param("q", tag + "_50%!"), admin, null).andExpect(jsonPath("$.data.length()").value(1));
            call(get(USERS).param("q", "  " + tag.toUpperCase() + "  "), admin, null).andExpect(jsonPath("$.data.length()").value(1));
        } finally { // RbacTest giả định không nhân sự nào có '%' trong tên (DB dùng chung)
            jdbc.update("update user_profiles set display_name = ? where user_id = ?", tag, editor.id());
        }
    }

    @Test
    void forgedOrInvalidCursorIsRejectedWithoutLeakingInternals() throws Exception {
        var admin = withRole("ADMIN");
        for (String c : List.of("!!!", "%%%", cursorOf("abc"), cursorOf("99999999999999999999"), cursorOf("1 or 1=1"), "a".repeat(5000))) {
            var res = call(get(USERS).param("cursor", c), admin, null).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("validation_failed")).andReturn().getResponse().getContentAsString();
            assertFalse(res.contains("Exception") || res.contains("at com."), res);
            call(get(AUDIT).param("cursor", c), admin, null).andExpect(status().isBadRequest());
        }
        // Cursor "giả" nhưng đúng định dạng chỉ dịch vị trí đọc, không mở rộng tập dữ liệu
        call(get(USERS).param("cursor", cursorOf("-5")), admin, null).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        call(get(USERS).param("cursor", cursorOf(String.valueOf(Long.MAX_VALUE))), admin, null).andExpect(status().isOk());
    }

    @Test
    void limitIsValidatedAndCapped() throws Exception {
        var admin = withRole("ADMIN");
        for (String l : List.of("-1", "0", "abc", "1.5", "99999999999", "")) {
            var r = call(get(USERS).param("limit", l), admin, null);
            if (l.isEmpty()) {
                r.andExpect(status().isOk());
            } else {
                r.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("validation_failed"));
            }
        }
        for (int i = 0; i < 101; i++) { // đủ hơn 100 nhân sự để thấy trần
            var u = register(newEmail(), PASSWORD);
            jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = 'EDITOR'", u.id());
        }
        JsonNode page = body(call(get(USERS).param("limit", "1000000"), admin, null).andExpect(status().isOk()));
        assertEquals(100, page.get("data").size());
        assertFalse(page.get("nextCursor").isNull());
        call(get(AUDIT).param("limit", "1000000"), admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.lessThanOrEqualTo(100)));
    }

    // ---- Header lý do ----

    private ResultActions createRole(Account admin, String reason) throws Exception {
        String code = "H" + UUID.randomUUID().toString().replaceAll("[^A-Za-z]", "").toUpperCase();
        var req = MockMvcRequestBuilders.post("/api/admin/roles").header("X-Audit-Reason", reason);
        return call(req, admin, Map.of("code", code, "name", "x", "permissions", List.of()));
    }

    private JsonNode lastRoleLog(Account admin, long roleId) throws Exception {
        return body(call(get(AUDIT).param("targetType", "ROLE").param("targetId", String.valueOf(roleId)), admin, null)).get("data").get(0);
    }

    @Test
    void veryLongRawReasonIsRejectedAndNothingIsWritten() throws Exception {
        var admin = withRole("ADMIN");
        int before = count("select count(*) from roles");
        createRole(admin, "a".repeat(20_000)).andExpect(status().isBadRequest());
        createRole(admin, "%41".repeat(501)).andExpect(status().isBadRequest()); // 1503 ký tự thô, 501 sau giải mã
        assertEquals(before, count("select count(*) from roles"));
        createRole(admin, "%41".repeat(500)).andExpect(status().isCreated());
    }

    @Test
    void malformedPercentSequencesDoNotCauseServerError() throws Exception {
        var admin = withRole("ADMIN");
        for (String r : List.of("%", "%E1%BA", "%C3%28", "%FF%FE", "%%41", "%2", "%G1")) {
            createRole(admin, r).andExpect(status().isCreated());
        }
    }

    @Test
    void encodedCrlfIsStoredAsDataAndReturnedJsonEscaped() throws Exception {
        var admin = withRole("ADMIN");
        long id = body(createRole(admin, "ok%0D%0AX-Injected: 1").andExpect(status().isCreated())).get("id").asLong();
        assertEquals("ok\r\nX-Injected: 1", lastRoleLog(admin, id).get("reason").asText());
    }

    /** PostgreSQL text không nhận byte 0x00: %00 phải bị chặn (400) hoặc loại bỏ, không được lọt vào DB. */
    @Test
    void encodedNulByteNeverReachesTheDatabase() throws Exception {
        var admin = withRole("ADMIN");
        int roles = count("select count(*) from roles");
        var res = createRole(admin, "abc%00def");
        if (res.andReturn().getResponse().getStatus() == 400) {
            assertEquals(roles, count("select count(*) from roles")); // lệnh ghi đã rollback
        }
        int s = res.andReturn().getResponse().getStatus();
        assertTrue(s < 500, "status " + s);
        if (s == 201) {
            long id = body(res).get("id").asLong();
            assertFalse(lastRoleLog(admin, id).get("reason").asText().contains("\u0000"), "NUL byte stored in audit reason");
        }
    }

    /** Header chỉ gồm khoảng trắng mã hóa phải như header trống: reason null, không phải chuỗi rỗng. */
    @Test
    void encodedBlankReasonIsStoredAsNull() throws Exception {
        var admin = withRole("ADMIN");
        long id = body(createRole(admin, "%20%20").andExpect(status().isCreated())).get("id").asLong();
        assertTrue(lastRoleLog(admin, id).get("reason").isNull(), "reason = " + lastRoleLog(admin, id).get("reason"));
    }

    // ---- last_admin ----

    @Test
    void revokeWithFutureExpiryOnLastAdminIsRejected() throws Exception {
        var a = withRole("ADMIN");
        isolate(a);
        call(put(USERS + "/" + a.id() + "/permissions/rbac.manage"), a,
                Map.of("effect", "REVOKE", "expiresAt", Instant.now().plusSeconds(3600).toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("last_admin"));
    }

    @Test
    void assigningTheStudentRoleCannotBeUsedToDropAdmin() throws Exception {
        var a = withRole("ADMIN");
        isolate(a);
        call(delete(USERS + "/" + a.id() + "/roles/ADMIN"), a, null).andExpect(status().isConflict());
        call(put(USERS + "/" + a.id() + "/permissions/rbac.manage"), a, Map.of("effect", "REVOKE")).andExpect(status().isConflict());
        call(get("/api/admin/roles"), a, null).andExpect(status().isOk());
    }

    /**
     * Người cuối cùng chỉ còn rbac.manage qua GRANT có hạn vẫn được gỡ role ADMIN; tới hạn thì không còn ai quản trị được.
     * Kiểm tra last_admin chỉ xét thời điểm hiện tại nên bỏ lọt trường hợp này.
     */
    @Test
    void lastAdminCannotBeBypassedWithAnExpiringSelfGrant() throws Exception {
        var a = withRole("ADMIN");
        isolate(a);
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        clock.set(now);
        call(put(USERS + "/" + a.id() + "/permissions/rbac.manage"), a,
                Map.of("effect", "GRANT", "expiresAt", now.plusSeconds(3600).toString())).andExpect(status().isOk());
        var remove = call(delete(USERS + "/" + a.id() + "/roles/ADMIN"), a, null);
        int s = remove.andReturn().getResponse().getStatus();
        clock.set(now.plusSeconds(7200));
        if (s == 200) {
            int lockedOut = call(get("/api/admin/roles"), a, null).andReturn().getResponse().getStatus();
            assertEquals(200, lockedOut, "system has no rbac.manage holder after the grant expired (status " + lockedOut + ")");
        } else {
            assertEquals(409, s);
        }
    }

    @Test
    void durableGrantCannotBeTurnedIntoAnExpiringOneByTheLastHolder() throws Exception {
        var admin = withRole("ADMIN");
        var s = register(newEmail(), PASSWORD);
        isolate(admin, s);
        call(put(USERS + "/" + s.id() + "/permissions/rbac.manage"), admin, Map.of("effect", "GRANT")).andExpect(status().isOk());
        call(delete(USERS + "/" + admin.id() + "/roles/ADMIN"), admin, null).andExpect(status().isOk());
        call(put(USERS + "/" + s.id() + "/permissions/rbac.manage"), s,
                Map.of("effect", "GRANT", "expiresAt", Instant.now().plusSeconds(3600).toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("last_admin"));
    }

    /** Hai admin gỡ ADMIN của nhau cùng lúc: đúng một lệnh thành công, luôn còn người quản trị, không có 500/deadlock. */
    @Test
    void concurrentMutualRemovalNeverLeavesZeroAdmins() throws Exception {
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 8; round++) {
                var a = withRole("ADMIN");
                var b = withRole("ADMIN");
                restore();
                isolate(a, b);
                var barrier = new java.util.concurrent.CyclicBarrier(2);
                java.util.concurrent.Callable<Integer> ab = () -> {
                    barrier.await();
                    return call(delete(USERS + "/" + b.id() + "/roles/ADMIN"), a, null).andReturn().getResponse().getStatus();
                };
                java.util.concurrent.Callable<Integer> ba = () -> {
                    barrier.await();
                    return call(delete(USERS + "/" + a.id() + "/roles/ADMIN"), b, null).andReturn().getResponse().getStatus();
                };
                var f1 = pool.submit(ab);
                var f2 = pool.submit(ba);
                var got = new java.util.TreeSet<>(List.of(f1.get(30, java.util.concurrent.TimeUnit.SECONDS),
                        f2.get(30, java.util.concurrent.TimeUnit.SECONDS)));
                int holders = count("select count(*) from user_roles ur join roles r on r.id = ur.role_id where r.code = 'ADMIN' and ur.user_id in (?, ?)",
                        a.id(), b.id());
                assertEquals(1, holders, "round " + round + " statuses " + got);
                assertEquals(new java.util.TreeSet<>(List.of(200, 409)), got, "round " + round);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
