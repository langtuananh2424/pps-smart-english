package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.support.FakeStorageConfig.FakeStorage;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** #69: CMS audio và cấu hình (tải audio, gắn vào part, chế độ phát, luật Nghe ép) và ghi đè khi giao bài (FR-ASG-01). */
class ListeningCmsApiTest extends ListeningTestSupport {

    private static final String CMS = "/api/admin/cms";

    @Autowired FakeStorage storage;

    /** Audio chưa xác nhận còn lại trong DB dùng chung sẽ bị MediaCleanupJob của test khác đếm vào. */
    @org.junit.jupiter.api.AfterEach
    void dropPendingAudio() {
        jdbc.update("delete from media_assets where kind = 'AUDIO' and status = 'PENDING'");
    }

    private ResultActions call(MockHttpServletRequestBuilder req, Account a, Object body) throws Exception {
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(body instanceof String s ? s : json.writeValueAsString(body));
        }
        return send(req, a.accessToken());
    }

    private static MockHttpServletRequestBuilder put(String url) {
        return MockMvcRequestBuilders.put(url);
    }

    private static MockHttpServletRequestBuilder get(String url) {
        return MockMvcRequestBuilders.get(url);
    }

    private static MockHttpServletRequestBuilder patch(String url) {
        return MockMvcRequestBuilders.patch(url);
    }

    private static MockHttpServletRequestBuilder post(String url) {
        return MockMvcRequestBuilders.post(url);
    }

    private static byte[] mp3() {
        byte[] b = new byte[64];
        System.arraycopy("ID3".getBytes(StandardCharsets.US_ASCII), 0, b, 0, 3);
        return b;
    }

    private long upload(Account admin, byte[] bytes, Integer durationMs) throws Exception {
        var permit = json.readTree(call(post(CMS + "/media/audio"), admin, Map.of("contentType", "audio/mpeg")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        storage.put(permit.at("/fields/key").asText(), bytes);
        var body = new HashMap<String, Object>();
        body.put("durationMs", durationMs);
        long id = permit.get("assetId").asLong();
        call(post(CMS + "/media/" + id + "/complete-audio"), admin, body).andExpect(status().isOk());
        return id;
    }

    @Test
    void audioUploadValidatesTypeSignatureSizeAndOwner() throws Exception {
        var admin = withRole("ADMIN");
        call(post(CMS + "/media/audio"), admin, Map.of("contentType", "video/mp4")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("audio_type_unsupported"));
        call(post(CMS + "/media/audio"), student(0), Map.of("contentType", "audio/mpeg")).andExpect(status().isForbidden());

        long id = upload(admin, mp3(), 5000);
        assertEquals("READY", jdbc.queryForObject("select status from media_assets where id = ?", String.class, id));
        assertEquals("AUDIO", jdbc.queryForObject("select kind from media_assets where id = ?", String.class, id));
        assertEquals(5000, count("select duration_ms from media_assets where id = ?", id));
        call(post(CMS + "/media/" + id + "/complete-audio"), admin, null).andExpect(status().isOk()); // idempotent
        call(post(CMS + "/media/" + id + "/complete-audio"), withRole("EDITOR"), null).andExpect(status().isNotFound()); // chỉ người tải lên

        var permit = json.readTree(call(post(CMS + "/media/audio"), admin, Map.of("contentType", "audio/mpeg")).andReturn().getResponse().getContentAsString());
        long pending = permit.get("assetId").asLong();
        call(post(CMS + "/media/" + pending + "/complete-audio"), admin, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("audio_missing"));
        String key = permit.at("/fields/key").asText();
        storage.put(key, "not an audio file at all".getBytes(StandardCharsets.UTF_8));
        call(post(CMS + "/media/" + pending + "/complete-audio"), admin, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("audio_invalid"));
        assertTrue(storage.head(key).isEmpty(), "file sai chữ ký bị xóa khỏi kho");

        var big = json.readTree(call(post(CMS + "/media/audio"), admin, Map.of("contentType", "audio/mpeg")).andReturn().getResponse().getContentAsString());
        long bigId = big.get("assetId").asLong();
        jdbc.update("update media_assets set max_bytes = 10 where id = ?", bigId);
        storage.put(big.at("/fields/key").asText(), mp3());
        call(post(CMS + "/media/" + bigId + "/complete-audio"), admin, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("audio_too_large"));

        var dur = json.readTree(call(post(CMS + "/media/audio"), admin, Map.of("contentType", "audio/mpeg")).andReturn().getResponse().getContentAsString());
        storage.put(dur.at("/fields/key").asText(), mp3());
        call(post(CMS + "/media/" + dur.get("assetId").asLong() + "/complete-audio"), admin, Map.of("durationMs", 0)).andExpect(status().isBadRequest());
    }

    @Test
    void groupLinksReadyAudioOnlyAndKeepsScriptAwayFromStudents() throws Exception {
        var admin = withRole("ADMIN");
        var w = world("FREE", null);
        long audio = upload(admin, mp3(), 60000);
        long image = insert("media_assets", Map.of("kind", "IMAGE", "status", "READY", "storage_key", "media/images/" + UUID.randomUUID(),
                "mime", "image/webp", "size_bytes", 10L, "width", 10, "height", 10, "uploaded_by", admin.id()));

        var created = json.readTree(call(post(CMS + "/banks/" + w.bank() + "/groups"), admin,
                Map.of("title", "Part 3", "audioAssetId", audio, "transcriptHtml", "<p>Xin chao <script>x()</script></p>"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.audioAssetId").value(audio)).andExpect(jsonPath("$.audioDurationMs").value(60000))
                .andReturn().getResponse().getContentAsString());
        assertEquals("<p>Xin chao </p>", created.get("transcriptHtml").asText());
        long gid = created.get("id").asLong();

        call(post(CMS + "/banks/" + w.bank() + "/groups"), admin, Map.of("title", "x", "audioAssetId", image)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("media_not_ready")); // ảnh không phải audio
        call(patch(CMS + "/groups/" + gid), admin, Map.of("audioAssetId", 999999)).andExpect(status().isBadRequest());
        call(patch(CMS + "/groups/" + gid), admin, "{\"audioAssetId\":\"abc\"}").andExpect(status().isBadRequest());
        call(patch(CMS + "/groups/" + gid), admin, Map.of("title", "Part 3b")).andExpect(status().isOk()).andExpect(jsonPath("$.audioAssetId").value(audio));
        call(patch(CMS + "/groups/" + gid), admin, "{\"audioAssetId\":null}").andExpect(status().isOk()).andExpect(jsonPath("$.audioAssetId").doesNotExist());

        // script của câu: sửa được, chuỗi rỗng là xóa
        call(patch(CMS + "/questions/" + w.q()[0]), admin, Map.of("transcriptExcerpt", "Script câu 0")).andExpect(status().isOk())
                .andExpect(jsonPath("$.transcriptExcerpt").value("Script câu 0"));
        call(patch(CMS + "/questions/" + w.q()[0]), admin, Map.of("transcriptExcerpt", " ")).andExpect(status().isOk())
                .andExpect(jsonPath("$.transcriptExcerpt").doesNotExist());
    }

    @Test
    void bankPlaybackModeAndForcedRuleAreConfigurableWithinLimits() throws Exception {
        var admin = withRole("ADMIN");
        var w = world("FREE", null);
        var url = CMS + "/banks/" + w.bank() + "/forced-listening";

        call(get(url), admin, null).andExpect(status().isOk()).andExpect(jsonPath("$.ruleId").doesNotExist())
                .andExpect(jsonPath("$.playLimit").value(3)).andExpect(jsonPath("$.passPctTotal").value(70))
                .andExpect(jsonPath("$.hintEveryN").value(3)).andExpect(jsonPath("$.wrongThreshold").value(3)); // mặc định D-138
        call(put(url), admin, Map.of("playLimit", 2)).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("forced_listening_off"));
        call(get(CMS + "/banks/999999/forced-listening"), admin, null).andExpect(status().isNotFound());

        call(patch(CMS + "/banks/" + w.bank()), admin, Map.of("playbackMode", "SLOW")).andExpect(status().isBadRequest());
        call(patch(CMS + "/banks/" + w.bank()), admin, Map.of("playLimit", 11)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.playLimit").exists());
        call(patch(CMS + "/banks/" + w.bank()), admin, Map.of("playbackMode", "LOCKED", "playLimit", 4)).andExpect(status().isOk())
                .andExpect(jsonPath("$.playbackMode").value("LOCKED")).andExpect(jsonPath("$.playLimit").value(4));

        call(put(url), admin, Map.of("playLimit", 2, "passPctTotal", 80)).andExpect(status().isOk()).andExpect(jsonPath("$.thresholdMode").value("TOTAL")).andExpect(jsonPath("$.ruleId").exists())
                .andExpect(jsonPath("$.playLimit").value(2)).andExpect(jsonPath("$.passPctTotal").value(80)).andExpect(jsonPath("$.hintEveryN").value(3));
        call(put(url), admin, Map.of("wrongThreshold", 5, "groupThresholds", List.of(Map.of("groupId", w.g1(), "passPct", 60))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.playLimit").value(2)).andExpect(jsonPath("$.wrongThreshold").value(5))
                .andExpect(jsonPath("$.groupThresholds[0].passPct").value(60)).andExpect(jsonPath("$.thresholdMode").value("PART"));
        call(put(url), admin, Map.of("passPctTotal", 60, "groupThresholds", List.of(Map.of("groupId", w.g1(), "passPct", 60))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("threshold_conflict"));
        call(put(url), admin, Map.of("passPctTotal", 65)).andExpect(status().isOk()).andExpect(jsonPath("$.groupThresholds").isEmpty())
                .andExpect(jsonPath("$.thresholdMode").value("TOTAL")); // đặt cả bài thì bỏ ngưỡng part
        call(put(url), admin, Map.of("groupThresholds", List.of(Map.of("groupId", w.g1(), "passPct", 60)))).andExpect(status().isOk());
        call(put(url), admin, Map.of("hintEveryN", 4)).andExpect(status().isOk()).andExpect(jsonPath("$.groupThresholds[0].groupId").value(w.g1())); // null = giữ
        call(put(url), admin, Map.of("groupThresholds", List.of())).andExpect(status().isOk()).andExpect(jsonPath("$.groupThresholds").isEmpty())
                .andExpect(jsonPath("$.thresholdMode").value("TOTAL"));
        assertEquals(1, count("select count(*) from forced_listening_rules where bank_id = ?", w.bank()), "PUT lặp không tạo thêm dòng luật");

        call(put(url), admin, Map.of("playLimit", 11)).andExpect(status().isBadRequest());
        call(put(url), admin, Map.of("passPctTotal", 101)).andExpect(status().isBadRequest());
        call(put(url), admin, Map.of("hintEveryN", 0)).andExpect(status().isBadRequest());
        call(put(url), admin, Map.of("groupThresholds", List.of(Map.of("groupId", world("FREE", null).g1(), "passPct", 50)))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.groupThresholds").exists()); // part của bộ khác
        call(put(url), admin, Map.of("groupThresholds", List.of(Map.of("groupId", w.g1(), "passPct", 50), Map.of("groupId", w.g1(), "passPct", 60))))
                .andExpect(status().isBadRequest());
        call(put(url), student(0), Map.of("playLimit", 2)).andExpect(status().isForbidden());

        var placement = json.readTree(call(post(CMS + "/placement/banks"), admin, Map.of("title", "Đề đầu vào", "timeLimitSec", 600))
                .andReturn().getResponse().getContentAsString());
        call(patch(CMS + "/banks/" + placement.get("id").asLong()), admin, Map.of("playbackMode", "LOCKED")).andExpect(status().isBadRequest());
    }

    @Test
    void teacherOverridesForcedRuleWhenAssigningAndSnapshotSurvivesBankEdits() throws Exception {
        var admin = withRole("ADMIN");
        var w = world("LOCKED", null);
        jdbc.update("update courses set delivery_mode = 'CLASS_BASED' where id = ?", w.course());
        put(CMS + "/banks/" + w.bank() + "/forced-listening", admin, Map.of("playLimit", 3, "passPctTotal", 70, "hintEveryN", 3)).andExpect(status().isOk());
        long cls = insert("classes", Map.of("course_id", w.course(), "name", "Lớp nghe", "starts_on", LocalDate.now().minusDays(1),
                "ends_on", LocalDate.now().plusDays(60), "mode", "ONLINE", "capacity", 20));
        var teacher = withRole("TEACHER");
        jdbc.update("insert into class_staff (class_id, user_id, role, assigned_from) values (?, ?, 'TEACHER', ?)", cls, teacher.id(), LocalDate.now().minusDays(1));
        var s = student(0);
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', "
                + "TIMESTAMP '2020-01-01 00:00:00', TIMESTAMP '2099-01-01 00:00:00')", s.id(), w.course());
        jdbc.update("insert into class_members (class_id, course_id, user_id, status, live_key, joined_at) values (?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP)",
                cls, w.course(), s.id(), w.course() + ":" + s.id());
        String base = "/api/classes/" + cls + "/assignments";
        String due = java.time.Instant.now().plusSeconds(48 * 3600).toString();

        call(post(base), teacher, Map.of("bankId", w.bank(), "dueAt", due, "forcedListening", Map.of("playLimit", 11))).andExpect(status().isBadRequest());
        call(post(base), teacher, Map.of("bankId", w.bank(), "dueAt", due, "forcedListening",
                Map.of("passPctTotal", 90, "groupThresholds", List.of(Map.of("groupId", w.g1(), "passPct", 60))))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("threshold_conflict"));
        assertEquals(0, count("select count(*) from assignments where class_id = ?", cls), "giao bài lỗi luật thì không tạo bài");
        var body = new HashMap<String, Object>(Map.of("bankId", w.bank(), "dueAt", due,
                "forcedListening", Map.of("playLimit", 1, "passPctTotal", 100, "hintEveryN", 1, "wrongThreshold", 1)));
        long asg = json.readTree(call(post(base), teacher, body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        assertEquals(1, count("select count(*) from forced_listening_rules where assignment_id = ?", asg));

        put(CMS + "/banks/" + w.bank() + "/forced-listening", admin, Map.of("playLimit", 5)).andExpect(status().isOk()); // sửa bộ sau khi giao
        long attempt = json.readTree(call(post(base + "/" + asg + "/start"), s, null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        assertEquals("COUNT_ONLY", jdbc.queryForObject("select result_mode from student_exercise_attempts where id = ?", String.class, attempt));
        play(s, attempt, w.g1()).andExpect(status().isOk()).andExpect(jsonPath("$.playLimit").value(1)); // luật của bài giao, không phải 5
        playDone(s, attempt, w.g1());
        play(s, attempt, w.g1()).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("play_limit_reached"));

        var res = json.readTree(postJson("/api/attempts/" + attempt + "/submit", s, Map.of("answers", answers(w, true, true, true, false)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        call(get(base + "/" + asg + "/mine"), s, null).andExpect(status().isOk()).andExpect(jsonPath("$.aiScore").doesNotExist())
                .andExpect(jsonPath("$.maxScore").doesNotExist()).andExpect(jsonPath("$.finalScore").doesNotExist()); // D-142
        assertEquals(100, res.at("/forced/passPct").asInt());
        assertEquals(false, res.at("/forced/passed").asBoolean());
        assertEquals(1, res.at("/forced/hardQuestions").size(), "N=1: lượt đầu đã có danh sách");

        // học viên tự học cùng bộ dùng luật của bộ, không phải luật của bài giao
        var self = student(0);
        long own = startAttempt(self, w);
        play(self, own, w.g1()).andExpect(jsonPath("$.playLimit").value(5));

        long free = insert("question_banks", Map.of("topic_id", w.topic(), "title", "Bộ thường", "is_trial", false, "status", "PUBLISHED"));
        question(free, "CONNECTIVE_MCQ", 1, "Câu", "{}", "{}");
        call(post(base), teacher, Map.of("bankId", free, "dueAt", due, "forcedListening", Map.of("playLimit", 2))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("forced_listening_off"));
    }
}
