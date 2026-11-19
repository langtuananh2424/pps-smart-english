package com.pps.smartenglish.commerce;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.support.ApiTestBase;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;

/** FR-COM-07, D-127: admin cấu hình số mức thời hạn của khóa, không cố định 6/12 tháng. */
class AdminProductTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private long level() {
        long c = insert("courses", Map.of("slug", "ap-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        return insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED"));
    }

    private Account admin() throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = 'ADMIN'", u.id());
        return login(u.email(), PASSWORD);
    }

    private org.springframework.test.web.servlet.ResultActions create(String token, Map<String, Object> body) throws Exception {
        return send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/admin/products").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)), token);
    }

    private Map<String, Object> levelProduct(long levelId, int days) {
        var b = new HashMap<String, Object>();
        b.put("type", "LEVEL");
        b.put("name", "Gói " + days + " ngày");
        b.put("priceVnd", 500_000);
        b.put("accessDays", days);
        b.put("levelIds", List.of(levelId));
        return b;
    }

    @Test
    void adminDefinesAnyNumberOfDurationsForOneLevel() throws Exception {
        var admin = admin();
        long lv = level();

        // 3 mức bất kỳ, không phải 180/365
        for (int days : new int[] {30, 90, 270}) {
            create(admin.accessToken(), levelProduct(lv, days)).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.accessDays").value(days)).andExpect(jsonPath("$.status").value("ACTIVE"));
        }
        mvc.perform(get("/api/products")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.levelIds[0]==" + lv + ")].accessDays", hasItem(30)))
                .andExpect(jsonPath("$[?(@.levelIds[0]==" + lv + ")].accessDays", hasItem(90)))
                .andExpect(jsonPath("$[?(@.levelIds[0]==" + lv + ")].accessDays", hasItem(270)));
    }

    @Test
    void rejectsDuplicateDurationAndInvalidShape() throws Exception {
        var admin = admin();
        long lv = level();
        create(admin.accessToken(), levelProduct(lv, 60)).andExpect(status().isCreated());
        create(admin.accessToken(), levelProduct(lv, 60)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("product_duplicate"));

        var noDays = levelProduct(lv, 10);
        noDays.remove("accessDays");
        create(admin.accessToken(), noDays).andExpect(status().isBadRequest());

        var combo = levelProduct(lv, 10);
        combo.put("levelIds", List.of(lv, level()));
        create(admin.accessToken(), combo).andExpect(status().isBadRequest());

        var badLevel = levelProduct(999_999L, 10);
        create(admin.accessToken(), badLevel).andExpect(status().isBadRequest());

        var addon = new HashMap<String, Object>(Map.of("type", "AI_ADDON", "name", "Lượt", "priceVnd", 1000, "aiQuota", 0));
        create(admin.accessToken(), addon).andExpect(status().isBadRequest());
    }

    @Test
    void priceIsAlwaysEditableButDurationOnlyBeforeFirstUse() throws Exception {
        var admin = admin();
        long lv = level();
        long id = create(admin.accessToken(), levelProduct(lv, 45)).andReturn().getResponse().getContentAsString()
                .transform(s -> {
                    try {
                        return json.readTree(s).get("id").asLong();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                });

        // chưa dùng: đổi thời hạn được
        send(patch("/api/admin/products/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"accessDays\":50}"),
                admin.accessToken()).andExpect(status().isOk()).andExpect(jsonPath("$.accessDays").value(50));

        // có link kích hoạt dùng gói: đổi thời hạn bị chặn, đổi giá và tắt vẫn được
        jdbc.update("insert into activation_links (token_hash, product_id, max_uses, expires_at, created_by) "
                + "values (?, ?, 1, TIMESTAMP '2099-01-01 00:00:00', ?)", UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", ""), id, admin.id());
        send(patch("/api/admin/products/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"accessDays\":99}"),
                admin.accessToken()).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("product_in_use"));
        send(patch("/api/admin/products/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"priceVnd\":123000,\"status\":\"INACTIVE\"}"), admin.accessToken())
                .andExpect(status().isOk()).andExpect(jsonPath("$.priceVnd").value(123000))
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        // gói đã tắt không còn bán và không chặn tạo gói mới cùng thời hạn
        create(admin.accessToken(), levelProduct(lv, 50)).andExpect(status().isCreated());
    }

    @Test
    void onlyProductManageCanUseAdminProducts() throws Exception {
        var u = register(newEmail(), PASSWORD);
        send(get("/api/admin/products"), login(u.email(), PASSWORD).accessToken()).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/products")).andExpect(status().isUnauthorized());
    }
}
