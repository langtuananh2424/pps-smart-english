package com.pps.smartenglish.classroom;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.support.ApiTestBase;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tester (FR-ASG-02): /api/me/assignments giới hạn 50 bản ghi, sắp hạn tăng dần. Lớp có hơn 50 bài còn hiệu lực
 * thì bài mới giao (hạn xa nhất) bị cắt khỏi danh sách, màn chi tiết web (tìm trong danh sách này) báo "Không tìm thấy".
 */
class AssignmentListLimitTesterTest extends ApiTestBase {

    private static final String PASSWORD = "mat-khau-1";

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    @Test
    void newestAssignmentStaysVisibleWhenStudentHasMoreThanFiftyActiveAssignments() throws Exception {
        long c = insert("courses", Map.of("slug", "asg-lim-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED",
                "delivery_mode", "CLASS_BASED", "sequential", false));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A", "status", "PUBLISHED", "sort_order", 0));
        long t = insert("topics", Map.of("level_id", lv, "title", "Unit", "is_free_trial", false, "status", "PUBLISHED", "sort_order", 0));
        long bank = insert("question_banks", Map.of("topic_id", t, "title", "Bộ", "is_trial", false, "status", "PUBLISHED", "mode", "PRACTICE"));
        insert("questions", Map.of("bank_id", bank, "type", "CONNECTIVE_MCQ", "prompt", "Câu"));
        long cls = insert("classes", Map.of("course_id", c, "name", "Lớp", "starts_on", LocalDate.now().minusDays(10),
                "ends_on", LocalDate.now().plusDays(400), "mode", "ONLINE", "capacity", 20));
        var tu = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = 'TEACHER'", tu.id());
        var teacher = login(tu.email(), PASSWORD);
        jdbc.update("insert into class_staff (class_id, user_id, role, assigned_from) values (?, ?, 'TEACHER', ?)", cls, teacher.id(), LocalDate.now().minusDays(10));
        var s = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', TIMESTAMP '2020-01-01 00:00:00', TIMESTAMP '2099-01-01 00:00:00')", s.id(), c);
        jdbc.update("insert into class_members (class_id, course_id, user_id, status, live_key, joined_at) values (?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP)", cls, c, s.id(), c + ":" + s.id());

        List<Long> ids = new ArrayList<>();
        Instant base = Instant.now().plus(1, ChronoUnit.DAYS);
        for (int i = 0; i < 51; i++) { // cùng bộ, khác hạn: không vướng unique (bài, lớp, hạn)
            var req = MockMvcRequestBuilders.post("/api/classes/" + cls + "/assignments").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("bankId", bank, "dueAt", base.plus(i, ChronoUnit.HOURS).toString())));
            String res = send(req, teacher.accessToken()).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            ids.add(json.readTree(res).get("id").asLong());
        }
        long newest = ids.get(ids.size() - 1);

        String body = send(MockMvcRequestBuilders.get("/api/me/assignments"), s.accessToken()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Long> seen = new ArrayList<>();
        json.readTree(body).forEach(n -> seen.add(n.get("id").asLong()));
        assertTrue(seen.contains(newest), "bài mới giao (id " + newest + ") bị cắt khỏi /api/me/assignments, chỉ thấy " + seen.size() + " bài");
    }
}
