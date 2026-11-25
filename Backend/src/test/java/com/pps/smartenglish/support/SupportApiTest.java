package com.pps.smartenglish.support;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** FR-SUP-03: tra cứu và thao tác hỗ trợ học viên, quyền student.view / student.support, nhật ký kèm lý do. */
class SupportApiTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private Account staff(String role) throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = ?", u.id(), role);
        return login(u.email(), PASSWORD);
    }

    private Account student() throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("update users set email_verified_at = CURRENT_TIMESTAMP where id = ?", u.id());
        return u;
    }

    private ResultActions postJson(String url, String token, Object body) throws Exception {
        return send(MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)), token);
    }

    private long levelProduct(int days) {
        long c = insert("courses", Map.of("slug", "sup-" + UUID.randomUUID(), "title", "Khóa hỗ trợ", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED"));
        var cols = new HashMap<String, Object>(Map.of("type", "LEVEL", "name", "SP " + UUID.randomUUID(), "price_vnd", 1000L, "status", "ACTIVE"));
        cols.put("access_days", days);
        cols.put("ai_quota", null);
        long id = insert("products", cols);
        jdbc.update("insert into product_levels (product_id, level_id) values (?, ?)", id, lv);
        return id;
    }

    private String order(Account u, long productId) throws Exception {
        var req = MockMvcRequestBuilders.post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", UUID.randomUUID().toString()).content(json.writeValueAsString(Map.of("productIds", List.of(productId))));
        String body = send(req, u.accessToken()).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("code").asText();
    }

    @Test
    void searchByEmailOrOrderCodeFindsOnlyStudents() throws Exception {
        var support = staff("SUPPORT");
        var s = student();
        String code = order(s, levelProduct(30));

        send(get("/api/admin/support/students").param("q", s.email().substring(0, s.email().indexOf('@'))), support.accessToken())
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(s.id()))
                .andExpect(jsonPath("$[0].emailVerified").value(true));
        send(get("/api/admin/support/students").param("q", code), support.accessToken())
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(s.id()));
        // tài khoản nhân sự không hiện trong tra cứu học viên
        send(get("/api/admin/support/students").param("q", support.email()), support.accessToken())
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        send(get("/api/admin/support/students").param("q", "ab"), support.accessToken()).andExpect(status().isBadRequest());
    }

    @Test
    void detailShowsOrdersEnrollmentsAndQuotaWithoutSecrets() throws Exception {
        var support = staff("SUPPORT");
        var s = student();
        String code = order(s, levelProduct(30));
        postJson("/api/admin/support/orders/" + code + "/activate", support.accessToken(), Map.of("reason", "Webhook lỗi")).andExpect(status().isOk());

        send(get("/api/admin/support/students").param("q", code), support.accessToken())
                .andExpect(jsonPath("$[0].courses", hasSize(1)));
        var res = send(get("/api/admin/support/students/" + s.id()), support.accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.student.courses", hasSize(1)))
                .andExpect(jsonPath("$.orders[0].code").value(code)).andExpect(jsonPath("$.orders[0].status").value("PAID"))
                .andExpect(jsonPath("$.enrollments", hasSize(1))).andExpect(jsonPath("$.quota.pool").value(0))
                .andReturn().getResponse().getContentAsString();
        assertEquals(false, res.contains("passwordHash") || res.contains("password_hash"));
        // đơn PENDING quá hạn hiện EXPIRED trong hồ sơ, DB vẫn PENDING
        String late = order(s, levelProduct(30));
        jdbc.update("update orders set expires_at = ? where code = ?", java.time.OffsetDateTime.parse("2020-01-01T00:00:00Z"), late);
        send(get("/api/admin/support/students/" + s.id()), support.accessToken())
                .andExpect(jsonPath("$.orders[0].code").value(late)).andExpect(jsonPath("$.orders[0].status").value("EXPIRED"));
        send(get("/api/admin/support/students/999999"), support.accessToken()).andExpect(status().isNotFound());
        // không xem được nhân sự qua đường học viên
        send(get("/api/admin/support/students/" + support.id()), support.accessToken()).andExpect(status().isNotFound());
    }

    @Test
    void manualActivationGrantsOnceAndNeedsReason() throws Exception {
        var support = staff("SUPPORT");
        var s = student();
        String code = order(s, levelProduct(30));

        postJson("/api/admin/support/orders/" + code + "/activate", support.accessToken(), Map.of("reason", " ")).andExpect(status().isBadRequest());
        postJson("/api/admin/support/orders/" + code + "/activate", support.accessToken(), Map.of("reason", "Khách chuyển khoản, webhook lỗi"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(s.id()));
        assertEquals(1, count("select count(*) from user_course_enrollments where user_id = ?", s.id()));
        assertEquals("PAID", jdbc.queryForObject("select status from orders where code = ?", String.class, code));
        assertEquals(1, count("select count(*) from audit_logs where action = 'MANUAL_ACTIVATE' and target_id = ? and after_value like '%webhook lỗi%'", code));

        postJson("/api/admin/support/orders/" + code + "/activate", support.accessToken(), Map.of("reason", "Thử lại"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("order_already_paid"));
        postJson("/api/admin/support/orders/ORD-NOPE00/activate", support.accessToken(), Map.of("reason", "x")).andExpect(status().isNotFound());

        String cancelled = order(s, levelProduct(30));
        jdbc.update("update orders set status = 'CANCELLED' where code = ?", cancelled);
        postJson("/api/admin/support/orders/" + cancelled + "/activate", support.accessToken(), Map.of("reason", "x"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("order_cancelled"));
    }

    @Test
    void bonusQuotaCreditsPoolAndLedgerWithReason() throws Exception {
        var support = staff("SUPPORT");
        var s = student();

        postJson("/api/admin/support/students/" + s.id() + "/bonus-quota", support.accessToken(), Map.of("amount", 5, "reason", "Bù lượt do lỗi chấm"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pool").value(5));
        postJson("/api/admin/support/students/" + s.id() + "/bonus-quota", support.accessToken(), Map.of("amount", 3, "reason", "Bù thêm"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pool").value(8));
        assertEquals(8, jdbc.queryForObject("select balance from user_ai_quota where user_id = ?", Integer.class, s.id()));
        assertEquals(2, count("select count(*) from ai_quota_ledger where user_id = ? and reason = 'REFUND' and ref_id < 0", s.id()));
        assertEquals(2, count("select count(*) from audit_logs where action = 'BONUS_QUOTA' and target_id = ?", String.valueOf(s.id())));

        for (Object bad : List.of(0, 501, -1)) {
            postJson("/api/admin/support/students/" + s.id() + "/bonus-quota", support.accessToken(), Map.of("amount", bad, "reason", "x"))
                    .andExpect(status().isBadRequest());
        }
        postJson("/api/admin/support/students/" + s.id() + "/bonus-quota", support.accessToken(), Map.of("amount", 1)).andExpect(status().isBadRequest());
    }

    @Test
    void unlockAccountNeedsReasonAndAuditsIt() throws Exception {
        var support = staff("SUPPORT");
        var s = student();

        postJson("/api/admin/support/students/" + s.id() + "/unlock", support.accessToken(), Map.of("reason", "x"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("not_locked"));
        jdbc.update("update users set status = 'LOCKED' where id = ?", s.id());
        postJson("/api/admin/support/students/" + s.id() + "/unlock", support.accessToken(), Map.of()).andExpect(status().isBadRequest());
        postJson("/api/admin/support/students/" + s.id() + "/unlock", support.accessToken(), Map.of("reason", "Khách xác minh qua hotline"))
                .andExpect(status().isNoContent());
        assertEquals("ACTIVE", jdbc.queryForObject("select status from users where id = ?", String.class, s.id()));
        assertEquals(1, count("select count(*) from audit_logs where action = 'UNLOCK_ACCOUNT' and target_id = ? and after_value like '%hotline%'",
                String.valueOf(s.id())));
    }

    @Test
    void resendVerificationOnlyForUnverifiedEmail() throws Exception {
        var support = staff("SUPPORT");
        var verified = student();
        postJson("/api/admin/support/students/" + verified.id() + "/resend-verification", support.accessToken(), Map.of())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("already_verified"));

        var unverified = register(newEmail(), PASSWORD);
        postJson("/api/admin/support/students/" + unverified.id() + "/resend-verification", support.accessToken(), Map.of())
                .andExpect(status().isNoContent());
    }

    @Test
    void readNeedsStudentViewAndWriteNeedsStudentSupport() throws Exception {
        var teacher = staff("TEACHER"); // student.view, không có student.support
        var s = student();
        send(get("/api/admin/support/students").param("q", s.email()), teacher.accessToken()).andExpect(status().isOk());
        postJson("/api/admin/support/students/" + s.id() + "/bonus-quota", teacher.accessToken(), Map.of("amount", 1, "reason", "x"))
                .andExpect(status().isForbidden());

        var other = student();
        send(get("/api/admin/support/students").param("q", s.email()), other.accessToken()).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/support/students").param("q", s.email())).andExpect(status().isUnauthorized());
        // Kế toán không có student.view: không tra cứu được học viên
        send(get("/api/admin/support/students").param("q", s.email()), staff("ACCOUNTANT").accessToken()).andExpect(status().isForbidden());
    }
}
