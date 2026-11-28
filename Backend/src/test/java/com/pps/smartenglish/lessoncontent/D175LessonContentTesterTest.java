package com.pps.smartenglish.lessoncontent;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.shared.storage.ObjectStorage;
import com.pps.smartenglish.support.ApiTestBase;
import com.pps.smartenglish.support.FakeStorageConfig.FakeStorage;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Tester đợt #17/#29 (D-171, D-172, D-175): quyền, rò dữ liệu khi bài khóa, thứ tự cue, trần video, xóa bài kèm phụ đề. */
class D175LessonContentTesterTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";
    private static final String CMS = "/api/admin/cms";

    @Autowired ObjectStorage storage;

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
        return a == null ? mvc.perform(req) : send(req, a.accessToken());
    }

    private long lesson(boolean free, String status) {
        long c = insert("courses", Map.of("slug", "d175-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED"));
        long t = insert("topics", Map.of("level_id", lv, "title", "Topic", "is_free_trial", free, "status", "PUBLISHED"));
        return insert("lessons", Map.of("topic_id", t, "type", "CONNECTIVE_VIDEO", "title", "Bài", "status", status));
    }

    private ResultActions upload(Account a, long lesson, String lang, String body) throws Exception {
        var req = multipart(HttpMethod.PUT, CMS + "/lessons/" + lesson + "/subtitles/" + lang)
                .file(new MockMultipartFile("file", "s.vtt", "text/vtt", body.getBytes(StandardCharsets.UTF_8)));
        return a == null ? mvc.perform(req) : send(req, a.accessToken());
    }

    private long glossary(Account editor, long ls, String phrase) throws Exception {
        return json.readTree(call(MockMvcRequestBuilders.post(CMS + "/lessons/" + ls + "/glossary"), editor,
                Map.of("phrase", phrase, "meaningVi", "nghĩa", "source", "MANUAL")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    /** BFLA: mọi endpoint CMS mới cần cms.content; học viên, giáo viên, hỗ trợ 403; khách 401. */
    @Test
    void d175EveryNewCmsEndpointNeedsCmsContent() throws Exception {
        var editor = withRole("EDITOR");
        long ls = lesson(true, "PUBLISHED");
        long gid = glossary(editor, ls, "however");
        for (Account who : new Account[] {register(newEmail(), PASSWORD), withRole("TEACHER"), withRole("SUPPORT"), null}) {
            int expected = who == null ? 401 : 403;
            upload(who, ls, "en", "WEBVTT\n\n00:00.000 --> 00:01.000\nx\n").andExpect(status().is(expected));
            List<MockHttpServletRequestBuilder> reqs = List.of(
                    delete(CMS + "/lessons/" + ls + "/subtitles/en"),
                    get(CMS + "/lessons/" + ls + "/glossary"),
                    MockMvcRequestBuilders.post(CMS + "/lessons/" + ls + "/glossary"),
                    patch(CMS + "/glossary/" + gid),
                    delete(CMS + "/glossary/" + gid),
                    get(CMS + "/dictionary?q=a"),
                    MockMvcRequestBuilders.post(CMS + "/media/lesson-video"));
            for (var r : reqs) {
                call(r, who, Map.of("contentType", "video/mp4", "phrase", "x", "meaningVi", "x", "source", "MANUAL"))
                        .andExpect(status().is(expected));
            }
        }
        assertEquals(1, count("select count(*) from lesson_glossary where id = ?", gid));
    }

    /** NFR-SEC-07: học viên chưa mua và bài DRAFT không thấy phụ đề/glossary. */
    @Test
    void d175LockedOrDraftLessonLeaksNoSubtitleOrGlossary() throws Exception {
        var editor = withRole("EDITOR");
        long locked = lesson(false, "PUBLISHED");
        long draft = lesson(true, "DRAFT");
        for (long ls : new long[] {locked, draft}) {
            upload(editor, ls, "en", "WEBVTT\n\n00:00.000 --> 00:01.000\nsecret-cue\n").andExpect(status().isOk());
            glossary(editor, ls, "secret-term");
        }
        var student = register(newEmail(), PASSWORD);
        call(get("/api/lessons/" + locked), student, null).andExpect(status().isForbidden())
                .andExpect(content().string(not(containsString("secret"))));
        call(get("/api/lessons/" + draft), student, null).andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("secret"))));
        call(get("/api/lessons/" + draft), null, null).andExpect(status().isNotFound());
    }

    /** Web tìm cue bằng tìm nhị phân, giả định cue theo startMs tăng dần. File VTT lệch thứ tự vẫn được nhận. */
    @Test
    void d175StudentCuesAreSortedByStartEvenIfFileIsNot() throws Exception {
        var editor = withRole("EDITOR");
        long ls = lesson(true, "PUBLISHED");
        upload(editor, ls, "en", "WEBVTT\n\n00:05.000 --> 00:06.000\nthird\n\n00:00.000 --> 00:01.000\nfirst\n\n"
                + "00:02.000 --> 00:03.000\nsecond\n").andExpect(status().isOk());
        mvc.perform(get("/api/lessons/" + ls)).andExpect(status().isOk())
                .andExpect(jsonPath("$.subtitles.en[0].text").value("first"))
                .andExpect(jsonPath("$.subtitles.en[1].text").value("second"))
                .andExpect(jsonPath("$.subtitles.en[2].text").value("third"));
    }

    /** XSS: thẻ trong cue bị bỏ, thực thể HTML giải mã thành chữ thường (web hiển thị bằng text). */
    @Test
    void d175CueMarkupIsStrippedAndEntitiesStayPlainText() throws Exception {
        var editor = withRole("EDITOR");
        long ls = lesson(true, "PUBLISHED");
        upload(editor, ls, "en", "WEBVTT\n\n00:00.000 --> 00:01.000\n<img src=x onerror=alert(1)>hi &lt;b&gt;\n").andExpect(status().isOk());
        mvc.perform(get("/api/lessons/" + ls)).andExpect(jsonPath("$.subtitles.en[0].text").value("hi <b>"));
    }

    /** Xóa bài đã có phụ đề và glossary: CASCADE, không báo in_use. */
    @Test
    void d175DeletingLessonRemovesItsSubtitlesAndGlossary() throws Exception {
        var editor = withRole("EDITOR");
        long ls = lesson(true, "DRAFT");
        upload(editor, ls, "vi", "WEBVTT\n\n00:00.000 --> 00:01.000\nxin chào\n").andExpect(status().isOk());
        glossary(editor, ls, "hello");
        call(delete(CMS + "/lessons/" + ls), editor, null).andExpect(status().isNoContent());
        assertEquals(0, count("select count(*) from lesson_subtitles where lesson_id = ?", ls));
        assertEquals(0, count("select count(*) from lesson_glossary where lesson_id = ?", ls));
    }

    /** Trần 300 MB: chính sách presigned POST mang maxBytes, complete-video chặn file vượt trần của asset. */
    @Test
    void d175LessonVideoCapIsEnforcedInPolicyAndOnComplete() throws Exception {
        var editor = withRole("EDITOR");
        long id = json.readTree(call(MockMvcRequestBuilders.post(CMS + "/media/lesson-video"), editor, Map.of("contentType", "video/mp4"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("assetId").asLong();
        var fake = (FakeStorage) storage;
        assertEquals(314_572_800L, fake.lastPost.maxBytes());
        String key = fake.lastPost.key();
        jdbc.update("update media_assets set max_bytes = 8 where id = ?", id);
        fake.put(key, new byte[16]);
        // editor khác không xác nhận được upload của người khác
        call(MockMvcRequestBuilders.post(CMS + "/media/" + id + "/complete-video"), withRole("EDITOR"), null).andExpect(status().isNotFound());
        call(MockMvcRequestBuilders.post(CMS + "/media/" + id + "/complete-video"), editor, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("video_too_large"));
        jdbc.update("delete from media_assets where id = ?", id); // không để asset PENDING làm lệch test dọn file tạm (MediaApiTest)
    }

    /** Luồng web STORAGE -> YouTube: gửi {videoRef, videoSource:"YOUTUBE"}; học viên nhận lại đúng nguồn. */
    @Test
    void d175SwitchStorageBackToYoutubeAndStudentSeesIt() throws Exception {
        var editor = withRole("EDITOR");
        long ls = lesson(true, "PUBLISHED");
        var cols = new HashMap<String, Object>(Map.of("kind", "VIDEO", "status", "READY", "uploaded_by", editor.id()));
        cols.put("storage_key", "media/lesson-video/" + UUID.randomUUID());
        cols.put("duration_ms", 5_000);
        long asset = insert("media_assets", cols);
        call(patch(CMS + "/lessons/" + ls), editor, Map.of("videoSource", "STORAGE", "videoMediaId", asset)).andExpect(status().isOk());
        mvc.perform(get("/api/lessons/" + ls)).andExpect(jsonPath("$.video.source").value("STORAGE"))
                .andExpect(jsonPath("$.video.url").value(containsString("media/lesson-video/")));
        call(patch(CMS + "/lessons/" + ls), editor, Map.of("videoRef", "https://youtu.be/dQw4w9WgXcQ", "videoSource", "YOUTUBE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.videoSource").value("YOUTUBE")).andExpect(jsonPath("$.videoRef").value("dQw4w9WgXcQ"));
        mvc.perform(get("/api/lessons/" + ls)).andExpect(jsonPath("$.video.source").value("YOUTUBE"))
                .andExpect(jsonPath("$.video.ref").value("dQw4w9WgXcQ"));
    }
}
