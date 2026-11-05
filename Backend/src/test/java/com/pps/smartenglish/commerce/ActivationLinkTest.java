package com.pps.smartenglish.commerce;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.auth.service.TokenUtil;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

class ActivationLinkTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";

    private record Content(long levelId, long lessonId) {}

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private Content paidContent() {
        long c = insert("courses", Map.of("slug", "act-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A"));
        long t = insert("topics", Map.of("level_id", lv, "title", "Topic", "is_free_trial", false));
        var lesson = new HashMap<String, Object>(Map.of("topic_id", t, "type", "CONNECTIVE_VIDEO", "title", "Bài", "status", "PUBLISHED"));
        lesson.put("video_source", "YOUTUBE");
        lesson.put("video_ref", "abc");
        lesson.put("duration_sec", 60);
        return new Content(lv, insert("lessons", lesson));
    }

    private long product(String type, Integer days, Integer ai, String status, long... levelIds) {
        var cols = new HashMap<String, Object>(Map.of("type", type, "name", "SP " + UUID.randomUUID(), "price_vnd", 1000L, "status", status));
        cols.put("access_days", days);
        cols.put("ai_quota", ai);
        long id = insert("products", cols);
        for (long l : levelIds) {
            jdbc.update("insert into product_levels (product_id, level_id) values (?, ?)", id, l);
        }
        return id;
    }

    private Account student() throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("update users set email_verified_at = CURRENT_TIMESTAMP where id = ?", u.id());
        return u;
    }

    /** Role nằm trong JWT nên phải đăng nhập lại sau khi đổi role. */
    private Account admin() throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("update users set role = 'ADMIN' where id = ?", u.id());
        return login(u.email(), PASSWORD);
    }

    private ResultActions createLink(String token, Object productId, Object maxUses, Object expiresAt, String note) throws Exception {
        var body = new HashMap<String, Object>();
        body.put("productId", productId);
        body.put("maxUses", maxUses);
        body.put("expiresAt", expiresAt);
        body.put("note", note);
        var req = MockMvcRequestBuilders.post("/api/admin/activation-links").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body));
        return token == null ? mvc.perform(req) : send(req, token);
    }

    private static String future(Duration d) {
        return Instant.now().plus(d).toString();
    }

    /** Tạo link và trả mã thô lấy từ url (mã chỉ có ở đây). */
    private String newLink(Account admin, long productId, int maxUses) throws Exception {
        String body = createLink(admin.accessToken(), productId, maxUses, future(Duration.ofDays(7)), "test")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String url = json.readTree(body).get("url").asText();
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private ResultActions redeem(String linkToken, Account u) throws Exception {
        var req = MockMvcRequestBuilders.post("/api/activation-links/" + linkToken + "/redeem");
        return u == null ? mvc.perform(req) : send(req, u.accessToken());
    }

    private ResultActions lesson(Content c, Account u) throws Exception {
        return send(get("/api/lessons/" + c.lessonId), u.accessToken());
    }

    private int balance(Account u) {
        return count("select balance from user_ai_quota where user_id = ?", u.id());
    }

    private OffsetDateTime expiry(Account u) {
        return jdbc.queryForObject("select expires_at from user_course_enrollments where user_id = ?", OffsetDateTime.class, u.id());
    }

    @Test
    void onlyAdminCanManageLinks() throws Exception {
        var c = paidContent();
        long p = product("LEVEL", 30, 5, "ACTIVE", c.levelId);
        var student = student();
        var admin = admin();
        createLink(null, p, 1, future(Duration.ofDays(1)), null).andExpect(status().isUnauthorized());
        createLink(student.accessToken(), p, 1, future(Duration.ofDays(1)), null).andExpect(status().isForbidden());
        send(get("/api/admin/activation-links"), student.accessToken()).andExpect(status().isForbidden());
        send(MockMvcRequestBuilders.post("/api/admin/activation-links/1/revoke"), student.accessToken()).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/activation-links")).andExpect(status().isUnauthorized());
        createLink(admin.accessToken(), p, 1, future(Duration.ofDays(1)), "khuyến mãi").andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.url", containsString("/activate/")));
    }

    @Test
    void tokenIsNeverStoredRawOrListed() throws Exception {
        var c = paidContent();
        long p = product("LEVEL", 30, 5, "ACTIVE", c.levelId);
        var admin = admin();
        String token = newLink(admin, p, 2);
        assertEquals(0, count("select count(*) from activation_links where token_hash = ?", token));
        assertEquals(1, count("select count(*) from activation_links where token_hash = ?", TokenUtil.hash(token)));
        assertEquals(0, count("select count(*) from activation_links where note like ?", "%" + token + "%"));
        send(get("/api/admin/activation-links"), admin.accessToken()).andExpect(status().isOk())
                .andExpect(content().string(not(containsString(token))))
                .andExpect(content().string(not(containsString("tokenHash"))))
                .andExpect(content().string(not(containsString("url"))));
    }

    @Test
    void createValidatesInput() throws Exception {
        var c = paidContent();
        long ok = product("LEVEL", 30, 5, "ACTIVE", c.levelId);
        long off = product("LEVEL", 30, 5, "INACTIVE", c.levelId);
        var admin = admin();
        String t = admin.accessToken();
        for (ResultActions r : List.of(
                createLink(t, off, 1, future(Duration.ofDays(1)), null),
                createLink(t, 999999999L, 1, future(Duration.ofDays(1)), null),
                createLink(t, ok, 0, future(Duration.ofDays(1)), null),
                createLink(t, ok, 1, future(Duration.ofDays(-1)), null),
                createLink(t, ok, 1, null, null),
                createLink(t, null, 1, future(Duration.ofDays(1)), null))) {
            r.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("validation_failed"));
        }
        assertEquals(0, count("select count(*) from activation_links where product_id in (?, ?)", ok, off));
    }

    @Test
    void previewShowsProductAndRejectsBadExpiredAndRevokedTokens() throws Exception {
        var c = paidContent();
        long p = product("LEVEL", 180, 20, "ACTIVE", c.levelId);
        var admin = admin();
        String token = newLink(admin, p, 5);
        mvc.perform(get("/api/activation-links/" + token)).andExpect(status().isOk()) // công khai, không cần token
                .andExpect(jsonPath("$.productType").value("LEVEL"))
                .andExpect(jsonPath("$.accessDays").value(180))
                .andExpect(jsonPath("$.aiQuota").value(20))
                .andExpect(jsonPath("$.productName").isNotEmpty());

        mvc.perform(get("/api/activation-links/khong-co-ma-nay")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("activation_link_invalid"));

        String expired = newLink(admin, p, 5);
        jdbc.update("update activation_links set expires_at = ? where token_hash = ?", OffsetDateTime.parse("2000-01-01T00:00:00Z"), TokenUtil.hash(expired));
        mvc.perform(get("/api/activation-links/" + expired)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("activation_link_invalid"));

        String revoked = newLink(admin, p, 5);
        long id = jdbc.queryForObject("select id from activation_links where token_hash = ?", Long.class, TokenUtil.hash(revoked));
        send(MockMvcRequestBuilders.post("/api/admin/activation-links/" + id + "/revoke"), admin.accessToken()).andExpect(status().isOk());
        mvc.perform(get("/api/activation-links/" + revoked)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("activation_link_invalid"));
    }

    @Test
    void redeemGrantsAccessAndQuotaLikeAPurchaseButWithoutOrder() throws Exception {
        var c = paidContent();
        long p = product("LEVEL", 180, 20, "ACTIVE", c.levelId);
        var admin = admin();
        var u = student();
        String token = newLink(admin, p, 3);
        lesson(c, u).andExpect(status().isForbidden());

        redeem(token, u).andExpect(status().isOk()).andExpect(jsonPath("$.productName").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());

        lesson(c, u).andExpect(status().isOk());
        assertEquals(20, balance(u));
        long redemptionId = jdbc.queryForObject("select id from activation_redemptions where user_id = ?", Long.class, u.id());
        assertEquals(1, count("select count(*) from ai_quota_ledger where user_id = ? and reason = 'ACTIVATION_LINK' and delta = 20 and ref_id = ?", u.id(), redemptionId));
        assertEquals(0, count("select count(*) from ai_quota_ledger where user_id = ? and reason = 'PURCHASE'", u.id()));
        assertEquals(0, count("select count(*) from orders where user_id = ?", u.id()));
        assertEquals(1, count("select count(*) from user_course_enrollments where user_id = ? and order_id is null", u.id()));
        long days = Duration.between(Instant.now(), expiry(u).toInstant()).toDays();
        assertTrue(days == 179 || days == 180, "hạn ~180 ngày, nhận " + days);
        assertEquals(1, count("select used_count from activation_links where token_hash = ?", TokenUtil.hash(token)));
    }

    @Test
    void aiAddonLinkOnlyAddsQuota() throws Exception {
        var c = paidContent();
        long addon = product("AI_ADDON", null, 20, "ACTIVE");
        var u = student();
        redeem(newLink(admin(), addon, 1), u).andExpect(status().isOk()).andExpect(jsonPath("$.expiresAt").doesNotExist());
        assertEquals(20, balance(u));
        assertEquals(0, count("select count(*) from user_course_enrollments where user_id = ?", u.id()));
        lesson(c, u).andExpect(status().isForbidden());
    }

    @Test
    void redeemingAnotherLinkRenewsOnTopOfRemainingTime() throws Exception {
        var c = paidContent();
        long p = product("LEVEL", 180, 20, "ACTIVE", c.levelId);
        var admin = admin();
        var u = student();
        redeem(newLink(admin, p, 1), u).andExpect(status().isOk());
        OffsetDateTime first = expiry(u);
        redeem(newLink(admin, p, 1), u).andExpect(status().isOk());
        assertEquals(Duration.ofDays(180), Duration.between(first, expiry(u)));
        assertEquals(1, count("select count(*) from user_course_enrollments where user_id = ?", u.id()));
        assertEquals(40, balance(u));
    }

    @Test
    void sameAccountCannotRedeemTwiceAndExhaustedLinkIsRefused() throws Exception {
        var c = paidContent();
        long p = product("LEVEL", 30, 20, "ACTIVE", c.levelId);
        String token = newLink(admin(), p, 1);
        var u1 = student();
        var u2 = student();
        redeem(token, u1).andExpect(status().isOk());
        redeem(token, u1).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("activation_link_already_used"));
        assertEquals(20, balance(u1));
        redeem(token, u2).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("activation_link_exhausted"));
        assertEquals(0, balance(u2));
        assertEquals(0, count("select count(*) from activation_redemptions where user_id = ?", u2.id())); // rollback sạch
        assertEquals(1, count("select used_count from activation_links where token_hash = ?", TokenUtil.hash(token)));
    }

    @Test
    void concurrentUsersNeverExceedMaxUses() throws Exception {
        var c = paidContent();
        long p = product("LEVEL", 30, 20, "ACTIVE", c.levelId);
        String token = newLink(admin(), p, 3);
        int n = 8;
        List<Account> users = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            users.add(student());
        }
        var pool = Executors.newFixedThreadPool(n);
        List<Integer> statuses = new ArrayList<>();
        try {
            List<Callable<Integer>> calls = users.stream().<Callable<Integer>>map(u -> () -> redeem(token, u).andReturn().getResponse().getStatus()).toList();
            for (var f : pool.invokeAll(calls)) {
                statuses.add(f.get());
            }
        } finally {
            pool.shutdown();
        }
        assertEquals(3, statuses.stream().filter(s -> s == 200).count(), "kết quả: " + statuses);
        assertEquals(n - 3, statuses.stream().filter(s -> s == 409).count(), "kết quả: " + statuses);
        assertEquals(3, count("select used_count from activation_links where token_hash = ?", TokenUtil.hash(token)));
        assertEquals(3, count("select count(*) from activation_redemptions where link_id = (select id from activation_links where token_hash = ?)", TokenUtil.hash(token)));
    }

    @Test
    void sameUserClickingConcurrentlyIsGrantedOnce() throws Exception {
        var c = paidContent();
        long p = product("LEVEL", 30, 20, "ACTIVE", c.levelId);
        String token = newLink(admin(), p, 10);
        var u = student();
        int n = 6;
        var pool = Executors.newFixedThreadPool(n);
        List<Integer> statuses = new ArrayList<>();
        try {
            List<Callable<Integer>> calls = IntStream.range(0, n).<Callable<Integer>>mapToObj(i -> () -> redeem(token, u).andReturn().getResponse().getStatus()).toList();
            for (var f : pool.invokeAll(calls)) {
                statuses.add(f.get());
            }
        } finally {
            pool.shutdown();
        }
        assertEquals(1, statuses.stream().filter(s -> s == 200).count(), "kết quả: " + statuses);
        assertEquals(n - 1, statuses.stream().filter(s -> s == 409).count(), "kết quả: " + statuses);
        assertEquals(20, balance(u));
        assertEquals(1, count("select count(*) from ai_quota_ledger where user_id = ?", u.id()));
        assertEquals(1, count("select used_count from activation_links where token_hash = ?", TokenUtil.hash(token)));
    }

    @Test
    void redeemNeedsLoginAndVerifiedEmail() throws Exception {
        var c = paidContent();
        long p = product("LEVEL", 30, 20, "ACTIVE", c.levelId);
        String token = newLink(admin(), p, 5);
        redeem(token, null).andExpect(status().isUnauthorized());
        var unverified = register(newEmail(), PASSWORD);
        redeem(token, unverified).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("email_not_verified"));
        assertEquals(0, count("select used_count from activation_links where token_hash = ?", TokenUtil.hash(token)));
        redeem("khong-co-ma-nay", student()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("activation_link_invalid"));
    }

    @Test
    void revokeIsIdempotentStopsNewUseButKeepsGrantedAccess() throws Exception {
        var c = paidContent();
        long p = product("LEVEL", 30, 20, "ACTIVE", c.levelId);
        var admin = admin();
        String token = newLink(admin, p, 5);
        var early = student();
        var late = student();
        redeem(token, early).andExpect(status().isOk());
        long id = jdbc.queryForObject("select id from activation_links where token_hash = ?", Long.class, TokenUtil.hash(token));

        String first = send(MockMvcRequestBuilders.post("/api/admin/activation-links/" + id + "/revoke"), admin.accessToken())
                .andExpect(status().isOk()).andExpect(jsonPath("$.revokedAt").isNotEmpty()).andReturn().getResponse().getContentAsString();
        String second = send(MockMvcRequestBuilders.post("/api/admin/activation-links/" + id + "/revoke"), admin.accessToken())
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(json.readTree(first), json.readTree(second));
        send(MockMvcRequestBuilders.post("/api/admin/activation-links/999999999/revoke"), admin.accessToken()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("activation_link_not_found"));

        redeem(token, late).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("activation_link_invalid"));
        assertEquals(0, balance(late));
        lesson(c, early).andExpect(status().isOk()); // quyền đã cấp còn nguyên
        assertEquals(20, balance(early));
    }

    @Test
    void listIsNewestFirstPaginatedAndHasNoToken() throws Exception {
        var c = paidContent();
        long p = product("LEVEL", 30, 20, "ACTIVE", c.levelId);
        var admin = admin();
        for (int i = 0; i < 3; i++) {
            newLink(admin, p, 4);
        }
        JsonNode page1 = json.readTree(send(get("/api/admin/activation-links?limit=2"), admin.accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2))).andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andExpect(jsonPath("$.data[0].maxUses").value(4)).andExpect(jsonPath("$.data[0].usedCount").value(0))
                .andExpect(jsonPath("$.data[0].productName").isNotEmpty()).andExpect(jsonPath("$.data[0].revokedAt").value(nullValue()))
                .andReturn().getResponse().getContentAsString());
        assertTrue(page1.at("/data/0/id").asLong() > page1.at("/data/1/id").asLong());
        String cursor = page1.get("nextCursor").asText();
        send(get("/api/admin/activation-links?limit=2&cursor=" + cursor), admin.accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(org.hamcrest.Matchers.lessThan((int) page1.at("/data/1/id").asLong())));
        send(get("/api/admin/activation-links?limit=0"), admin.accessToken()).andExpect(status().isBadRequest());
        send(get("/api/admin/activation-links?cursor=%21%21"), admin.accessToken()).andExpect(status().isBadRequest());
    }
}
