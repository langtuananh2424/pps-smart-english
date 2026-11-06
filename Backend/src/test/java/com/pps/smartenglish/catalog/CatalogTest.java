package com.pps.smartenglish.catalog;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.support.ApiTestBase;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;

class CatalogTest extends ApiTestBase {

    // H2 không có "returning", nên lấy khóa sinh tự động qua SimpleJdbcInsert
    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id").usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private long course(String slug, String status) {
        return insert("courses", Map.of("slug", slug, "title", "Khóa " + slug, "description", "Mô tả", "target_type", "IELTS", "status", status));
    }

    private long level(long courseId, String code, int order) {
        return insert("course_levels", Map.of("course_id", courseId, "code", code, "title", "Level " + code, "sort_order", order, "status", "PUBLISHED"));
    }

    private long topic(long levelId, String title, int order, boolean free) {
        return insert("topics", Map.of("level_id", levelId, "title", title, "sort_order", order, "is_free_trial", free, "status", "PUBLISHED"));
    }

    private void lesson(long topicId, String title, int order, String status) {
        jdbc.update("insert into lessons (topic_id, type, title, video_source, video_ref, duration_sec, sort_order, status)"
                + " values (?, 'CONNECTIVE_VIDEO', ?, 'STORAGE', 'secret-key-123', 60, ?, ?)", topicId, title, order, status);
    }

    @Test
    void listHasOnlyPublishedCoursesWithoutToken() throws Exception {
        String pub = "pub-" + UUID.randomUUID();
        String draft = "draft-" + UUID.randomUUID();
        course(pub, "PUBLISHED");
        course(draft, "DRAFT");
        mvc.perform(get("/api/catalog/courses")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slug=='" + pub + "')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.slug=='" + draft + "')]", hasSize(0)))
                .andExpect(jsonPath("$[?(@.slug=='" + pub + "')].targetType").value(org.hamcrest.Matchers.hasItem("IELTS")));
    }

    @Test
    void detailReturnsOrderedTreeAndNeverLeaksVideoFields() throws Exception {
        String slug = "tree-" + UUID.randomUUID();
        long c = course(slug, "PUBLISHED");
        // chèn ngược thứ tự để chứng minh sắp theo sort_order
        long l2 = level(c, "B", 2);
        long l1 = level(c, "A", 1);
        long t2 = topic(l1, "T2", 2, false);
        long t1 = topic(l1, "T1", 1, true);
        topic(l2, "T3", 1, false);
        lesson(t1, "L2", 2, "PUBLISHED");
        lesson(t1, "L1", 1, "PUBLISHED");
        lesson(t1, "L-draft", 3, "DRAFT");
        lesson(t2, "L3", 1, "PUBLISHED");

        mvc.perform(get("/api/catalog/courses/" + slug)).andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(slug))
                .andExpect(jsonPath("$.levels[0].code").value("A"))
                .andExpect(jsonPath("$.levels[1].code").value("B"))
                .andExpect(jsonPath("$.levels[0].topics[0].title").value("T1"))
                .andExpect(jsonPath("$.levels[0].topics[0].isFreeTrial").value(true))
                .andExpect(jsonPath("$.levels[0].topics[1].isFreeTrial").value(false))
                .andExpect(jsonPath("$.levels[0].topics[0].lessons", hasSize(2)))
                .andExpect(jsonPath("$.levels[0].topics[0].lessons[0].title").value("L1"))
                .andExpect(jsonPath("$.levels[0].topics[0].lessons[1].title").value("L2"))
                .andExpect(jsonPath("$.levels[0].topics[0].lessons[0].type").value("CONNECTIVE_VIDEO"))
                .andExpect(jsonPath("$.levels[0].topics[0].lessons[0].durationSec").value(60))
                .andExpect(jsonPath("$.levels[0].topics[0].lessons[0].videoRef").doesNotExist())
                .andExpect(jsonPath("$.levels[0].topics[0].lessons[0].videoSource").doesNotExist())
                .andExpect(jsonPath("$.levels[1].topics[0].lessons", hasSize(0)))
                .andExpect(content().string(not(containsString("secret-key-123"))))
                .andExpect(content().string(not(containsString("video"))));
    }

    @Test
    void draftCourseAndUnknownSlugReturn404Envelope() throws Exception {
        String draft = "draft-" + UUID.randomUUID();
        course(draft, "DRAFT");
        mvc.perform(get("/api/catalog/courses/" + draft)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("course_not_found"));
        mvc.perform(get("/api/catalog/courses/khong-co-" + UUID.randomUUID())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("course_not_found"));
    }
}
