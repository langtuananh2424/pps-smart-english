package com.pps.smartenglish.lessoncontent;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.support.ApiTestBase;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** FR-ADM-01, FR-LMS-02, D-171, D-175: phụ đề, glossary, gợi ý từ điển, video STORAGE và dữ liệu cho học viên. */
class LessonContentApiTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";
    private static final String CMS = "/api/admin/cms";
    private static final String VTT = "WEBVTT\n\n00:00.000 --> 00:02.000\nHello\n\n00:02.000 --> 00:04.000\nWorld\n";

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

    /** Bài PUBLISHED trong khóa PUBLISHED; free = topic học thử (khách xem được). */
    private long lesson(boolean free) {
        long c = insert("courses", Map.of("slug", "lc-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED"));
        long t = insert("topics", Map.of("level_id", lv, "title", "Topic", "is_free_trial", free, "status", "PUBLISHED"));
        return insert("lessons", Map.of("topic_id", t, "type", "CONNECTIVE_VIDEO", "title", "Bài", "status", "PUBLISHED"));
    }

    private ResultActions upload(Account a, long lesson, String lang, byte[] bytes) throws Exception {
        return send(multipart(HttpMethod.PUT, CMS + "/lessons/" + lesson + "/subtitles/" + lang)
                .file(new MockMultipartFile("file", "s.vtt", "text/vtt", bytes)), a.accessToken());
    }

    private long asset(long userId, String kind, String status, Integer durationMs) {
        var cols = new HashMap<String, Object>(Map.of("kind", kind, "status", status, "uploaded_by", userId));
        cols.put("storage_key", "media/lesson-video/" + UUID.randomUUID());
        cols.put("duration_ms", durationMs);
        return insert("media_assets", cols);
    }

    @Test
    void subtitleUploadReplaceDeleteAndPermissions() throws Exception {
        var editor = withRole("EDITOR");
        long ls = lesson(true);
        upload(editor, ls, "en", VTT.getBytes(StandardCharsets.UTF_8)).andExpect(status().isOk())
                .andExpect(jsonPath("$.lang").value("en")).andExpect(jsonPath("$.cueCount").value(2))
                .andExpect(jsonPath("$.updatedAt").isString()).andExpect(jsonPath("$.lessonId").doesNotExist());
        upload(editor, ls, "en", "1\n00:00:01,000 --> 00:00:02,000\nOne\n".getBytes(StandardCharsets.UTF_8)).andExpect(jsonPath("$.cueCount").value(1));
        assertEquals(1, count("select count(*) from lesson_subtitles where lesson_id = ?", ls));
        upload(editor, ls, "vi", VTT.getBytes(StandardCharsets.UTF_8)).andExpect(status().isOk());

        // cây CMS thấy phụ đề
        long course = jdbc.queryForObject("select c.id from courses c join course_levels l on l.course_id = c.id join topics t on t.level_id = l.id "
                + "join lessons x on x.topic_id = t.id where x.id = ?", Long.class, ls);
        call(get(CMS + "/courses/" + course), editor, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.levels[0].topics[0].lessons[0].subtitles", hasSize(2)))
                .andExpect(jsonPath("$.levels[0].topics[0].lessons[0].subtitles[0].lang").value("en"));

        call(delete(CMS + "/lessons/" + ls + "/subtitles/vi"), editor, null).andExpect(status().isNoContent());
        call(delete(CMS + "/lessons/" + ls + "/subtitles/vi"), editor, null).andExpect(status().isNoContent());
        assertEquals(1, count("select count(*) from lesson_subtitles where lesson_id = ?", ls));

        var student = register(newEmail(), PASSWORD);
        upload(student, ls, "en", VTT.getBytes(StandardCharsets.UTF_8)).andExpect(status().isForbidden());
        mvc.perform(multipart(HttpMethod.PUT, CMS + "/lessons/" + ls + "/subtitles/en").file(new MockMultipartFile("file", "a".getBytes())))
                .andExpect(status().isUnauthorized());
        upload(editor, 999999999L, "en", VTT.getBytes(StandardCharsets.UTF_8)).andExpect(status().isNotFound());
    }

    @Test
    void invalidSubtitleFilesAreRejectedWithFileField() throws Exception {
        var editor = withRole("EDITOR");
        long ls = lesson(true);
        upload(editor, ls, "fr", VTT.getBytes(StandardCharsets.UTF_8)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.lang").exists());
        for (byte[] bad : new byte[][] {
                new byte[0], "chỉ là văn bản".getBytes(StandardCharsets.UTF_8),
                "WEBVTT\n\n00:05.000 --> 00:01.000\nx\n".getBytes(StandardCharsets.UTF_8),
                {(byte) 0xff, (byte) 0xfe, 'a', (byte) 0xc3}, // không phải UTF-8
                tooManyCues(), new byte[1_048_577]}) {
            upload(editor, ls, "en", bad).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.file").exists());
        }
        assertEquals(0, count("select count(*) from lesson_subtitles where lesson_id = ?", ls));
    }

    private static byte[] tooManyCues() {
        var sb = new StringBuilder("WEBVTT\n\n");
        for (int i = 0; i < 5001; i++) {
            sb.append(String.format("%02d:%02d:%02d.000 --> %02d:%02d:%02d.500\nx\n\n", i / 3600, i / 60 % 60, i % 60, i / 3600, i / 60 % 60, i % 60));
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void glossaryCrudDuplicateFullAndValidation() throws Exception {
        var editor = withRole("EDITOR");
        long ls = lesson(true);
        long g = json.readTree(call(post(CMS + "/lessons/" + ls + "/glossary"), editor, Map.of("phrase", "  In order to ", "partOfSpeech", "phrase",
                "meaningVi", "để mà", "example", "We left early in order to avoid traffic.", "source", "MANUAL"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.phrase").value("In order to"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        call(post(CMS + "/lessons/" + ls + "/glossary"), editor, Map.of("phrase", "IN ORDER TO", "meaningVi", "x", "source", "MANUAL"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("glossary_duplicate"));
        call(post(CMS + "/lessons/" + ls + "/glossary"), editor, Map.of("phrase", "however", "meaningVi", "tuy nhiên", "source", "WIKTIONARY"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.partOfSpeech").doesNotExist());
        for (Map<String, Object> bad : java.util.List.<Map<String, Object>>of(
                Map.of("phrase", " ", "meaningVi", "x", "source", "MANUAL"), Map.of("phrase", "a".repeat(101), "meaningVi", "x", "source", "MANUAL"),
                Map.of("phrase", "a", "meaningVi", "", "source", "MANUAL"), Map.of("phrase", "a", "meaningVi", "x", "source", "OTHER"),
                Map.of("phrase", "a", "meaningVi", "x".repeat(501), "source", "MANUAL"))) {
            call(post(CMS + "/lessons/" + ls + "/glossary"), editor, bad).andExpect(status().isBadRequest());
        }
        call(patch(CMS + "/glossary/" + g), editor, Map.of("meaningVi", "nhằm để")).andExpect(status().isOk()).andExpect(jsonPath("$.meaningVi").value("nhằm để"));
        call(patch(CMS + "/glossary/" + g), editor, Map.of("phrase", "However")).andExpect(status().isConflict());
        call(get(CMS + "/lessons/" + ls + "/glossary"), editor, null).andExpect(jsonPath("$", hasSize(2)));

        for (int i = 0; i < 198; i++) {
            jdbc.update("insert into lesson_glossary (lesson_id, phrase, phrase_key, meaning_vi, source) values (?, ?, ?, 'x', 'MANUAL')", ls, "p" + i, "p" + i);
        }
        call(post(CMS + "/lessons/" + ls + "/glossary"), editor, Map.of("phrase", "overflow", "meaningVi", "x", "source", "MANUAL"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("glossary_full"));
        call(delete(CMS + "/glossary/" + g), editor, null).andExpect(status().isNoContent());
        call(delete(CMS + "/glossary/" + g), editor, null).andExpect(status().isNotFound());
        call(get(CMS + "/lessons/999999999/glossary"), editor, null).andExpect(status().isNotFound());
        call(get(CMS + "/lessons/" + ls + "/glossary"), register(newEmail(), PASSWORD), null).andExpect(status().isForbidden());
    }

    @Test
    void dictionarySuggestsExactThenPrefixAtMostTen() throws Exception {
        var editor = withRole("EDITOR");
        jdbc.update("delete from dictionary_entries");
        String[] words = {"environments", "Environment", "environmental", "envy", "other"};
        for (String w : words) {
            jdbc.update("insert into dictionary_entries (word, word_key, part_of_speech, meaning_vi) values (?, ?, 'noun', 'nghĩa')", w, w.toLowerCase());
        }
        call(get(CMS + "/dictionary?q=ENVIRONMENT"), editor, null).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].word").value("Environment")).andExpect(jsonPath("$[1].word").value("environments"))
                .andExpect(jsonPath("$[0].meaningVi").value("nghĩa"));
        call(get(CMS + "/dictionary?q=100%25"), editor, null).andExpect(jsonPath("$", hasSize(0))); // % không thành ký tự đại diện
        for (int i = 0; i < 15; i++) {
            jdbc.update("insert into dictionary_entries (word, word_key, meaning_vi) values (?, ?, 'x')", "pre" + i, "pre" + i);
        }
        call(get(CMS + "/dictionary?q=pre"), editor, null).andExpect(jsonPath("$", hasSize(10)));
        call(get(CMS + "/dictionary?q="), editor, null).andExpect(status().isBadRequest());
        call(get(CMS + "/dictionary"), editor, null).andExpect(status().isBadRequest());
        call(get(CMS + "/dictionary?q=" + "a".repeat(101)), editor, null).andExpect(status().isBadRequest());
        call(get(CMS + "/dictionary?q=a"), register(newEmail(), PASSWORD), null).andExpect(status().isForbidden());
    }

    @Test
    void lessonVideoPermitUsesOwnCapAndPrefix() throws Exception {
        var editor = withRole("EDITOR");
        call(post(CMS + "/media/lesson-video"), editor, Map.of("contentType", "video/mp4")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.maxBytes").value(314_572_800L)).andExpect(jsonPath("$.fields.key", startsWith("media/lesson-video/")));
        call(post(CMS + "/media/lesson-video"), editor, Map.of("contentType", "video/webm")).andExpect(status().isBadRequest());
        jdbc.update("delete from media_assets where kind = 'VIDEO' and status = 'PENDING'");
    }

    @Test
    void storageVideoNeedsReadyVideoAssetAndTakesItsDuration() throws Exception {
        var editor = withRole("EDITOR");
        long ls = lesson(true);
        jdbc.update("update lessons set duration_sec = null where id = ?", ls);
        long ready = asset(editor.id(), "VIDEO", "READY", 90_500);
        call(patch(CMS + "/lessons/" + ls), editor, Map.of("videoSource", "STORAGE", "videoMediaId", ready)).andExpect(status().isOk())
                .andExpect(jsonPath("$.videoSource").value("STORAGE")).andExpect(jsonPath("$.durationSec").value(91))
                .andExpect(jsonPath("$.videoRef").value(startsWith("media/lesson-video/"))).andExpect(jsonPath("$.videoUrl").value(startsWith("http://fake-storage/")));
        // thời lượng đã đặt thì giữ nguyên khi đổi video
        call(patch(CMS + "/lessons/" + ls), editor, Map.of("videoSource", "STORAGE", "videoMediaId", asset(editor.id(), "VIDEO", "READY", 10_000)))
                .andExpect(jsonPath("$.durationSec").value(91));
        call(post(CMS + "/lessons/" + ls + "/publish"), editor, null).andExpect(status().isOk());

        for (long bad : new long[] {asset(editor.id(), "VIDEO", "PENDING", null), asset(editor.id(), "AUDIO", "READY", 1000), 999999999L}) {
            call(patch(CMS + "/lessons/" + ls), editor, Map.of("videoSource", "STORAGE", "videoMediaId", bad)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("media_not_ready"));
        }
        call(patch(CMS + "/lessons/" + ls), editor, Map.of("videoSource", "STORAGE")).andExpect(status().isBadRequest());
        call(patch(CMS + "/lessons/" + ls), editor, Map.of("videoSource", "YOUTUBE", "videoMediaId", ready)).andExpect(status().isBadRequest());
        call(patch(CMS + "/lessons/" + ls), editor, Map.of("videoSource", "YOUTUBE", "videoRef", "dQw4w9WgXcQ")).andExpect(status().isOk())
                .andExpect(jsonPath("$.videoUrl").doesNotExist());
    }

    @Test
    void studentGetsSubtitlesAndGlossaryOnlyWhenLessonIsOpen() throws Exception {
        var editor = withRole("EDITOR");
        long open = lesson(true);
        long locked = lesson(false);
        for (long ls : new long[] {open, locked}) {
            upload(editor, ls, "en", VTT.getBytes(StandardCharsets.UTF_8)).andExpect(status().isOk());
            call(post(CMS + "/lessons/" + ls + "/glossary"), editor, Map.of("phrase", "secret-term", "meaningVi", "bí mật", "source", "MANUAL"));
        }
        mvc.perform(get("/api/lessons/" + open)).andExpect(status().isOk())
                .andExpect(jsonPath("$.subtitles.en", hasSize(2))).andExpect(jsonPath("$.subtitles.en[0].startMs").value(0))
                .andExpect(jsonPath("$.subtitles.en[1].endMs").value(4000)).andExpect(jsonPath("$.subtitles.en[1].text").value("World"))
                .andExpect(jsonPath("$.subtitles.vi", hasSize(0)))
                .andExpect(jsonPath("$.glossary", hasSize(1))).andExpect(jsonPath("$.glossary[0].phrase").value("secret-term"))
                .andExpect(jsonPath("$.glossary[0].source").value("MANUAL")).andExpect(jsonPath("$.glossary[0].id").doesNotExist());
        mvc.perform(get("/api/lessons/" + locked)).andExpect(status().isForbidden())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-term"))));
    }
}
