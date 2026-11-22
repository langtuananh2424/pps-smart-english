package com.pps.smartenglish.media;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.media.service.MediaCleanupJob;
import com.pps.smartenglish.support.ApiTestBase;
import com.pps.smartenglish.support.FakeStorageConfig.FakeStorage;
import com.pps.smartenglish.support.MutableClockConfig;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** #65, D-134: tải ảnh, đổi WebP, gắn vào câu hỏi, thấy ở CMS và ở học viên; dọn file tạm quá hạn. */
@Import(MutableClockConfig.class)
class MediaApiTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";
    private static final String CMS = "/api/admin/cms";

    @Autowired FakeStorage storage;
    @Autowired MediaCleanupJob cleanup;
    @Autowired MutableClockConfig.MutableClock clock;

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
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
        return send(req, a.accessToken());
    }

    private static MockHttpServletRequestBuilder post(String url) {
        return MockMvcRequestBuilders.post(url);
    }

    private JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private static byte[] png(int w, int h) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    /** Xin quyền tải, "tải" lên kho giả đúng khóa trong policy, trả id ảnh và khóa tạm. */
    private record Uploaded(long id, String tmpKey) {}

    private Uploaded upload(Account a, byte[] bytes) throws Exception {
        var permit = body(call(post(CMS + "/media/images"), a, Map.of("contentType", "image/png")).andExpect(status().isCreated()));
        String key = permit.get("fields").get("key").asText();
        assertEquals(10_485_760L, storage.lastPost.maxBytes());
        assertEquals("image/png", storage.lastPost.contentType());
        storage.put(key, bytes);
        return new Uploaded(permit.get("assetId").asLong(), key);
    }

    private long questionInNewBank(Account admin) throws Exception {
        long c = insert("courses", Map.of("slug", "m-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "L", "status", "PUBLISHED"));
        long t = insert("topics", Map.of("level_id", lv, "title", "T", "is_free_trial", true, "status", "PUBLISHED"));
        long bank = body(call(post(CMS + "/topics/" + t + "/banks"), admin, Map.of("title", "Bộ", "isTrial", true)).andExpect(status().isCreated())).get("id").asLong();
        var mcq = Map.of("type", "CONNECTIVE_MCQ", "prompt", "Chọn từ nối",
                "choices", List.of(Map.of("label", "A", "content", "Therefore", "isCorrect", false),
                        Map.of("label", "B", "content", "However", "isCorrect", true)));
        long q = body(call(post(CMS + "/banks/" + bank + "/questions"), admin, mcq).andExpect(status().isCreated())).get("id").asLong();
        call(post(CMS + "/banks/" + bank + "/publish"), admin, null).andExpect(status().isOk());
        return q;
    }

    @Test
    void uploadIsConvertedToWebpAndAttachedWithItsOwnDisplaySize() throws Exception {
        var admin = withRole("ADMIN");
        var up = upload(admin, png(3200, 1600));

        var file = body(call(post(CMS + "/media/" + up.id + "/complete"), admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.width").value(1600)).andExpect(jsonPath("$.height").value(800)));
        assertTrue(file.get("url").asText().startsWith("http://fake-storage/media/images/"));
        assertFalse(file.get("url").asText().contains("tmp"));
        assertFalse(storage.objects.containsKey(up.tmpKey), "chỉ giữ bản đã xử lý, khóa tạm phải bị xóa");
        String finalKey = jdbc.queryForObject("select storage_key from media_assets where id = ?", String.class, up.id);
        assertEquals("image/webp", storage.objects.get(finalKey).contentType());
        // idempotent
        call(post(CMS + "/media/" + up.id + "/complete"), admin, null).andExpect(status().isOk()).andExpect(jsonPath("$.width").value(1600));

        long q = questionInNewBank(admin);
        var item = Map.of("assetId", up.id, "role", "DIAGRAM", "displaySize", "MD", "altText", "Sơ đồ khu vực");
        call(put(CMS + "/questions/" + q + "/media"), admin, Map.of("items", List.of(item))).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].displaySize").value("MD"))
                .andExpect(jsonPath("$[0].assetId").value(up.id)).andExpect(jsonPath("$[0].url").value(startsWith("http://fake-storage/")));

        long bank = jdbc.queryForObject("select bank_id from questions where id = ?", Long.class, q);
        call(get(CMS + "/banks/" + bank), admin, null).andExpect(jsonPath("$.questions[0].media[0].altText").value("Sơ đồ khu vực"));
        var student = login(register(newEmail(), PASSWORD).email(), PASSWORD);
        send(get("/api/banks/" + bank), student.accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].media[0].displaySize").value("MD"))
                .andExpect(jsonPath("$.questions[0].media[0].width").value(1600));

        // thay bằng danh sách rỗng gỡ hết ảnh
        call(put(CMS + "/questions/" + q + "/media"), admin, Map.of("items", List.of())).andExpect(jsonPath("$", hasSize(0)));
        assertEquals(0, count("select count(*) from question_media where question_id = ?", q));
    }

    @Test
    void invalidUploadsAndAttachmentsAreRejected() throws Exception {
        var admin = withRole("ADMIN");
        call(post(CMS + "/media/images"), admin, Map.of("contentType", "application/pdf")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("image_type_unsupported"));

        var up = upload(admin, png(10, 10));
        call(post(CMS + "/media/" + up.id + "/complete"), admin, null).andExpect(status().isOk());

        var bad = upload(admin, "không phải ảnh".getBytes());
        call(post(CMS + "/media/" + bad.id + "/complete"), admin, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("image_invalid"));
        assertFalse(storage.objects.containsKey(bad.tmpKey));

        var missing = body(call(post(CMS + "/media/images"), admin, Map.of("contentType", "image/png")));
        call(post(CMS + "/media/" + missing.get("assetId").asLong() + "/complete"), admin, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("image_missing"));

        long q = questionInNewBank(admin);
        var item = Map.of("assetId", up.id, "role", "DIAGRAM", "displaySize", "MD", "altText", "x");
        call(put(CMS + "/questions/" + q + "/media"), admin, Map.of("items", List.of(item, item))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("duplicate_image"));
        call(put(CMS + "/questions/" + q + "/media"), admin, Map.of("items", List.of(Map.of("assetId", bad.id, "role", "DIAGRAM",
                "displaySize", "MD", "altText", "x")))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("media_not_ready"));
        call(put(CMS + "/questions/" + q + "/media"), admin, Map.of("items", List.of(Map.of("assetId", up.id, "role", "DIAGRAM",
                "displaySize", "HUGE", "altText", "x")))).andExpect(status().isBadRequest());
        call(put(CMS + "/questions/" + q + "/media"), admin, Map.of("items", List.of(Map.of("assetId", up.id, "role", "DIAGRAM",
                "displaySize", "MD", "altText", " ")))).andExpect(status().isBadRequest());
        call(put(CMS + "/questions/999999999/media"), admin, Map.of("items", List.of())).andExpect(status().isNotFound());
    }

    @Test
    void onlyTheUploaderCompletesAndStudentsCannotUseTheCms() throws Exception {
        var admin = withRole("ADMIN");
        var other = withRole("EDITOR");
        var up = upload(admin, png(10, 10));
        call(post(CMS + "/media/" + up.id + "/complete"), other, null).andExpect(status().isNotFound());
        var student = login(register(newEmail(), PASSWORD).email(), PASSWORD);
        call(post(CMS + "/media/images"), student, Map.of("contentType", "image/png")).andExpect(status().isForbidden());
    }

    @Test
    void staleUnconfirmedUploadsAreCleanedAndExpiredOnesCannotComplete() throws Exception {
        var admin = withRole("ADMIN");
        var up = upload(admin, png(10, 10));
        clock.set(Instant.now().plus(Duration.ofMinutes(16)));
        call(post(CMS + "/media/" + up.id + "/complete"), admin, null).andExpect(status().isGone());
        assertEquals(0, cleanup.tick(), "chưa qua expireAfter nên chưa dọn");
        clock.set(Instant.now().plus(Duration.ofHours(25)));
        assertEquals(1, cleanup.tick());
        clock.reset();
        assertFalse(storage.objects.containsKey(up.tmpKey));
        assertEquals(0, count("select count(*) from media_assets where id = ?", up.id));
    }
}
