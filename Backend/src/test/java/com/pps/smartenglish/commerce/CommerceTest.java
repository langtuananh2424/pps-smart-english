package com.pps.smartenglish.commerce;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.commerce.payment.FakePaymentGateway;
import com.pps.smartenglish.commerce.service.OrderExpiryJob;
import com.pps.smartenglish.support.ApiTestBase;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

class CommerceTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";

    @Autowired FakePaymentGateway fake;
    @Autowired OrderExpiryJob expiryJob;

    /** Nội dung trả phí: Level, một bài và một bộ câu hỏi nằm trong topic KHÔNG học thử. */
    private record Content(long levelId, long lessonId, long bankId) {}

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private Content content() {
        long c = insert("courses", Map.of("slug", "com-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A"));
        long t = insert("topics", Map.of("level_id", lv, "title", "Topic", "is_free_trial", false));
        var lesson = new HashMap<String, Object>(Map.of("topic_id", t, "type", "CONNECTIVE_VIDEO", "title", "Bài", "status", "PUBLISHED"));
        lesson.put("video_source", "YOUTUBE");
        lesson.put("video_ref", "abc");
        lesson.put("duration_sec", 60);
        long bank = insert("question_banks", Map.of("topic_id", t, "title", "Bộ", "is_trial", false));
        return new Content(lv, insert("lessons", lesson), bank);
    }

    private long product(String type, long price, Integer days, Integer ai, String status, long... levelIds) {
        var cols = new HashMap<String, Object>(Map.of("type", type, "name", "SP " + UUID.randomUUID(), "price_vnd", price, "status", status));
        cols.put("access_days", days);
        cols.put("ai_quota", ai);
        long id = insert("products", cols);
        for (long l : levelIds) {
            jdbc.update("insert into product_levels (product_id, level_id) values (?, ?)", id, l);
        }
        return id;
    }

    private Account buyer() throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("update users set email_verified_at = CURRENT_TIMESTAMP where id = ?", u.id());
        return u;
    }

    private ResultActions placeOrder(String token, String key, Object productIds) throws Exception {
        var req = MockMvcRequestBuilders.post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("productIds", productIds)));
        if (key != null) {
            req.header("Idempotency-Key", key);
        }
        return token == null ? mvc.perform(req) : send(req, token);
    }

    private String orderCode(Account u, long... productIds) throws Exception {
        var ids = java.util.Arrays.stream(productIds).boxed().toList();
        String body = placeOrder(u.accessToken(), UUID.randomUUID().toString(), ids).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("code").asText();
    }

    private ResultActions webhook(String provider, JsonNode body) throws Exception {
        return post("/api/payments/webhook/" + provider, body, newIp());
    }

    private ResultActions pay(String code, long amount, String reference) throws Exception {
        return webhook("fake", fake.signedPaid(code, amount, reference));
    }

    private ResultActions pay(String code, long amount) throws Exception {
        return pay(code, amount, "REF-" + UUID.randomUUID());
    }

    private int balance(Account u) {
        return count("select balance from user_ai_quota where user_id = ?", u.id());
    }

    private String orderStatus(String code) {
        return jdbc.queryForObject("select status from orders where code = ?", String.class, code);
    }

    private ResultActions lesson(Content c, Account u) throws Exception {
        var req = get("/api/lessons/" + c.lessonId);
        return u == null ? mvc.perform(req) : send(req, u.accessToken());
    }

    @Test
    void productsArePublicAndOnlyActive() throws Exception {
        var c = content();
        long active = product("LEVEL", 490000, 180, 20, "ACTIVE", c.levelId);
        long inactive = product("LEVEL", 1, 1, 1, "INACTIVE", c.levelId);
        mvc.perform(get("/api/products")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + active + ")]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.id==" + active + ")].type").value(org.hamcrest.Matchers.hasItem("LEVEL")))
                .andExpect(jsonPath("$[?(@.id==" + active + ")].priceVnd").value(org.hamcrest.Matchers.hasItem(490000)))
                .andExpect(jsonPath("$[?(@.id==" + active + ")].accessDays").value(org.hamcrest.Matchers.hasItem(180)))
                .andExpect(jsonPath("$[?(@.id==" + active + ")].aiQuota").value(org.hamcrest.Matchers.hasItem(20)))
                .andExpect(jsonPath("$[?(@.id==" + active + ")].levelIds[0]").value(org.hamcrest.Matchers.hasItem((int) c.levelId)))
                .andExpect(jsonPath("$[?(@.id==" + inactive + ")]", hasSize(0)));
    }

    @Test
    void createOrderLocksPriceAndReturnsPayment() throws Exception {
        var c = content();
        long p1 = product("LEVEL", 490000, 180, 20, "ACTIVE", c.levelId);
        long p2 = product("AI_ADDON", 99000, null, 20, "ACTIVE");
        var u = buyer();
        var before = Instant.now();
        var body = placeOrder(u.accessToken(), UUID.randomUUID().toString(), List.of(p1, p2)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", matchesPattern("ORD-[A-Z0-9]{6}")))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalVnd").value(589000))
                .andExpect(jsonPath("$.payment.qrContent").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String code = json.readTree(body).get("code").asText();
        Instant expires = Instant.parse(json.readTree(body).at("/payment/expiresAt").asText());
        long minutes = Duration.between(before, expires).toMinutes();
        assertTrue(minutes == 29 || minutes == 30, "hạn thanh toán ~30 phút, nhận " + minutes);

        jdbc.update("update products set price_vnd = 1 where id = ?", p1); // đổi giá sau khi mua không ảnh hưởng đơn cũ
        send(get("/api/orders/" + code), u.accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVnd").value(589000))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[?(@.productId==" + p1 + ")].priceVnd").value(org.hamcrest.Matchers.hasItem(490000)))
                .andExpect(jsonPath("$.payment.qrContent").isNotEmpty());
    }

    @Test
    void unverifiedEmailIsForbidden() throws Exception {
        var c = content();
        long p = product("LEVEL", 1000, 30, 0, "ACTIVE", c.levelId);
        var u = register(newEmail(), PASSWORD);
        placeOrder(u.accessToken(), UUID.randomUUID().toString(), List.of(p)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("email_not_verified"));
        assertEquals(0, count("select count(*) from orders where user_id = ?", u.id()));
    }

    @Test
    void missingKeyAndInvalidBodiesReturn400() throws Exception {
        var c = content();
        long ok = product("LEVEL", 1000, 30, 0, "ACTIVE", c.levelId);
        long off = product("LEVEL", 1000, 30, 0, "INACTIVE", c.levelId);
        var u = buyer();
        placeOrder(u.accessToken(), null, List.of(ok)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_failed"));
        for (Object ids : List.of(List.of(), List.of(ok, ok), List.of(off), List.of(999999999L), List.of(ok, off))) {
            placeOrder(u.accessToken(), UUID.randomUUID().toString(), ids).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("validation_failed"));
        }
        assertEquals(0, count("select count(*) from orders where user_id = ?", u.id()));
    }

    @Test
    void sameKeyAndBodyReplaysTheSameOrder() throws Exception {
        var c = content();
        long a = product("LEVEL", 1000, 30, 0, "ACTIVE", c.levelId);
        long b = product("AI_ADDON", 2000, null, 5, "ACTIVE");
        var u = buyer();
        String key = UUID.randomUUID().toString();
        String first = placeOrder(u.accessToken(), key, List.of(a, b)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String second = placeOrder(u.accessToken(), key, List.of(b, a)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertEquals(json.readTree(first), json.readTree(second));
        assertEquals(1, count("select count(*) from orders where user_id = ?", u.id()));
    }

    @Test
    void sameKeyDifferentBodyReturns409() throws Exception {
        var c = content();
        long a = product("LEVEL", 1000, 30, 0, "ACTIVE", c.levelId);
        long b = product("AI_ADDON", 2000, null, 5, "ACTIVE");
        var u = buyer();
        String key = UUID.randomUUID().toString();
        placeOrder(u.accessToken(), key, List.of(a)).andExpect(status().isCreated());
        placeOrder(u.accessToken(), key, List.of(b)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("idempotency_key_reused"));
        assertEquals(1, count("select count(*) from orders where user_id = ?", u.id()));
    }

    @Test
    void sameKeyIsScopedPerUser() throws Exception {
        var c = content();
        long a = product("LEVEL", 1000, 30, 0, "ACTIVE", c.levelId);
        var u1 = buyer();
        var u2 = buyer();
        String key = UUID.randomUUID().toString();
        placeOrder(u1.accessToken(), key, List.of(a)).andExpect(status().isCreated());
        placeOrder(u2.accessToken(), key, List.of(a)).andExpect(status().isCreated());
        assertEquals(1, count("select count(*) from orders where user_id = ?", u2.id()));
    }

    @Test
    void concurrentRequestsWithSameKeyCreateOneOrder() throws Exception {
        var c = content();
        long a = product("LEVEL", 1000, 30, 0, "ACTIVE", c.levelId);
        var u = buyer();
        String key = UUID.randomUUID().toString();
        int n = 6;
        var pool = Executors.newFixedThreadPool(n);
        List<String> codes = new ArrayList<>();
        try {
            List<Callable<String>> calls = IntStream.range(0, n).<Callable<String>>mapToObj(i -> () -> {
                var res = placeOrder(u.accessToken(), key, List.of(a)).andReturn().getResponse();
                return res.getStatus() + ":" + json.readTree(res.getContentAsString()).path("code").asText();
            }).toList();
            for (var f : pool.invokeAll(calls)) {
                codes.add(f.get());
            }
        } finally {
            pool.shutdown();
        }
        assertEquals(1, codes.stream().distinct().count(), "mọi request phải nhận cùng một đơn: " + codes);
        assertTrue(codes.get(0).startsWith("201:ORD-"), codes.get(0));
        assertEquals(1, count("select count(*) from orders where user_id = ?", u.id()));
    }

    @Test
    void ordersRequireTokenAndBelongToOwnerOnly() throws Exception {
        var c = content();
        long a = product("LEVEL", 1000, 30, 0, "ACTIVE", c.levelId);
        var owner = buyer();
        var other = buyer();
        placeOrder(null, UUID.randomUUID().toString(), List.of(a)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/orders/ORD-AAAAAA")).andExpect(status().isUnauthorized());
        String code = orderCode(owner, a);
        send(get("/api/orders/" + code), other.accessToken()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("order_not_found"));
        send(get("/api/orders/ORD-NOPE00"), owner.accessToken()).andExpect(status().isNotFound());
    }

    @Test
    void badSignatureIs401AndChangesNothing() throws Exception {
        var c = content();
        long a = product("LEVEL", 1000, 30, 5, "ACTIVE", c.levelId);
        var u = buyer();
        String code = orderCode(u, a);
        var forged = fake.signedPaid(code, 1000, "REF-FORGED");
        forged.put("amount", 1); // sửa nội dung sau khi ký
        webhook("fake", forged).andExpect(status().isUnauthorized());
        var unsigned = fake.signedPaid(code, 1000, "REF-UNSIGNED");
        unsigned.remove("signature");
        webhook("fake", unsigned).andExpect(status().isUnauthorized());
        assertEquals("PENDING", orderStatus(code));
        assertEquals(0, count("select count(*) from payment_transactions where provider_ref like 'REF-%FORGED' or provider_ref = 'REF-UNSIGNED'"));
        webhook("unknown", fake.signedPaid(code, 1000, "REF-X")).andExpect(status().isNotFound());
    }

    @Test
    void nonSuccessPayloadIsAcknowledgedWithoutAction() throws Exception {
        var c = content();
        long a = product("LEVEL", 1000, 30, 5, "ACTIVE", c.levelId);
        var u = buyer();
        String code = orderCode(u, a);
        webhook("fake", fake.signed(code, 1000, "REF-FAILED", "FAILED")).andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("IGNORED"));
        assertEquals("PENDING", orderStatus(code));
    }

    @Test
    void successfulPaymentOpensEnrollmentAndCreditsQuotaWithLedger() throws Exception {
        var c = content();
        long a = product("LEVEL", 490000, 180, 20, "ACTIVE", c.levelId);
        var u = buyer();
        String code = orderCode(u, a);
        long orderId = jdbc.queryForObject("select id from orders where code = ?", Long.class, code);

        pay(code, 490000).andExpect(status().isOk()).andExpect(jsonPath("$.result").value("APPLIED"));

        assertEquals("PAID", orderStatus(code));
        assertEquals(20, balance(u));
        assertEquals(1, count("select count(*) from ai_quota_ledger where user_id = ? and reason = 'PURCHASE' and delta = 20 and ref_id = ?", u.id(), orderId));
        OffsetDateTime exp = jdbc.queryForObject("select expires_at from user_course_enrollments where user_id = ? and level_id = ?",
                OffsetDateTime.class, u.id(), c.levelId);
        long days = Duration.between(Instant.now(), exp.toInstant()).toDays();
        assertTrue(days == 179 || days == 180, "hạn ~180 ngày, nhận " + days);
        assertEquals(1, count("select count(*) from payment_transactions where order_id = ? and status = 'APPLIED'", orderId));
        send(get("/api/orders/" + code), u.accessToken()).andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paidAt").isNotEmpty()).andExpect(jsonPath("$.payment").doesNotExist());
    }

    @Test
    void paidContentOpensAfterPurchaseAndLocksAgainWhenExpired() throws Exception {
        var c = content();
        long a = product("LEVEL", 1000, 30, 0, "ACTIVE", c.levelId);
        var u = buyer();
        lesson(c, null).andExpect(status().isForbidden());
        lesson(c, u).andExpect(status().isForbidden());
        send(get("/api/banks/" + c.bankId), u.accessToken()).andExpect(status().isForbidden());

        pay(orderCode(u, a), 1000).andExpect(status().isOk());
        lesson(c, u).andExpect(status().isOk()).andExpect(jsonPath("$.video.source").value("YOUTUBE"));
        send(get("/api/banks/" + c.bankId), u.accessToken()).andExpect(status().isOk());
        lesson(c, null).andExpect(status().isForbidden()); // quyền gắn với người mua, không mở cho khách

        jdbc.update("update user_course_enrollments set expires_at = ? where user_id = ?",
                OffsetDateTime.parse("2000-01-01T00:00:00Z"), u.id());
        lesson(c, u).andExpect(status().isForbidden());
        send(get("/api/banks/" + c.bankId), u.accessToken()).andExpect(status().isForbidden());
    }

    @Test
    void duplicateWebhookGrantsOnlyOnce() throws Exception {
        var c = content();
        long a = product("LEVEL", 1000, 30, 20, "ACTIVE", c.levelId);
        var u = buyer();
        String code = orderCode(u, a);
        pay(code, 1000, "REF-DUP-1").andExpect(jsonPath("$.result").value("APPLIED"));
        pay(code, 1000, "REF-DUP-1").andExpect(status().isOk()).andExpect(jsonPath("$.result").value("DUPLICATE"));
        assertEquals(20, balance(u));
        assertEquals(1, count("select count(*) from ai_quota_ledger where user_id = ?", u.id()));
        // cùng đơn nhưng mã giao dịch khác = khách trả thừa: ghi lại, không cấp lần hai
        pay(code, 1000, "REF-DUP-2").andExpect(status().isOk()).andExpect(jsonPath("$.result").value("ORDER_ALREADY_PAID"));
        assertEquals(20, balance(u));
        assertEquals(1, count("select count(*) from payment_transactions where provider_ref = 'REF-DUP-2' and status = 'ORDER_ALREADY_PAID'"));
    }

    @Test
    void concurrentDuplicateWebhooksGrantOnlyOnce() throws Exception {
        var c = content();
        long a = product("LEVEL", 1000, 30, 20, "ACTIVE", c.levelId);
        var u = buyer();
        String code = orderCode(u, a);
        var payload = fake.signedPaid(code, 1000, "REF-CONC-" + UUID.randomUUID());
        int n = 6;
        var pool = Executors.newFixedThreadPool(n);
        try {
            List<Callable<Integer>> calls = IntStream.range(0, n).<Callable<Integer>>mapToObj(i -> () ->
                    webhook("fake", payload).andReturn().getResponse().getStatus()).toList();
            for (var f : pool.invokeAll(calls)) {
                assertEquals(200, f.get());
            }
        } finally {
            pool.shutdown();
        }
        assertEquals(20, balance(u));
        assertEquals(1, count("select count(*) from ai_quota_ledger where user_id = ?", u.id()));
        assertEquals(1, count("select count(*) from user_course_enrollments where user_id = ?", u.id()));
        assertEquals(1, count("select count(*) from payment_transactions where provider_ref = ?", payload.get("reference").asText()));
    }

    @Test
    void amountMismatchRecordsButDoesNotActivate() throws Exception {
        var c = content();
        long a = product("LEVEL", 1000, 30, 20, "ACTIVE", c.levelId);
        var u = buyer();
        String code = orderCode(u, a);
        pay(code, 999, "REF-MISMATCH").andExpect(status().isOk()).andExpect(jsonPath("$.result").value("MISMATCH"));
        assertEquals("PENDING", orderStatus(code));
        assertEquals(0, balance(u));
        assertEquals(0, count("select count(*) from user_course_enrollments where user_id = ?", u.id()));
        assertEquals(1, count("select count(*) from payment_transactions where provider_ref = 'REF-MISMATCH' and status = 'MISMATCH'"));
    }

    @Test
    void unknownOrderAndCancelledOrderAreRecordedNotActivated() throws Exception {
        var c = content();
        long a = product("LEVEL", 1000, 30, 20, "ACTIVE", c.levelId);
        var u = buyer();
        pay("ORD-GHOST0", 1000, "REF-GHOST").andExpect(status().isOk()).andExpect(jsonPath("$.result").value("ORDER_NOT_FOUND"));
        assertEquals(1, count("select count(*) from payment_transactions where provider_ref = 'REF-GHOST' and order_id is null and status = 'ORDER_NOT_FOUND'"));

        String code = orderCode(u, a);
        jdbc.update("update orders set status = 'CANCELLED' where code = ?", code);
        pay(code, 1000, "REF-CANCELLED").andExpect(status().isOk()).andExpect(jsonPath("$.result").value("ORDER_CANCELLED"));
        assertEquals("CANCELLED", orderStatus(code));
        assertEquals(0, balance(u));
        assertEquals(0, count("select count(*) from user_course_enrollments where user_id = ?", u.id()));
        assertEquals(1, count("select count(*) from payment_transactions where provider_ref = 'REF-CANCELLED' and status = 'ORDER_CANCELLED'"));
    }

    @Test
    void expiredOrderIsStillActivatedBecauseMoneyArrived() throws Exception {
        var c = content();
        long a = product("LEVEL", 1000, 30, 20, "ACTIVE", c.levelId);
        var u = buyer();
        String code = orderCode(u, a);
        jdbc.update("update orders set status = 'EXPIRED' where code = ?", code);
        pay(code, 1000).andExpect(jsonPath("$.result").value("APPLIED"));
        assertEquals("PAID", orderStatus(code));
        assertEquals(20, balance(u));
        assertEquals(1, count("select count(*) from user_course_enrollments where user_id = ?", u.id()));
    }

    @Test
    void renewalStacksOnRemainingTimeAndKeepsOneRow() throws Exception {
        var c = content();
        long a = product("LEVEL", 1000, 180, 20, "ACTIVE", c.levelId);
        var u = buyer();
        pay(orderCode(u, a), 1000);
        OffsetDateTime first = jdbc.queryForObject("select expires_at from user_course_enrollments where user_id = ?", OffsetDateTime.class, u.id());
        pay(orderCode(u, a), 1000);
        OffsetDateTime second = jdbc.queryForObject("select expires_at from user_course_enrollments where user_id = ?", OffsetDateTime.class, u.id());
        assertEquals(Duration.ofDays(180), Duration.between(first, second));
        assertEquals(1, count("select count(*) from user_course_enrollments where user_id = ?", u.id()));
        assertEquals(40, balance(u));
    }

    @Test
    void renewalAfterExpiryRestartsFromNow() throws Exception {
        var c = content();
        long a = product("LEVEL", 1000, 30, 0, "ACTIVE", c.levelId);
        var u = buyer();
        pay(orderCode(u, a), 1000);
        jdbc.update("update user_course_enrollments set expires_at = ? where user_id = ?", OffsetDateTime.parse("2000-01-01T00:00:00Z"), u.id());
        pay(orderCode(u, a), 1000);
        OffsetDateTime exp = jdbc.queryForObject("select expires_at from user_course_enrollments where user_id = ?", OffsetDateTime.class, u.id());
        long days = Duration.between(Instant.now(), exp.toInstant()).toDays();
        assertTrue(days == 29 || days == 30, "hạn ~30 ngày từ bây giờ, nhận " + days);
    }

    @Test
    void productWithoutAccessDaysNeverExpires() throws Exception {
        var c = content();
        long a = product("LEVEL", 1000, null, 0, "ACTIVE", c.levelId);
        var u = buyer();
        pay(orderCode(u, a), 1000);
        assertEquals(1, count("select count(*) from user_course_enrollments where user_id = ? and expires_at is null", u.id()));
        lesson(c, u).andExpect(status().isOk());
    }

    @Test
    void aiAddonOnlyAddsQuota() throws Exception {
        var c = content();
        long addon = product("AI_ADDON", 99000, null, 20, "ACTIVE");
        var u = buyer();
        pay(orderCode(u, addon), 99000).andExpect(jsonPath("$.result").value("APPLIED"));
        assertEquals(20, balance(u));
        assertEquals(0, count("select count(*) from user_course_enrollments where user_id = ?", u.id()));
        lesson(c, u).andExpect(status().isForbidden());
    }

    @Test
    void comboOpensEveryLevel() throws Exception {
        var c1 = content();
        var c2 = content();
        long combo = product("COMBO", 890000, 365, 50, "ACTIVE", c1.levelId, c2.levelId);
        var u = buyer();
        pay(orderCode(u, combo), 890000);
        assertEquals(2, count("select count(*) from user_course_enrollments where user_id = ?", u.id()));
        lesson(c1, u).andExpect(status().isOk());
        lesson(c2, u).andExpect(status().isOk());
        assertEquals(50, balance(u));
    }

    @Test
    void expiryJobMovesOnlyOverduePendingOrders() throws Exception {
        var c = content();
        long a = product("LEVEL", 1000, 30, 0, "ACTIVE", c.levelId);
        var u = buyer();
        String overdue = orderCode(u, a);
        String fresh = orderCode(u, a);
        String paid = orderCode(u, a);
        pay(paid, 1000);
        jdbc.update("update orders set expires_at = ? where code in (?, ?)", OffsetDateTime.parse("2000-01-01T00:00:00Z"), overdue, paid);
        expiryJob.expire();
        assertEquals("EXPIRED", orderStatus(overdue));
        assertEquals("PENDING", orderStatus(fresh));
        assertEquals("PAID", orderStatus(paid));
    }

    @Test
    void devPaymentEndpointPaysOnlyOwnOrderThroughWebhookFlow() throws Exception {
        var c = content();
        long a = product("LEVEL", 1000, 30, 20, "ACTIVE", c.levelId);
        var owner = buyer();
        var other = buyer();
        String code = orderCode(owner, a);
        mvc.perform(MockMvcRequestBuilders.post("/api/dev/payments/" + code + "/pay")).andExpect(status().isUnauthorized());
        send(MockMvcRequestBuilders.post("/api/dev/payments/" + code + "/pay"), other.accessToken()).andExpect(status().isNotFound());
        assertEquals("PENDING", orderStatus(code));
        send(MockMvcRequestBuilders.post("/api/dev/payments/" + code + "/pay"), owner.accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
        assertEquals(20, balance(owner));
        assertEquals(1, count("select count(*) from payment_transactions where order_id = (select id from orders where code = ?) and provider = 'fake'", code));
    }
}
