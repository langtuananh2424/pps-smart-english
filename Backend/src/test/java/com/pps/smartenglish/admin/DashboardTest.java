package com.pps.smartenglish.admin;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.support.ApiTestBase;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.web.servlet.ResultActions;

/** FR-ADM-03. Mỗi test dùng một khoảng ngày trong quá khứ riêng để không lẫn với dữ liệu của test khác (DB H2 dùng chung). */
class DashboardTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private static OffsetDateTime at(String instant) {
        return OffsetDateTime.ofInstant(Instant.parse(instant), ZoneOffset.UTC);
    }

    private Account admin() throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("update users set role = 'ADMIN' where id = ?", u.id());
        return login(u.email(), PASSWORD); // role nằm trong JWT nên phải đăng nhập lại
    }

    private ResultActions dash(Account a, String query) throws Exception {
        var req = get("/api/admin/reports/dashboard" + query);
        return a == null ? mvc.perform(req) : send(req, a.accessToken());
    }

    private long user(String role, String status, String createdAt, boolean verified) {
        var cols = new HashMap<String, Object>(Map.of("email", "rep-" + UUID.randomUUID() + "@example.com", "role", role,
                "status", status, "created_at", at(createdAt)));
        cols.put("email_verified_at", verified ? at(createdAt) : null);
        return insert("users", cols);
    }

    private long student(String createdAt, boolean verified) {
        return user("STUDENT", "ACTIVE", createdAt, verified);
    }

    private long product(String type, long price) {
        var cols = new HashMap<String, Object>(Map.of("type", type, "name", "SP " + UUID.randomUUID(), "price_vnd", price, "status", "ACTIVE"));
        cols.put("access_days", null);
        cols.put("ai_quota", null);
        return insert("products", cols);
    }

    private long order(long userId, String status, String paidAt, long total) {
        var cols = new HashMap<String, Object>(Map.of("user_id", userId, "code", "ORD-" + UUID.randomUUID().toString().substring(0, 8),
                "status", status, "total_vnd", total, "expires_at", at("2099-01-01T00:00:00Z")));
        cols.put("paid_at", paidAt == null ? null : at(paidAt));
        return insert("orders", cols);
    }

    private void item(long orderId, long productId, long price) {
        insert("order_items", Map.of("order_id", orderId, "product_id", productId, "price_vnd", price));
    }

    private long job(long userId, String kind) {
        return insert("ai_jobs", Map.of("user_id", userId, "kind", kind));
    }

    private void usage(long jobId, long userId, String cost, int in, int out, int cacheRead, int cacheWrite, String createdAt) {
        insert("ai_grading_token_usage", Map.ofEntries(Map.entry("job_id", jobId), Map.entry("user_id", userId),
                Map.entry("provider", "fake"), Map.entry("model", "m"), Map.entry("input_tokens", in), Map.entry("output_tokens", out),
                Map.entry("cache_read_tokens", cacheRead), Map.entry("cache_write_tokens", cacheWrite),
                Map.entry("cost_usd", new BigDecimal(cost)), Map.entry("created_at", at(createdAt))));
    }

    @Test
    void onlyAdminsMayReadTheDashboard() throws Exception {
        dash(null, "").andExpect(status().isUnauthorized());
        var student = register(newEmail(), PASSWORD);
        dash(student, "").andExpect(status().isForbidden());
        dash(admin(), "").andExpect(status().isOk());
    }

    @Test
    void defaultsToTheLast30DaysInVietnamTime() throws Exception {
        var admin = admin();
        LocalDate today = LocalDate.now(VN);
        dash(admin, "").andExpect(status().isOk()).andExpect(jsonPath("$.from").value(today.minusDays(29).toString()))
                .andExpect(jsonPath("$.to").value(today.toString())).andExpect(jsonPath("$.timezone").value("Asia/Ho_Chi_Minh"))
                .andExpect(jsonPath("$.generatedAt").isNotEmpty());
        dash(admin, "?from=2001-01-01").andExpect(jsonPath("$.from").value("2001-01-01")).andExpect(jsonPath("$.to").value("2001-01-30"));
        dash(admin, "?to=2001-02-28").andExpect(jsonPath("$.from").value("2001-01-30")).andExpect(jsonPath("$.to").value("2001-02-28"));
    }

    @Test
    void invalidRangesAndDatesAreRejected() throws Exception {
        var admin = admin();
        dash(admin, "?from=2001-02-02&to=2001-02-01").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_failed"));
        dash(admin, "?from=2001-01-01&to=2002-01-02").andExpect(status().isBadRequest()); // 367 ngày
        dash(admin, "?from=2001-01-01&to=2002-01-01").andExpect(status().isOk()); // đúng 366 ngày
        for (String bad : new String[] {"?from=2001-13-01", "?from=abc", "?to=2001-1-1", "?from=2001-02-30&to=2001-03-01"}) {
            dash(admin, bad).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("validation_failed"));
        }
    }

    @Test
    void daysFollowVietnamTimeAtBothEdgesOfThePeriod() throws Exception {
        var admin = admin();
        student("2002-03-10T16:59:59Z", false); // 23:59:59 ngày 10 giờ VN
        student("2002-03-10T17:00:00Z", false); // 00:00:00 ngày 11
        student("2002-03-11T17:30:00Z", false); // 00:30 ngày 12: UTC còn là ngày 11
        student("2002-03-12T16:59:59Z", false); // 23:59:59 ngày 12
        student("2002-03-12T17:00:00Z", false); // 00:00 ngày 13: ngoài kỳ

        dash(admin, "?from=2002-03-11&to=2002-03-12").andExpect(status().isOk())
                .andExpect(jsonPath("$.users.registered").value(3))
                .andExpect(jsonPath("$.users.daily", hasSize(2)))
                .andExpect(jsonPath("$.users.daily[0].date").value("2002-03-11")).andExpect(jsonPath("$.users.daily[0].registered").value(1))
                .andExpect(jsonPath("$.users.daily[1].date").value("2002-03-12")).andExpect(jsonPath("$.users.daily[1].registered").value(2));
        dash(admin, "?from=2002-03-10&to=2002-03-10").andExpect(jsonPath("$.users.registered").value(1));
        dash(admin, "?from=2002-03-13&to=2002-03-13").andExpect(jsonPath("$.users.registered").value(1));
    }

    @Test
    void registeredCountsEveryStudentStatusButNeverAdmins() throws Exception {
        var admin = admin();
        student("2003-05-10T03:00:00Z", true);
        student("2003-05-10T04:00:00Z", false);
        user("STUDENT", "LOCKED", "2003-05-20T03:00:00Z", true);
        user("ADMIN", "ACTIVE", "2003-05-10T05:00:00Z", true);

        dash(admin, "?from=2003-05-01&to=2003-05-31").andExpect(jsonPath("$.users.registered").value(3))
                .andExpect(jsonPath("$.users.verified").value(2))
                .andExpect(jsonPath("$.users.daily", hasSize(2))) // chỉ ngày có dữ liệu
                .andExpect(jsonPath("$.users.daily[0].date").value("2003-05-10")).andExpect(jsonPath("$.users.daily[0].registered").value(2))
                .andExpect(jsonPath("$.users.daily[0].verified").value(1))
                .andExpect(jsonPath("$.users.daily[1].date").value("2003-05-20"));
    }

    @Test
    void conversionSeparatesPaidFromLinkOnlyAndHandlesEmptyCohort() throws Exception {
        var admin = admin();
        long level = insert("course_levels", Map.of("course_id",
                insert("courses", Map.of("slug", "rep-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED")), "code", "A", "title", "L"));
        String created = "2004-06-05T03:00:00Z";
        long paid = student(created, true);
        long linkOnly = student(created, true);
        long pending = student(created, true);
        student(created, false); // chưa làm gì
        long both = student(created, true);
        order(paid, "PAID", "2004-06-06T03:00:00Z", 1000);
        order(pending, "PENDING", null, 1000);
        order(both, "PAID", Instant.now().toString(), 1000); // thanh toán sau kỳ vẫn tính cho cohort
        for (long u : new long[] {linkOnly, both}) {
            insert("user_course_enrollments", Map.of("user_id", u, "level_id", level, "starts_at", at(created)));
        }

        dash(admin, "?from=2004-06-01&to=2004-06-30").andExpect(jsonPath("$.conversion.cohortSize").value(5))
                .andExpect(jsonPath("$.conversion.paidUsers").value(2))
                .andExpect(jsonPath("$.conversion.paidRatePercent").value(40.0))
                .andExpect(jsonPath("$.conversion.activationLinkUsers").value(1)); // người có đơn PAID không bị tính là "chỉ link"
        dash(admin, "?from=2004-07-01&to=2004-07-31").andExpect(jsonPath("$.conversion.cohortSize").value(0))
                .andExpect(jsonPath("$.conversion.paidUsers").value(0)).andExpect(jsonPath("$.conversion.paidRatePercent").value(nullValue()))
                .andExpect(jsonPath("$.conversion.activationLinkUsers").value(0));
    }

    @Test
    void paidRateRoundsToTwoDecimals() throws Exception {
        var admin = admin();
        long a = student("2009-02-05T03:00:00Z", true);
        student("2009-02-05T03:00:00Z", true);
        student("2009-02-05T03:00:00Z", true);
        order(a, "PAID", "2009-02-06T03:00:00Z", 1000);
        dash(admin, "?from=2009-02-01&to=2009-02-28").andExpect(jsonPath("$.conversion.paidRatePercent").value(33.33));
    }

    @Test
    void revenueCountsOnlyPaidOrdersAtPurchasePriceSplitByProduct() throws Exception {
        var admin = admin();
        long buyer = student("2005-01-01T00:00:00Z", true);
        long p1 = product("LEVEL", 490000);
        long p2 = product("AI_ADDON", 99000);

        long o1 = order(buyer, "PAID", "2005-08-10T10:00:00Z", 490000); // 17:00 ngày 10 giờ VN
        item(o1, p1, 490000);
        long o2 = order(buyer, "PAID", "2005-08-10T20:00:00Z", 589000); // 03:00 ngày 11 giờ VN, nhiều dòng hàng
        item(o2, p1, 490000);
        item(o2, p2, 99000);
        item(order(buyer, "EXPIRED", null, 490000), p1, 490000);
        item(order(buyer, "PENDING", null, 490000), p1, 490000);
        item(order(buyer, "PAID", "2005-09-05T10:00:00Z", 490000), p1, 490000); // ngoài kỳ
        item(order(buyer, "CANCELLED", null, 99000), p2, 99000);
        jdbc.update("update products set price_vnd = 1 where id = ?", p1); // đổi giá sau khi bán không ảnh hưởng báo cáo

        dash(admin, "?from=2005-08-01&to=2005-08-31").andExpect(status().isOk())
                .andExpect(jsonPath("$.revenue.totalVnd").value(1079000))
                .andExpect(jsonPath("$.revenue.products", hasSize(2)))
                .andExpect(jsonPath("$.revenue.products[0].productId").value(p1))
                .andExpect(jsonPath("$.revenue.products[0].type").value("LEVEL"))
                .andExpect(jsonPath("$.revenue.products[0].unitsSold").value(2))
                .andExpect(jsonPath("$.revenue.products[0].revenueVnd").value(980000))
                .andExpect(jsonPath("$.revenue.products[1].productId").value(p2))
                .andExpect(jsonPath("$.revenue.products[1].type").value("AI_ADDON"))
                .andExpect(jsonPath("$.revenue.products[1].unitsSold").value(1))
                .andExpect(jsonPath("$.revenue.products[1].revenueVnd").value(99000))
                .andExpect(jsonPath("$.revenue.daily", hasSize(2)))
                .andExpect(jsonPath("$.revenue.daily[0].date").value("2005-08-10")).andExpect(jsonPath("$.revenue.daily[0].revenueVnd").value(490000))
                .andExpect(jsonPath("$.revenue.daily[1].date").value("2005-08-11")).andExpect(jsonPath("$.revenue.daily[1].revenueVnd").value(589000));
        dash(admin, "?from=2005-10-01&to=2005-10-31").andExpect(jsonPath("$.revenue.totalVnd").value(0))
                .andExpect(jsonPath("$.revenue.products", hasSize(0)));
    }

    @Test
    void aiCostGroupsByJobKindWithoutRateLeavesVndNull() throws Exception {
        var admin = admin();
        long who = student("2006-01-01T00:00:00Z", true);
        long writing = job(who, "WRITING_GRADE");
        long rewrite = job(who, "REWRITE_GRADE");
        usage(writing, who, "1.250000", 1000, 400, 50, 10, "2006-09-10T03:00:00Z");
        usage(writing, who, "0.500000", 500, 200, 25, 5, "2006-09-10T04:00:00Z"); // lần thử lại của cùng job
        usage(rewrite, who, "0.250000", 100, 40, 0, 0, "2006-09-11T03:00:00Z");
        usage(writing, who, "9.000000", 9, 9, 9, 9, "2006-10-02T03:00:00Z"); // ngoài kỳ

        dash(admin, "?from=2006-09-01&to=2006-09-30").andExpect(status().isOk())
                .andExpect(jsonPath("$.aiCost.totalUsd").value(2.0))
                .andExpect(jsonPath("$.aiCost.inputTokens").value(1600)).andExpect(jsonPath("$.aiCost.outputTokens").value(640))
                .andExpect(jsonPath("$.aiCost.cacheReadTokens").value(75)).andExpect(jsonPath("$.aiCost.cacheWriteTokens").value(15))
                .andExpect(jsonPath("$.aiCost.kinds", hasSize(2)))
                .andExpect(jsonPath("$.aiCost.kinds[0].kind").value("REWRITE_GRADE")).andExpect(jsonPath("$.aiCost.kinds[0].jobs").value(1))
                .andExpect(jsonPath("$.aiCost.kinds[0].costUsd").value(0.25))
                .andExpect(jsonPath("$.aiCost.kinds[1].kind").value("WRITING_GRADE")).andExpect(jsonPath("$.aiCost.kinds[1].jobs").value(1))
                .andExpect(jsonPath("$.aiCost.kinds[1].costUsd").value(1.75))
                .andExpect(jsonPath("$.aiCost.usdVndRate").value(nullValue())).andExpect(jsonPath("$.aiCost.totalVnd").value(nullValue()))
                .andExpect(jsonPath("$.aiCost.kinds[1].costVnd").value(nullValue()));
        dash(admin, "?from=2006-11-01&to=2006-11-30").andExpect(jsonPath("$.aiCost.totalUsd").value(0))
                .andExpect(jsonPath("$.aiCost.kinds", hasSize(0)));
    }

    @Test
    void redisFailureStillReturnsTheReport() throws Exception {
        // ApiTestBase đã làm redis.opsForValue() ném RedisConnectionFailureException cả khi đọc lẫn khi ghi
        var admin = admin();
        student("2010-04-10T03:00:00Z", true);
        dash(admin, "?from=2010-04-01&to=2010-04-30").andExpect(status().isOk()).andExpect(jsonPath("$.users.registered").value(1));
    }

    @Test
    void secondCallWithinTtlComesFromCacheWithoutRecomputing() throws Exception {
        var admin = admin();
        Map<String, String> store = new ConcurrentHashMap<>();
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(ops.get(anyString())).thenAnswer(inv -> store.get(inv.<String>getArgument(0)));
        doAnswer(inv -> store.put(inv.getArgument(0), inv.getArgument(1))).when(ops).set(anyString(), anyString(), any(Duration.class));
        doReturn(ops).when(redis).opsForValue();

        long who = student("2008-01-10T03:00:00Z", true);
        usage(job(who, "WRITING_GRADE"), who, "0.123456", 10, 20, 30, 40, "2008-01-12T03:00:00Z");
        order(who, "PAID", "2008-01-15T03:00:00Z", 1000);

        String query = "?from=2008-01-01&to=2008-01-31";
        String first = dash(admin, query).andExpect(status().isOk()).andExpect(jsonPath("$.users.registered").value(1))
                .andReturn().getResponse().getContentAsString();
        String key = "report:dashboard:2008-01-01:2008-01-31";
        verify(ops).set(eq(key), anyString(), eq(Duration.ofSeconds(60)));
        assertTrue(store.containsKey(key));

        student("2008-01-11T03:00:00Z", true); // đổi dữ liệu: nếu tính lại thì registered phải thành 2
        String second = dash(admin, query).andExpect(status().isOk()).andExpect(jsonPath("$.users.registered").value(1))
                .andReturn().getResponse().getContentAsString();

        // generatedAt, Instant, LocalDate, BigDecimal đọc lại từ cache phải y hệt bản tính trực tiếp
        JsonNode a = json.readTree(first);
        assertEquals(a, json.readTree(second));
        assertEquals(new BigDecimal("0.123456"), a.at("/aiCost/totalUsd").decimalValue().stripTrailingZeros().setScale(6));
        assertEquals(1, store.size());
        // khoảng khác thì khóa khác, tính lại
        dash(admin, "?from=2008-01-01&to=2008-01-30").andExpect(jsonPath("$.users.registered").value(2));
        assertEquals(2, store.size());
    }

    @Test
    void corruptCacheEntryFallsBackToDirectComputation() throws Exception {
        var admin = admin();
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(ops.get(anyString())).thenReturn("{not json");
        doReturn(ops).when(redis).opsForValue();
        student("2011-04-10T03:00:00Z", true);
        dash(admin, "?from=2011-04-01&to=2011-04-30").andExpect(status().isOk()).andExpect(jsonPath("$.users.registered").value(1));
    }
}
