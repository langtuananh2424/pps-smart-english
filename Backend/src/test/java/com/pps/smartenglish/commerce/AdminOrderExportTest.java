package com.pps.smartenglish.commerce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.support.ApiTestBase;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;

/** FR-ADM-03, D-38: GET /api/admin/orders và /export.csv. Mỗi test dùng một năm riêng vì DB H2 dùng chung. */
class AdminOrderExportTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";
    private static final String EXPORT = "/api/admin/orders/export.csv";
    private static final String BOM = "﻿";

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private static OffsetDateTime at(String instant) {
        return OffsetDateTime.ofInstant(Instant.parse(instant), ZoneOffset.UTC);
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

    private long product(String name, long price) {
        var cols = new HashMap<String, Object>(Map.of("type", "AI_ADDON", "name", name, "price_vnd", price, "status", "ACTIVE"));
        cols.put("access_days", null);
        cols.put("ai_quota", 10);
        return insert("products", cols);
    }

    /** Đơn có một dòng hàng; paidAt null cho đơn chưa thanh toán. */
    private String order(long userId, String status, String paidAt, long productId, long price, String ref) {
        String code = "ORD-" + UUID.randomUUID().toString().substring(0, 8);
        var cols = new HashMap<String, Object>(Map.of("user_id", userId, "code", code, "status", status, "total_vnd", price,
                "expires_at", at("2099-01-01T00:00:00Z"), "provider", "fake"));
        cols.put("paid_at", paidAt == null ? null : at(paidAt));
        cols.put("created_at", at(paidAt == null ? "2004-06-01T00:00:00Z" : paidAt));
        long id = insert("orders", cols);
        insert("order_items", Map.of("order_id", id, "product_id", productId, "price_vnd", price));
        if (ref != null) {
            insert("payment_transactions", Map.of("order_id", id, "provider", "fake", "provider_ref", ref,
                    "amount_vnd", price, "status", "APPLIED"));
        }
        return code;
    }

    /** Như order() nhưng đặt hạn thanh toán: quá hạn (2020) hoặc còn hạn (2099). */
    private String pendingOrder(long userId, long productId, String expiresAt) {
        String code = "ORD-" + UUID.randomUUID().toString().substring(0, 8);
        long id = insert("orders", new HashMap<>(Map.of("user_id", userId, "code", code, "status", "PENDING", "total_vnd", 1L,
                "expires_at", at(expiresAt), "provider", "fake", "created_at", at("2007-01-01T00:00:00Z"))));
        insert("order_items", Map.of("order_id", id, "product_id", productId, "price_vnd", 1L));
        return code;
    }

    private String codes(Account a, String query) throws Exception {
        return send(get("/api/admin/orders" + query), a.accessToken()).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void overduePendingShowsAsExpiredInListFiltersAndDetail() throws Exception {
        var acc = staff("SUPPORT");
        long u = buyer("qh-" + UUID.randomUUID() + "@example.com", "Quá hạn");
        long p = product("Gói quá hạn", 1);
        String late = pendingOrder(u, p, "2020-01-01T00:00:00Z");
        String live = pendingOrder(u, p, "2099-01-01T00:00:00Z");
        String q = "?from=2007-01-01&to=2007-01-01&email=qh-";

        String pending = codes(acc, q + "&status=PENDING");
        assertTrue(pending.contains(live));
        assertFalse(pending.contains(late));
        String expired = codes(acc, q + "&status=EXPIRED");
        assertTrue(expired.contains(late));
        assertFalse(expired.contains(live));
        // dòng của đơn quá hạn mang trạng thái EXPIRED, DB vẫn là PENDING
        send(get("/api/admin/orders" + q + "&status=EXPIRED"), acc.accessToken()).andExpect(jsonPath("$.data[0].status").value("EXPIRED"));
        assertEquals("PENDING", jdbc.queryForObject("select status from orders where code = ?", String.class, late));

        long id = jdbc.queryForObject("select id from orders where code = ?", Long.class, late);
        send(get("/api/admin/orders/" + id), acc.accessToken()).andExpect(jsonPath("$.status").value("EXPIRED"));
        long liveId = jdbc.queryForObject("select id from orders where code = ?", Long.class, live);
        send(get("/api/admin/orders/" + liveId), acc.accessToken()).andExpect(jsonPath("$.status").value("PENDING"));
    }

    private String csv(Account a, String query) throws Exception {
        var res = send(get(EXPORT + query), a.accessToken()).andExpect(status().isOk()).andReturn().getResponse();
        assertTrue(res.getContentType().startsWith("text/csv"));
        return res.getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void exportsPaidOrdersInVietnamDaysWithHeaderBomAndAudit() throws Exception {
        var acc = staff("ACCOUNTANT");
        long u = buyer("khach-" + UUID.randomUUID() + "@example.com", "Nguyễn Văn A");
        long p = product("Nạp thêm 10 lượt AI", 49000);
        // 2001-03-10 16:59:59Z = 23:59:59 giờ VN ngày 10 (trong); 17:00:00Z = 00:00 ngày 11 (ngoài khoảng 10..10)
        String inside = order(u, "PAID", "2001-03-10T16:59:59Z", p, 49000, "TX-1");
        String outside = order(u, "PAID", "2001-03-10T17:00:00Z", p, 49000, "TX-2");
        String pending = order(u, "PENDING", null, p, 49000, null);

        String body = csv(acc, "?from=2001-03-10&to=2001-03-10");
        assertTrue(body.startsWith(BOM + "Mã đơn,Thời điểm thanh toán,Email người mua,Họ tên,Tên sản phẩm,Loại sản phẩm,"
                + "Thành tiền (VND),Cổng thanh toán,Mã giao dịch cổng\r\n"));
        assertTrue(body.contains(inside + ",2001-03-10T23:59:59+07:00,"));
        assertTrue(body.contains(",Nguyễn Văn A,Nạp thêm 10 lượt AI,AI_ADDON,49000,fake,TX-1\r\n"));
        assertFalse(body.contains(outside));
        assertFalse(body.contains(pending));

        assertTrue(csv(acc, "?from=2001-03-11&to=2001-03-11").contains(outside));
        assertEquals(1, count("select count(*) from audit_logs where action = 'EXPORT' and target_type = 'ORDER' "
                + "and target_id = '2001-03-10..2001-03-10' and actor_id = ?", acc.id()));
    }

    @Test
    void escapesCsvAndNeutralizesFormulaInjection() throws Exception {
        var acc = staff("DIRECTOR");
        long u = buyer("inj-" + UUID.randomUUID() + "@example.com", "=HYPERLINK(\"http://evil\")");
        long p = product("Gói \"đặc biệt\", có phẩy\nxuống dòng", 1000);
        long p2 = product("@SUM(A1)", 2000);
        order(u, "PAID", "2002-05-05T05:00:00Z", p, 1000, "TX-3");
        order(u, "PAID", "2002-05-05T06:00:00Z", p2, 2000, null);

        String body = csv(acc, "?from=2002-05-05&to=2002-05-05");
        assertTrue(body.contains("\"'=HYPERLINK(\"\"http://evil\"\")\""), body);
        assertTrue(body.contains("\"Gói \"\"đặc biệt\"\", có phẩy\nxuống dòng\""), body);
        assertTrue(body.contains(",'@SUM(A1),AI_ADDON,2000,fake,\r\n"), body); // cổng có, mã giao dịch trống
    }

    @Test
    void badRangeIs400() throws Exception {
        var acc = staff("ACCOUNTANT");
        for (String q : List.of("", "?from=2001-01-01", "?to=2001-01-01", "?from=abc&to=2001-01-01",
                "?from=2001-02-01&to=2001-01-01", "?from=2001-01-01&to=2002-01-02")) { // 367 ngày
            send(get(EXPORT + q), acc.accessToken()).andExpect(status().isBadRequest());
        }
        // đúng 366 ngày (2000 là năm nhuận) thì được
        send(get(EXPORT + "?from=2000-01-01&to=2000-12-31"), acc.accessToken()).andExpect(status().isOk());
    }

    @Test
    void needsOrderViewPermission() throws Exception {
        String q = "?from=2001-01-01&to=2001-01-02";
        mvc.perform(get(EXPORT + q)).andExpect(status().isUnauthorized());
        send(get(EXPORT + q), staff("EDITOR").accessToken()).andExpect(status().isForbidden());
        send(get(EXPORT + q), register(newEmail(), PASSWORD).accessToken()).andExpect(status().isForbidden());
    }

    /** Hơn một lô (500) dòng: keyset không bỏ sót hay lặp. */
    @Test
    void streamsMoreThanOneBatchWithoutLossOrDuplicates() throws Exception {
        long u = buyer("big-" + UUID.randomUUID() + "@example.com", "Khách đông");
        long p = product("Gói lô lớn", 1);
        for (int i = 0; i < 1203; i++) {
            order(u, "PAID", "2003-07-07T05:00:00Z", p, 1, null);
        }
        var lines = csv(staff("ACCOUNTANT"), "?from=2003-07-07&to=2003-07-07").split("\r\n");
        assertEquals(1204, lines.length); // header + 1203 dòng
        assertEquals(1203, java.util.Arrays.stream(lines).skip(1).distinct().count(), "mã đơn phải khác nhau");
    }

    @Test
    void listFiltersByStatusDateEmailAndPages() throws Exception {
        var acc = staff("SUPPORT");
        String mail = "Loc_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        long u = buyer(mail, "Người lọc");
        long other = buyer("khac-" + UUID.randomUUID() + "@example.com", "Khác");
        long p = product("Gói lọc", 5000);
        String paid = order(u, "PAID", "2005-02-02T05:00:00Z", p, 5000, "TX-L1");
        order(u, "PENDING", null, p, 5000, null); // created_at 2004-06-01
        order(other, "PAID", "2005-02-02T05:00:00Z", p, 5000, "TX-L2");

        send(get("/api/admin/orders?email=" + mail.toLowerCase() + "&status=PAID&from=2005-02-01&to=2005-02-03"), acc.accessToken())
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].code").value(paid)).andExpect(jsonPath("$.data[0].displayName").value("Người lọc"))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
        // '_' trong email là ký tự thường, không phải wildcard
        send(get("/api/admin/orders?email=Loc_%25"), acc.accessToken()).andExpect(jsonPath("$.data.length()").value(0));
        send(get("/api/admin/orders?email=" + mail + "&limit=1"), acc.accessToken())
                .andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.nextCursor").exists());
        send(get("/api/admin/orders?status=BOGUS"), acc.accessToken()).andExpect(status().isBadRequest());
    }

    @Test
    void detailShowsItemsTransactionsWithoutRawPayloadAndNeedsPermission() throws Exception {
        long u = buyer("ct-" + UUID.randomUUID() + "@example.com", "Khách chi tiết");
        long p = product("Gói chi tiết", 7000);
        String code = order(u, "PAID", "2006-01-01T05:00:00Z", p, 7000, "TX-D1");
        long id = jdbc.queryForObject("select id from orders where code = ?", Long.class, code);
        insert("payment_transactions", Map.of("order_id", id, "provider", "fake", "provider_ref", "TX-D2",
                "amount_vnd", 1, "status", "MISMATCH", "raw_payload", "{\"secret\":\"x\"}"));

        var res = send(get("/api/admin/orders/" + id), staff("SUPPORT").accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code)).andExpect(jsonPath("$.buyer.displayName").value("Khách chi tiết"))
                .andExpect(jsonPath("$.items[0].productType").value("AI_ADDON"))
                .andExpect(jsonPath("$.items[0].courseTitle").doesNotExist())
                .andExpect(jsonPath("$.items[0].priceVnd").value(7000))
                .andExpect(jsonPath("$.transactions.length()").value(2))
                .andExpect(jsonPath("$.transactions[1].status").value("MISMATCH")).andReturn().getResponse();
        assertFalse(res.getContentAsString().contains("secret"));
        assertFalse(res.getContentAsString().contains("rawPayload"));
        send(get("/api/admin/orders/99999999"), staff("ACCOUNTANT").accessToken()).andExpect(status().isNotFound());
        send(get("/api/admin/orders/" + id), staff("EDITOR").accessToken()).andExpect(status().isForbidden());
    }
}
