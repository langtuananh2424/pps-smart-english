package com.pps.smartenglish.commerce;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.support.ApiTestBase;
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

/** D-75 (lịch sử đơn), D-77 (dùng lại đơn chờ), D-79 (simulatable), trạng thái hiệu lực và khóa học của dòng đơn. */
class OrderHistoryTest extends ApiTestBase {

    private static final OffsetDateTime PAST = OffsetDateTime.parse("2000-01-01T00:00:00Z");

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private record Course(long id, String slug, long levelId) {}

    private Course course() {
        String slug = "oh-" + UUID.randomUUID();
        long c = insert("courses", Map.of("slug", slug, "title", "Khóa " + slug, "status", "PUBLISHED"));
        return new Course(c, slug, insert("course_levels", Map.of("course_id", c, "code", "A", "title", "A", "status", "PUBLISHED")));
    }

    private long product(String type, long price, Course c) {
        var cols = new HashMap<String, Object>(Map.of("type", type, "name", "SP " + UUID.randomUUID(), "price_vnd", price, "status", "ACTIVE"));
        cols.put("access_days", 30);
        cols.put("ai_quota", null);
        long id = insert("products", cols);
        if (c != null) {
            jdbc.update("insert into product_levels (product_id, level_id) values (?, ?)", id, c.levelId());
        }
        return id;
    }

    private Account buyer() throws Exception {
        var u = register(newEmail(), "Password123!");
        jdbc.update("update users set email_verified_at = CURRENT_TIMESTAMP where id = ?", u.id());
        return u;
    }

    private ResultActions place(Account u, String key, long... ids) throws Exception {
        return send(MockMvcRequestBuilders.post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", key)
                .content(json.writeValueAsString(Map.of("productIds", java.util.Arrays.stream(ids).boxed().toList()))), u.accessToken());
    }

    private String code(Account u, long... ids) throws Exception {
        return json.readTree(place(u, UUID.randomUUID().toString(), ids).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString()).get("code").asText();
    }

    @Test
    void listIsOwnOnlyNewestFirstAndPaged() throws Exception {
        var c = course();
        var me = buyer();
        var other = buyer();
        long[] ps = IntStream.range(0, 3).mapToLong(i -> product("LEVEL", 1000 + i, c)).toArray();
        String c1 = code(me, ps[0]);
        String c2 = code(me, ps[1]);
        String c3 = code(me, ps[2]);
        code(other, ps[0]);

        mvc.perform(get("/api/me/orders")).andExpect(status().isUnauthorized());
        var first = send(get("/api/me/orders?limit=2"), me.accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].code").value(c3))
                .andExpect(jsonPath("$.data[1].code").value(c2))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].totalVnd").value(1002))
                .andExpect(jsonPath("$.data[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data[0].items[0].productId").value(ps[2]))
                .andExpect(jsonPath("$.data[0].items[0].name").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String next = json.readTree(first).get("nextCursor").asText();
        send(get("/api/me/orders?limit=2&cursor=" + next), me.accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].code").value(c1))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
        send(get("/api/me/orders?cursor=bad"), me.accessToken()).andExpect(status().isBadRequest());
    }

    @Test
    void overduePendingShowsExpiredWithoutWaitingForJob() throws Exception {
        var c = course();
        var u = buyer();
        String code = code(u, product("LEVEL", 1000, c));
        jdbc.update("update orders set expires_at = ? where code = ?", PAST, code);
        assertEquals("PENDING", jdbc.queryForObject("select status from orders where code = ?", String.class, code));
        send(get("/api/me/orders"), u.accessToken()).andExpect(jsonPath("$.data[0].status").value("EXPIRED"));
        send(get("/api/orders/" + code), u.accessToken()).andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.payment").doesNotExist()).andExpect(jsonPath("$.transferContent").doesNotExist());
    }

    @Test
    void sameProductSetReusesLivePendingOrderOtherwiseNewOne() throws Exception {
        var c = course();
        var u = buyer();
        long a = product("LEVEL", 1000, c);
        long b = product("LEVEL", 2000, c);
        var first = json.readTree(place(u, "k1", a).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        var again = json.readTree(place(u, "k2", a).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertEquals(first.get("code").asText(), again.get("code").asText());
        assertEquals(first.at("/payment/qrContent").asText(), again.at("/payment/qrContent").asText());
        assertEquals(first.at("/payment/expiresAt").asText(), again.at("/payment/expiresAt").asText());
        assertEquals(1, count("select count(*) from orders where user_id = ?", u.id()));

        assertEquals(false, code(u, a, b).equals(first.get("code").asText())); // tập khác thì đơn mới
        jdbc.update("update orders set expires_at = ? where code = ?", PAST, first.get("code").asText());
        assertEquals(false, code(u, a).equals(first.get("code").asText())); // đơn đã hết hạn thì không dùng lại
        assertEquals(3, count("select count(*) from orders where user_id = ?", u.id()));
    }

    @Test
    void concurrentRequestsWithDifferentKeysCreateOneOrder() throws Exception {
        var c = course();
        var u = buyer();
        long a = product("LEVEL", 1000, c);
        int n = 6;
        var pool = Executors.newFixedThreadPool(n);
        List<String> codes = new ArrayList<>();
        try {
            List<Callable<String>> calls = IntStream.range(0, n).<Callable<String>>mapToObj(i -> () -> {
                var res = place(u, UUID.randomUUID().toString(), a).andReturn().getResponse();
                return res.getStatus() + ":" + json.readTree(res.getContentAsString()).path("code").asText();
            }).toList();
            for (var f : pool.invokeAll(calls)) {
                codes.add(f.get());
            }
        } finally {
            pool.shutdown();
        }
        assertEquals(1, codes.stream().distinct().count(), "mọi request phải nhận cùng một đơn: " + codes);
        assertEquals(1, count("select count(*) from orders where user_id = ?", u.id()));
    }

    @Test
    void viewHasCourseTransferContentAndSimulatable() throws Exception {
        var c = course();
        var u = buyer();
        long level = product("LEVEL", 1000, c);
        long addon = product("AI_ADDON", 500, null);
        String code = code(u, level, addon);
        send(get("/api/orders/" + code), u.accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.transferContent").value("Thanh toan " + code))
                .andExpect(jsonPath("$.payment.simulatable").value(true))
                .andExpect(jsonPath("$.items[?(@.productId==" + level + ")].course.slug").value(org.hamcrest.Matchers.hasItem(c.slug())))
                .andExpect(jsonPath("$.items[?(@.productId==" + level + ")].course.id").value(org.hamcrest.Matchers.hasItem((int) c.id())))
                .andExpect(jsonPath("$.items[?(@.productId==" + addon + ")].course").value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.nullValue())));
        send(MockMvcRequestBuilders.post("/api/dev/payments/" + code + "/pay"), u.accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID")).andExpect(jsonPath("$.transferContent").doesNotExist());
    }
}
