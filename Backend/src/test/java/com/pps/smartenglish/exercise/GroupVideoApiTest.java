package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.support.FakeStorageConfig.FakeStorage;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** D-156: tải video mp4 (MIME, chữ ký, dung lượng, quyền), link YouTube và gắn video vào nhóm câu. */
class GroupVideoApiTest extends ListeningTestSupport {

    private static final String CMS = "/api/admin/cms";
    private static final String YT = "dQw4w9WgXcQ";

    @Autowired FakeStorage storage;

    @AfterEach
    void dropPendingVideo() {
        jdbc.update("delete from media_assets where kind = 'VIDEO' and status = 'PENDING'");
    }

    private ResultActions call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req, Account a, Object body)
            throws Exception {
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return send(req, a.accessToken());
    }

    /** ftyp isom + moov{mvhd v0, timescale 1000, thời lượng 100 s}; moov ở cuối file như video chưa tối ưu streaming. */
    private static byte[] mp4() {
        return mp4(true);
    }

    private static byte[] mp4(boolean withMoov) {
        var b = java.nio.ByteBuffer.allocate(24 + 8 + 28 + 4);
        b.putInt(20).put("ftyp".getBytes(StandardCharsets.US_ASCII)).put("isom".getBytes(StandardCharsets.US_ASCII)).putInt(0)
                .put("isom".getBytes(StandardCharsets.US_ASCII));
        b.putInt(withMoov ? 36 : 8).put((withMoov ? "moov" : "free").getBytes(StandardCharsets.US_ASCII));
        if (withMoov) {
            b.putInt(28).put("mvhd".getBytes(StandardCharsets.US_ASCII)).putInt(0).putInt(0).putInt(0).putInt(1000).putInt(100_000);
        }
        return java.util.Arrays.copyOf(b.array(), b.position());
    }

    private JsonNode permit(Account a) throws Exception {
        return json.readTree(call(MockMvcRequestBuilders.post(CMS + "/media/video"), a, Map.of("contentType", "video/mp4"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private long uploadVideo(Account a) throws Exception {
        var p = permit(a);
        storage.put(p.at("/fields/key").asText(), mp4());
        long id = p.get("assetId").asLong();
        call(MockMvcRequestBuilders.post(CMS + "/media/" + id + "/complete-video"), a, null).andExpect(status().isOk());
        return id;
    }

    private ResultActions patchGroup(Account a, long group, Map<String, Object> body) throws Exception {
        return call(MockMvcRequestBuilders.patch(CMS + "/groups/" + group), a, body);
    }

    @Test
    void uploadChecksMimeSignatureSizeAndRole() throws Exception {
        var admin = withRole("ADMIN");
        call(MockMvcRequestBuilders.post(CMS + "/media/video"), admin, Map.of("contentType", "video/webm")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("video_type_unsupported"));
        call(MockMvcRequestBuilders.post(CMS + "/media/video"), student(0), Map.of("contentType", "video/mp4")).andExpect(status().isForbidden());
        var p = permit(admin);
        assertEquals(104857600L, p.get("maxBytes").asLong(), "mặc định 100 MB, cấu hình được");

        long id = uploadVideo(admin);
        assertEquals("VIDEO", jdbc.queryForObject("select kind from media_assets where id = ?", String.class, id));
        assertEquals("READY", jdbc.queryForObject("select status from media_assets where id = ?", String.class, id));
        assertEquals(100_000, jdbc.queryForObject("select duration_ms from media_assets where id = ?", Integer.class, id), "thời lượng đọc từ mvhd");

        var noMoov = permit(admin);
        storage.put(noMoov.at("/fields/key").asText(), mp4(false));
        call(MockMvcRequestBuilders.post(CMS + "/media/" + noMoov.get("assetId").asLong() + "/complete-video"), admin, null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("video_duration_unreadable"));
        assertTrue(storage.head(noMoov.at("/fields/key").asText()).isEmpty(), "file không đọc được thời lượng bị xóa");
        call(MockMvcRequestBuilders.post(CMS + "/media/" + id + "/complete-video"), admin, null).andExpect(status().isOk()); // idempotent
        call(MockMvcRequestBuilders.post(CMS + "/media/" + id + "/complete-video"), withRole("EDITOR"), null).andExpect(status().isNotFound());

        var bad = permit(admin);
        long badId = bad.get("assetId").asLong();
        call(MockMvcRequestBuilders.post(CMS + "/media/" + badId + "/complete-video"), admin, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("video_missing"));
        String key = bad.at("/fields/key").asText();
        storage.put(key, "not a video at all, just text".getBytes(StandardCharsets.UTF_8));
        call(MockMvcRequestBuilders.post(CMS + "/media/" + badId + "/complete-video"), admin, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("video_invalid"));
        assertTrue(storage.head(key).isEmpty(), "file sai chữ ký bị xóa khỏi kho");

        var m4a = permit(admin);
        byte[] audioBytes = mp4();
        System.arraycopy("M4A ".getBytes(StandardCharsets.US_ASCII), 0, audioBytes, 8, 4);
        storage.put(m4a.at("/fields/key").asText(), audioBytes);
        call(MockMvcRequestBuilders.post(CMS + "/media/" + m4a.get("assetId").asLong() + "/complete-video"), admin, null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("video_invalid"));

        // giới hạn nhỏ: hạ max_bytes của lượt tải để mô phỏng cấu hình nhỏ
        var big = permit(admin);
        jdbc.update("update media_assets set max_bytes = 10 where id = ?", big.get("assetId").asLong());
        storage.put(big.at("/fields/key").asText(), mp4());
        call(MockMvcRequestBuilders.post(CMS + "/media/" + big.get("assetId").asLong() + "/complete-video"), admin, null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("video_too_large"));
    }

    @Test
    void groupHoldsOneVideoSourceAndStudentsSeeIt() throws Exception {
        var admin = withRole("ADMIN");
        var w = world("FREE", null);
        long video = uploadVideo(admin);

        patchGroup(admin, w.g1(), Map.of("videoAssetId", video)).andExpect(status().isOk()).andExpect(jsonPath("$.videoAssetId").value(video));
        var file = get("/api/banks/" + w.bank(), student(0), 200).get("groups").get(0);
        assertTrue(file.get("videoUrl").asText().contains("media/video/"));
        assertTrue(file.get("videoYoutubeId").isNull());

        // cả hai cùng đặt -> 400, không đổi dữ liệu
        patchGroup(admin, w.g1(), Map.of("videoYoutube", "https://youtu.be/" + YT)).andExpect(status().isBadRequest());
        // đổi nguồn trong một PATCH thì được
        var swap = new HashMap<String, Object>();
        swap.put("videoAssetId", null);
        swap.put("videoYoutube", "https://www.youtube.com/watch?v=" + YT);
        patchGroup(admin, w.g1(), swap).andExpect(status().isOk()).andExpect(jsonPath("$.videoYoutubeId").value(YT));
        var yt = get("/api/banks/" + w.bank(), student(0), 200).get("groups").get(0);
        assertEquals(YT, yt.get("videoYoutubeId").asText());
        assertTrue(yt.get("videoUrl").isNull());

        patchGroup(admin, w.g1(), Map.of("videoYoutube", "https://evil.example.com/watch?v=" + YT)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.videoYoutube").exists());
        // tạo nhóm với cả hai -> 400; id audio không phải video -> 400
        call(MockMvcRequestBuilders.post(CMS + "/banks/" + w.bank() + "/groups"), admin,
                Map.of("videoAssetId", video, "videoYoutube", YT)).andExpect(status().isBadRequest());
        patchGroup(admin, w.g2(), Map.of("videoAssetId", audioAsset(1000))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("media_not_ready"));
        // học viên không được sửa nhóm
        patchGroup(student(0), w.g1(), Map.of("videoYoutube", YT)).andExpect(status().isForbidden());
    }
}
