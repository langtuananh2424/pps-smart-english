package com.pps.smartenglish.commerce;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.support.ApiTestBase;
import com.pps.smartenglish.support.MutableClockConfig;
import com.pps.smartenglish.support.MutableClockConfig.MutableClock;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;

/** Tester #56: quyền, SQL injection ở lọc email, mốc EXPIRED, CSV injection mở rộng, tra cứu học viên. */
@Import(MutableClockConfig.class)
class AdminOrderTesterTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";

    @Autowired MutableClock clock;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private Account staff(String role) throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = ?", u.id(), role);
        return login(u.email(), PASSWORD);
    }

    private long buyer(String email, String name) {
        long id = insert("users", new HashMap<>(Map.of("email", email, "status", "ACTIVE")));
        jdbc.update("insert into user_profiles (user_id, display_name) values (?, ?)", id, name);
        return id;
    }

    private long product(String name) {
        var cols = new HashMap<String, Object>(Map.of("type", "AI_ADDON", "name", name, "price_vnd", 1000L, "status", "ACTIVE"));
        cols.put("access_days", null);
        cols.put("ai_quota", 10);
        return insert("products", cols);
    }

    private long order(long userId, String status, Instant created, Instant expires, Instant paid, String ref) {
        var cols = new HashMap<String, Object>(Map.of("user_id", userId, "code", "ORD-" + UUID.randomUUID().toString().substring(0, 8),
                "status", status, "total_vnd", 1000L, "expires_at", expires.atOffset(ZoneOffset.UTC), "provider", "fake",
                "created_at", created.atOffset(ZoneOffset.UTC)));
        cols.put("paid_at", paid == null ? null : paid.atOffset(ZoneOffset.UTC));
        long id = insert("orders", cols);
        insert("order_items", Map.of("order_id", id, "product_id", product("Gói " + UUID.randomUUID()), "price_vnd", 1000L));
        if (ref != null) {
            insert("payment_transactions", Map.of("order_id", id, "provider", "fake", "provider_ref", ref, "amount_vnd", 1000L,
                    "status", "APPLIED", "raw_payload", "{\"signature\":\"top-secret\"}"));
        }
        return id;
    }

    // ---------- Quyền ----------

    @Test
    void ordersAndStudentLookupNeedTheirOwnPermission() throws Exception {
        long u = buyer("perm-" + UUID.randomUUID() + "@example.com", "Quyền");
        long id = order(u, "PAID", Instant.parse("2008-01-01T00:00:00Z"), Instant.parse("2099-01-01T00:00:00Z"),
                Instant.parse("2008-01-01T00:00:00Z"), "TX-P");
        var assistant = staff("ASSISTANT"); // có student.view, không có order.view
        var accountant = staff("ACCOUNTANT"); // có order.view, không có student.view
        var student = register(newEmail(), PASSWORD);
        for (String url : List.of("/api/admin/orders", "/api/admin/orders/" + id, "/api/admin/orders/export.csv?from=2008-01-01&to=2008-01-01")) {
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
            send(get(url), assistant.accessToken()).andExpect(status().isForbidden());
            send(get(url), student.accessToken()).andExpect(status().isForbidden());
            send(get(url), accountant.accessToken()).andExpect(status().isOk());
        }
        for (String url : List.of("/api/admin/support/students?q=perm", "/api/admin/support/students/" + u)) {
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
            send(get(url), accountant.accessToken()).andExpect(status().isForbidden());
            send(get(url), student.accessToken()).andExpect(status().isForbidden());
        }
        send(get("/api/admin/support/students?q=perm"), assistant.accessToken()).andExpect(status().isOk());
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        var acc = staff("ACCOUNTANT");
        String t = acc.accessToken();
        String bad = t.substring(0, t.length() - 3) + (t.endsWith("AAA") ? "BBB" : "AAA");
        send(get("/api/admin/orders"), bad).andExpect(status().isUnauthorized());
    }

    // ---------- Injection ----------

    @Test
    void emailFilterIsNotSqlInjectable() throws Exception {
        var acc = staff("SUPPORT");
        long u = buyer("sqli-" + UUID.randomUUID() + "@example.com", "SQLi");
        order(u, "PAID", Instant.parse("2009-01-01T00:00:00Z"), Instant.parse("2099-01-01T00:00:00Z"), Instant.parse("2009-01-01T00:00:00Z"), null);
        for (String p : List.of("' OR 1=1 --", "%' OR '1'='1", "x'); DROP TABLE orders; --", "\\", "!", "%%%")) { // "_" bỏ: email thật có thể chứa "_"
            send(get("/api/admin/orders").param("email", p), acc.accessToken()).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(0)));
        }
        assertTrue(count("select count(*) from orders") > 0);
        for (String p : List.of("' OR 1=1 --", "x'); DROP TABLE users; --")) {
            send(get("/api/admin/support/students").param("q", p), acc.accessToken()).andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }
        // limit/cursor rác không lọt thành 500
        send(get("/api/admin/orders?limit=100000"), acc.accessToken()).andExpect(status().is(org.hamcrest.Matchers.anyOf(
                org.hamcrest.Matchers.is(200), org.hamcrest.Matchers.is(400))));
        send(get("/api/admin/orders?cursor=%27OR1%3D1"), acc.accessToken()).andExpect(status().is4xxClientError());
    }

    @Test
    void csvNeutralizesEveryFormulaLeadAndKeepsVietnamese() throws Exception {
        var acc = staff("ACCOUNTANT");
        Instant paid = Instant.parse("2010-04-04T05:00:00Z");
        var names = List.of("+1+1", "-2+3", "\t=1", "\r=1", "@cmd", "Trần Thị Ánh");
        for (int i = 0; i < names.size(); i++) { // provider_ref là unique nên mỗi đơn một mã
            long u = buyer("csv-" + UUID.randomUUID() + "@example.com", names.get(i));
            order(u, "PAID", paid, Instant.parse("2099-01-01T00:00:00Z"), paid, "=HYPERLINK(\"x" + i + "\")");
        }
        var res = send(get("/api/admin/orders/export.csv?from=2010-04-04&to=2010-04-04"), acc.accessToken()).andExpect(status().isOk())
                .andReturn().getResponse();
        byte[] bytes = res.getContentAsByteArray();
        assertEquals((byte) 0xEF, bytes[0]);
        assertEquals((byte) 0xBB, bytes[1]);
        assertEquals((byte) 0xBF, bytes[2]);
        assertTrue(res.getHeader("Content-Disposition").contains("orders_2010-04-04_2010-04-04.csv"));
        String body = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(body.contains(",'+1+1,"), body);
        assertTrue(body.contains(",'-2+3,"), body);
        assertTrue(body.contains(",'\t=1,"), body);
        assertTrue(body.contains(",\"'\r=1\","), body);
        assertTrue(body.contains(",'@cmd,"), body);
        assertTrue(body.contains(",Trần Thị Ánh,"), body);
        assertTrue(body.contains(",\"'=HYPERLINK(\"\"x0\"\")\"\r\n"), body);
        assertFalse(body.contains("top-secret"));
    }

    // ---------- EXPIRED đúng mốc ----------

    @Test
    void pendingBecomesExpiredExactlyAtExpiresAt() throws Exception {
        var acc = staff("SUPPORT");
        Instant t = Instant.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(3600);
        String mail = "moc-" + UUID.randomUUID() + "@example.com";
        long u = buyer(mail, "Mốc");
        long id = order(u, "PENDING", Instant.parse("2011-01-01T00:00:00Z"), t, null, null);

        clock.set(t.minusSeconds(1));
        send(get("/api/admin/orders/" + id), acc.accessToken()).andExpect(jsonPath("$.status").value("PENDING"));
        send(get("/api/admin/orders").param("email", mail).param("status", "PENDING"), acc.accessToken()).andExpect(jsonPath("$.data", hasSize(1)));
        send(get("/api/admin/orders").param("email", mail).param("status", "EXPIRED"), acc.accessToken()).andExpect(jsonPath("$.data", hasSize(0)));

        clock.set(t);
        send(get("/api/admin/orders/" + id), acc.accessToken()).andExpect(jsonPath("$.status").value("EXPIRED"));
        send(get("/api/admin/orders").param("email", mail).param("status", "EXPIRED"), acc.accessToken())
                .andExpect(jsonPath("$.data", hasSize(1))).andExpect(jsonPath("$.data[0].status").value("EXPIRED"));
        send(get("/api/admin/orders").param("email", mail).param("status", "PENDING"), acc.accessToken()).andExpect(jsonPath("$.data", hasSize(0)));
        assertEquals("PENDING", jdbc.queryForObject("select status from orders where id = ?", String.class, id));
    }

    @Test
    void noEndpointLeaksRawPayload() throws Exception {
        var acc = staff("SUPPORT");
        String mail = "raw-" + UUID.randomUUID() + "@example.com";
        long u = buyer(mail, "Raw");
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = 'STUDENT'", u);
        Instant paid = Instant.parse("2012-02-02T05:00:00Z");
        long id = order(u, "PAID", paid, Instant.parse("2099-01-01T00:00:00Z"), paid, "TX-RAW");
        for (String url : List.of("/api/admin/orders?email=" + mail, "/api/admin/orders/" + id,
                "/api/admin/orders/export.csv?from=2012-02-02&to=2012-02-02", "/api/admin/support/students?q=" + mail,
                "/api/admin/support/students/" + u)) {
            String body = send(get(url), acc.accessToken()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertFalse(body.contains("top-secret") || body.contains("rawPayload") || body.contains("raw_payload"), url);
        }
    }

    // ---------- Tra cứu học viên ----------

    @Test
    void studentLookupNeedsThreeCharsAfterTrimAndCapsAtTwenty() throws Exception {
        var acc = staff("SUPPORT");
        String tag = "cap" + UUID.randomUUID().toString().substring(0, 6);
        for (int i = 0; i < 25; i++) {
            long u = buyer(tag + "-" + i + "@example.com", "Học viên " + i);
            jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = 'STUDENT'", u);
        }
        for (String q : List.of("", "a", "ab", "  ab  ", "%%", "__")) {
            send(get("/api/admin/support/students").param("q", q), acc.accessToken()).andExpect(status().isBadRequest());
        }
        send(get("/api/admin/support/students"), acc.accessToken()).andExpect(status().is4xxClientError());
        send(get("/api/admin/support/students").param("q", tag), acc.accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(20)));
        // wildcard người dùng gõ là ký tự thường: "%%%" không liệt kê cả bảng
        send(get("/api/admin/support/students").param("q", "%%%"), acc.accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        send(get("/api/admin/support/students").param("q", "___"), acc.accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
