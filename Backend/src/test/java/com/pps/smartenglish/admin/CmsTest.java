package com.pps.smartenglish.admin;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.support.ApiTestBase;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** FR-ADM-01 đợt 1: soạn Course > Level > Topic > Lesson, phân quyền ADMIN/EDITOR, trùng, đang dùng, xuất bản. */
class CmsTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";
    private static final String CMS = "/api/admin/cms";

    /** Tên post đã bị ApiTestBase.post(url, body, ip) chiếm, nên import tĩnh không dùng được. */
    private static MockHttpServletRequestBuilder post(String url) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url);
    }

    private Account withRole(String role) throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("update users set role = ? where id = ?", role, u.id());
        return login(u.email(), PASSWORD); // role nằm trong JWT nên phải đăng nhập lại
    }

    private ResultActions call(MockHttpServletRequestBuilder req, Account a, Object body) throws Exception {
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return a == null ? mvc.perform(req) : send(req, a.accessToken());
    }

    private long idOf(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private static String slug() {
        return "cms-" + UUID.randomUUID().toString().substring(0, 12);
    }

    private long course(Account a) throws Exception {
        return idOf(call(post(CMS + "/courses"), a, Map.of("slug", slug(), "title", "Khóa", "targetType", "IELTS"))
                .andExpect(status().isCreated()));
    }

    private long level(Account a, long courseId, String code) throws Exception {
        return idOf(call(post(CMS + "/courses/" + courseId + "/levels"), a, Map.of("code", code, "title", "Level " + code))
                .andExpect(status().isCreated()));
    }

    private long topic(Account a, long levelId) throws Exception {
        return idOf(call(post(CMS + "/levels/" + levelId + "/topics"), a, Map.of("title", "Chủ đề")).andExpect(status().isCreated()));
    }

    private long lesson(Account a, long topicId) throws Exception {
        return idOf(call(post(CMS + "/topics/" + topicId + "/lessons"), a,
                Map.of("type", "CONNECTIVE_VIDEO", "title", "Bài", "durationSec", 120, "videoRef", "dQw4w9WgXcQ"))
                .andExpect(status().isCreated()));
    }

    @Test
    void onlyAdminAndEditorEnterCmsAndEditorStaysOutOfAdminOnlyAreas() throws Exception {
        var admin = withRole("ADMIN");
        var editor = withRole("EDITOR");
        var student = register(newEmail(), PASSWORD);

        call(get(CMS + "/courses"), null, null).andExpect(status().isUnauthorized());
        call(get(CMS + "/courses"), student, null).andExpect(status().isForbidden());
        call(post(CMS + "/courses"), student, Map.of("slug", slug(), "title", "x")).andExpect(status().isForbidden());
        call(get(CMS + "/courses"), editor, null).andExpect(status().isOk());
        call(get(CMS + "/courses"), admin, null).andExpect(status().isOk());

        // EDITOR không xem được báo cáo, link kích hoạt; ADMIN thì vào được cả hai
        call(get("/api/admin/reports/dashboard"), editor, null).andExpect(status().isForbidden());
        call(get("/api/admin/activation-links"), editor, null).andExpect(status().isForbidden());
        call(get("/api/admin/reports/dashboard"), admin, null).andExpect(status().isOk());
        call(get("/api/admin/activation-links"), admin, null).andExpect(status().isOk());
        course(editor); // và EDITOR soạn được
    }

    @Test
    void courseLifecycleWithConflictsAndPartialUpdates() throws Exception {
        var admin = withRole("ADMIN");
        String slug = slug();
        long id = idOf(call(post(CMS + "/courses"), admin, Map.of("slug", slug, "title", "  Khóa A  ", "description", "Mô tả"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT")).andExpect(jsonPath("$.title").value("Khóa A")));

        call(post(CMS + "/courses"), admin, Map.of("slug", slug, "title", "Trùng")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("slug_taken"));
        for (String bad : new String[] {"Viet Hoa", "-abc", "a_b", ""}) {
            call(post(CMS + "/courses"), admin, Map.of("slug", bad, "title", "x")).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("validation_failed"));
        }
        call(post(CMS + "/courses"), admin, Map.of("slug", slug(), "title", "  ")).andExpect(status().isBadRequest());

        // PATCH bỏ qua trường vắng mặt
        call(patch(CMS + "/courses/" + id), admin, Map.of("title", "Khóa B")).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Khóa B")).andExpect(jsonPath("$.slug").value(slug))
                .andExpect(jsonPath("$.description").value("Mô tả")).andExpect(jsonPath("$.status").value("DRAFT"));
        call(patch(CMS + "/courses/" + id), admin, Map.of()).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Khóa B"));
        call(patch(CMS + "/courses/" + id), admin, Map.of("title", " ")).andExpect(status().isBadRequest());
        long other = course(admin);
        call(patch(CMS + "/courses/" + other), admin, Map.of("slug", slug)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("slug_taken"));
        call(patch(CMS + "/courses/" + id), admin, Map.of("slug", slug)).andExpect(status().isOk()); // giữ slug của chính mình

        // danh sách gồm cả DRAFT, mới nhất trước, phân trang con trỏ
        JsonNode page1 = json.readTree(call(get(CMS + "/courses?limit=1"), admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1))).andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andExpect(jsonPath("$.data[0].id").value(other)).andReturn().getResponse().getContentAsString());
        call(get(CMS + "/courses?limit=1&cursor=" + page1.get("nextCursor").asText()), admin, null)
                .andExpect(jsonPath("$.data[0].id").value(id));
        call(get(CMS + "/courses?limit=0"), admin, null).andExpect(status().isBadRequest());

        call(get(CMS + "/courses/999999999"), admin, null).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("course_not_found"));
        call(delete(CMS + "/courses/" + id), admin, null).andExpect(status().isNoContent());
        call(get(CMS + "/courses/" + id), admin, null).andExpect(status().isNotFound());
        call(delete(CMS + "/courses/" + id), admin, null).andExpect(status().isNotFound());
        call(get(CMS + "/courses/abc"), admin, null).andExpect(status().isNotFound());
    }

    @Test
    void levelTopicAndLessonLifecycleKeepTheirParents() throws Exception {
        var admin = withRole("ADMIN");
        long c = course(admin);
        long otherCourse = course(admin);
        long lv = level(admin, c, "A1");
        call(post(CMS + "/courses/" + c + "/levels"), admin, Map.of("code", "A1", "title", "Trùng mã")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("level_code_taken"));
        level(admin, otherCourse, "A1"); // mã chỉ cần duy nhất trong một khóa
        long lv2 = level(admin, c, "A2");
        call(patch(CMS + "/levels/" + lv2), admin, Map.of("code", "A1")).andExpect(status().isConflict());
        // cố đổi cha bằng body: bị bỏ qua
        call(patch(CMS + "/levels/" + lv2), admin, Map.of("title", "Mới", "sortOrder", 5, "courseId", otherCourse)).andExpect(status().isOk())
                .andExpect(jsonPath("$.courseId").value(c)).andExpect(jsonPath("$.sortOrder").value(5)).andExpect(jsonPath("$.code").value("A2"));
        call(post(CMS + "/courses/999999999/levels"), admin, Map.of("code", "X", "title", "x")).andExpect(status().isNotFound());
        call(post(CMS + "/courses/" + c + "/levels"), admin, Map.of("code", "", "title", "x")).andExpect(status().isBadRequest());

        long tp = idOf(call(post(CMS + "/levels/" + lv + "/topics"), admin, Map.of("title", "Chủ đề 1", "isFreeTrial", true, "sortOrder", 2))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.isFreeTrial").value(true)).andExpect(jsonPath("$.status").value("DRAFT")));
        call(patch(CMS + "/topics/" + tp), admin, Map.of("isFreeTrial", false, "levelId", 1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.isFreeTrial").value(false)).andExpect(jsonPath("$.levelId").value(lv))
                .andExpect(jsonPath("$.title").value("Chủ đề 1")).andExpect(jsonPath("$.sortOrder").value(2));
        call(post(CMS + "/levels/999999999/topics"), admin, Map.of("title", "x")).andExpect(status().isNotFound());

        // video: URL được chuẩn hóa về mã; chỉ YOUTUBE
        long ls = idOf(call(post(CMS + "/topics/" + tp + "/lessons"), admin, Map.of("type", "REFLEX_VIDEO", "title", "Bài 1",
                "videoRef", "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=30s"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.videoSource").value("YOUTUBE"))
                .andExpect(jsonPath("$.videoRef").value("dQw4w9WgXcQ")).andExpect(jsonPath("$.status").value("DRAFT")));
        for (String ref : new String[] {"https://youtu.be/aaaaaaaaaaa", "https://www.youtube.com/embed/bbbbbbbbbbb", "ccccccccccc"}) {
            call(patch(CMS + "/lessons/" + ls), admin, Map.of("videoRef", ref)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.videoRef").value(ref.substring(ref.length() - 11)));
        }
        for (String bad : new String[] {"short", "https://evil.example.com/watch?v=dQw4w9WgXcQ", "ftp://youtu.be/dQw4w9WgXcQ", " "}) {
            call(patch(CMS + "/lessons/" + ls), admin, Map.of("videoRef", bad)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("validation_failed"));
        }
        call(patch(CMS + "/lessons/" + ls), admin, Map.of("videoSource", "STORAGE", "videoRef", "videos/a.mp4")).andExpect(status().isBadRequest());
        call(post(CMS + "/topics/" + tp + "/lessons"), admin, Map.of("type", "CONNECTIVE_VIDEO", "title", "x", "videoSource", "STORAGE"))
                .andExpect(status().isBadRequest());
        call(post(CMS + "/topics/" + tp + "/lessons"), admin, Map.of("type", "OTHER", "title", "x")).andExpect(status().isBadRequest());
        call(post(CMS + "/topics/" + tp + "/lessons"), admin, Map.of("type", "CONNECTIVE_VIDEO", "title", "x", "durationSec", 0))
                .andExpect(status().isBadRequest());
        call(patch(CMS + "/lessons/" + ls), admin, Map.of("durationSec", 90, "title", "Bài mới", "topicId", 1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.durationSec").value(90)).andExpect(jsonPath("$.topicId").value(tp));

        // cây đầy đủ kể cả DRAFT, theo sortOrder
        call(get(CMS + "/courses/" + c), admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.course.id").value(c)).andExpect(jsonPath("$.levels", hasSize(2)))
                .andExpect(jsonPath("$.levels[0].level.code").value("A1")).andExpect(jsonPath("$.levels[1].level.code").value("A2"))
                .andExpect(jsonPath("$.levels[0].topics[0].topic.id").value(tp))
                .andExpect(jsonPath("$.levels[0].topics[0].lessons[0].id").value(ls))
                .andExpect(jsonPath("$.levels[0].topics[0].lessons[0].status").value("DRAFT"));
    }

    @Test
    void deleteIsRefusedWhileAnythingStillReferencesTheItem() throws Exception {
        var admin = withRole("ADMIN");
        long c = course(admin);
        long lv = level(admin, c, "A1");
        long tp = topic(admin, lv);
        long ls = lesson(admin, tp);
        long user = register(newEmail(), PASSWORD).id();

        // còn con
        for (String url : new String[] {"/courses/" + c, "/levels/" + lv, "/topics/" + tp}) {
            call(delete(CMS + url), admin, null).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("in_use"));
        }
        // bài đã có người học: khóa ngoại chặn, vẫn 409 chứ không phải 500, và bài còn nguyên
        jdbc.update("insert into lesson_progress (user_id, lesson_id) values (?, ?)", user, ls);
        call(delete(CMS + "/lessons/" + ls), admin, null).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("in_use"));
        assertEquals(1, count("select count(*) from lessons where id = ?", ls));
        call(get(CMS + "/courses/" + c), admin, null).andExpect(status().isOk()); // transaction vẫn dùng được sau lỗi

        jdbc.update("delete from lesson_progress where lesson_id = ?", ls);
        call(delete(CMS + "/lessons/" + ls), admin, null).andExpect(status().isNoContent());

        // topic có bộ câu hỏi, level có gói bán: khóa ngoại chặn
        long bank = new SimpleJdbcInsert(jdbc).withTableName("question_banks").usingGeneratedKeyColumns("id").usingColumns("topic_id", "title")
                .executeAndReturnKey(Map.of("topic_id", tp, "title", "Bộ")).longValue();
        call(delete(CMS + "/topics/" + tp), admin, null).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("in_use"));
        jdbc.update("delete from question_banks where id = ?", bank);
        call(delete(CMS + "/topics/" + tp), admin, null).andExpect(status().isNoContent());

        long product = new SimpleJdbcInsert(jdbc).withTableName("products").usingGeneratedKeyColumns("id")
                .usingColumns("type", "name", "price_vnd").executeAndReturnKey(Map.of("type", "LEVEL", "name", "SP " + UUID.randomUUID(), "price_vnd", 1L))
                .longValue();
        jdbc.update("insert into product_levels (product_id, level_id) values (?, ?)", product, lv);
        call(delete(CMS + "/levels/" + lv), admin, null).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("in_use"));
        jdbc.update("delete from product_levels where product_id = ?", product);

        // khi trống thì xóa cứng được, từ dưới lên
        call(delete(CMS + "/levels/" + lv), admin, null).andExpect(status().isNoContent());
        call(delete(CMS + "/courses/" + c), admin, null).andExpect(status().isNoContent());
        assertEquals(0, count("select count(*) from courses where id = ?", c));
    }

    @Test
    void publishIsIdempotentAndLessonNeedsVideoAndDuration() throws Exception {
        var editor = withRole("EDITOR");
        long c = course(editor);
        long lv = level(editor, c, "A1");
        long tp = topic(editor, lv);

        long noVideo = idOf(call(post(CMS + "/topics/" + tp + "/lessons"), editor, Map.of("type", "CONNECTIVE_VIDEO", "title", "Chưa có video",
                "durationSec", 60)).andExpect(status().isCreated()));
        long noDuration = idOf(call(post(CMS + "/topics/" + tp + "/lessons"), editor, Map.of("type", "CONNECTIVE_VIDEO", "title", "Chưa có thời lượng",
                "videoRef", "dQw4w9WgXcQ")).andExpect(status().isCreated()));
        call(post(CMS + "/lessons/" + noVideo + "/publish"), editor, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_failed")).andExpect(jsonPath("$.error.details.videoRef").exists());
        call(post(CMS + "/lessons/" + noDuration + "/publish"), editor, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.durationSec").exists());
        assertEquals("DRAFT", jdbc.queryForObject("select status from lessons where id = ?", String.class, noVideo));

        call(patch(CMS + "/lessons/" + noVideo), editor, Map.of("videoRef", "dQw4w9WgXcQ")).andExpect(status().isOk());
        for (int i = 0; i < 2; i++) { // gọi lại vẫn 200 và giữ nguyên
            call(post(CMS + "/lessons/" + noVideo + "/publish"), editor, null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
        }
        // xuất bản con khi cha còn DRAFT được; xuất bản cha không kéo con theo
        call(post(CMS + "/courses/" + c + "/publish"), editor, null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
        assertEquals("DRAFT", jdbc.queryForObject("select status from course_levels where id = ?", String.class, lv));
        assertEquals("DRAFT", jdbc.queryForObject("select status from topics where id = ?", String.class, tp));
        call(post(CMS + "/levels/" + lv + "/publish"), editor, null).andExpect(jsonPath("$.status").value("PUBLISHED"));
        call(post(CMS + "/topics/" + tp + "/publish"), editor, null).andExpect(jsonPath("$.status").value("PUBLISHED"));

        for (int i = 0; i < 2; i++) {
            call(post(CMS + "/lessons/" + noVideo + "/unpublish"), editor, null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"));
        }
        call(post(CMS + "/courses/" + c + "/unpublish"), editor, null).andExpect(jsonPath("$.status").value("DRAFT"));
        call(post(CMS + "/widgets/" + c + "/publish"), editor, null).andExpect(status().isNotFound());
        call(post(CMS + "/lessons/999999999/publish"), editor, null).andExpect(status().isNotFound());
        call(post(CMS + "/courses/" + c + "/publish"), null, null).andExpect(status().isUnauthorized());
    }
}
