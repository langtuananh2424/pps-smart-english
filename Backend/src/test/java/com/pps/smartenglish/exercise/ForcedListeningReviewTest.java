package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Tester đợt #69 + #70: điểm ghép giữa Nghe ép (result_mode COUNT_ONLY) và nhập/hoàn tác lô (câu archived), cùng các đường
 * vẫn đọc chế độ HIỆN TẠI của bộ thay vì result_mode đã chụp trên lượt (D-142, D-143).
 */
class ForcedListeningReviewTest extends ListeningTestSupport {

    private static final String CMS = "/api/admin/cms";

    private record Fx(World w, long classId, Account teacher) {}

    private Fx fixture(String playback) throws Exception {
        var w = world(playback, null);
        jdbc.update("update courses set delivery_mode = 'CLASS_BASED' where id = ?", w.course());
        long cls = insert("classes", Map.of("course_id", w.course(), "name", "Lớp nghe", "starts_on", LocalDate.now().minusDays(1),
                "ends_on", LocalDate.now().plusDays(60), "mode", "ONLINE", "capacity", 20));
        var teacher = withRole("TEACHER");
        jdbc.update("insert into class_staff (class_id, user_id, role, assigned_from) values (?, ?, 'TEACHER', ?)", cls, teacher.id(),
                LocalDate.now().minusDays(2));
        return new Fx(w, cls, teacher);
    }

    private Account member(Fx f) throws Exception {
        var s = student(0);
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', "
                + "TIMESTAMP '2020-01-01 00:00:00', TIMESTAMP '2099-01-01 00:00:00')", s.id(), f.w().course());
        jdbc.update("insert into class_members (class_id, course_id, user_id, status, live_key, joined_at) values (?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP)",
                f.classId(), f.w().course(), s.id(), f.w().course() + ":" + s.id());
        return s;
    }

    private String base(Fx f) {
        return "/api/classes/" + f.classId() + "/assignments";
    }

    private ResultActions call(MockHttpServletRequestBuilder req, Account a, Object body) throws Exception {
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return send(req, a.accessToken());
    }

    private JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private long assign(Fx f) throws Exception {
        return body(call(MockMvcRequestBuilders.post(base(f)), f.teacher(),
                Map.of("bankId", f.w().bank(), "dueAt", Instant.now().plus(48, ChronoUnit.HOURS).toString()))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private ResultActions start(Fx f, long asg, Account s) throws Exception {
        return call(MockMvcRequestBuilders.post(base(f) + "/" + asg + "/start"), s, null);
    }

    private void submitAssigned(Fx f, long asg, Account s, boolean... correct) throws Exception {
        long id = body(start(f, asg, s).andExpect(status().isOk())).get("id").asLong();
        postJson("/api/attempts/" + id + "/submit", s, Map.of("answers", answers(f.w(), correct))).andExpect(status().isOk());
    }

    private JsonNode progressRow(Fx f, long asg, long userId) throws Exception {
        for (JsonNode r : body(call(MockMvcRequestBuilders.get(base(f) + "/" + asg + "/progress"), f.teacher(), null).andExpect(status().isOk()))) {
            if (r.get("userId").asLong() == userId) {
                return r;
            }
        }
        throw new AssertionError("không thấy học viên " + userId);
    }

    private void playbackMode(Account admin, long bank, String mode) throws Exception {
        call(MockMvcRequestBuilders.patch(CMS + "/banks/" + bank), admin, Map.of("playbackMode", mode)).andExpect(status().isOk());
    }

    private static List<Long> ids(JsonNode arr, String field) {
        List<Long> out = new ArrayList<>();
        arr.forEach(n -> out.add(n.get(field).asLong()));
        return out;
    }

    // ---- D-142/D-143 phải theo result_mode của lượt, không theo chế độ hiện tại của bộ ----

    @Test
    void teacherProgressHidesScoreOfCountOnlyAttemptAfterBankLeavesLockedMode() throws Exception {
        var f = fixture("LOCKED");
        var admin = withRole("ADMIN");
        var s = member(f);
        long asg = assign(f);
        submitAssigned(f, asg, s, true, true, true, false);
        playbackMode(admin, f.w().bank(), "FREE"); // người soạn đổi chế độ sau khi học viên đã làm Nghe ép

        var row = progressRow(f, asg, s.id());
        assertTrue(row.path("aiScore").isNull() && row.path("maxScore").isNull() && row.path("finalScore").isNull(),
                "lượt COUNT_ONLY không được lộ điểm cho giáo viên dù bộ đã về FREE: " + row);
    }

    @Test
    void gradingCountOnlyAttemptIsStillRejectedAfterBankLeavesLockedMode() throws Exception {
        var f = fixture("LOCKED");
        var admin = withRole("ADMIN");
        var s = member(f);
        long asg = assign(f);
        long attempt = body(start(f, asg, s).andExpect(status().isOk())).get("id").asLong();
        postJson("/api/attempts/" + attempt + "/submit", s, Map.of("answers", answers(f.w(), true, true, true, true))).andExpect(status().isOk());
        playbackMode(admin, f.w().bank(), "FREE");

        call(MockMvcRequestBuilders.put(base(f) + "/" + asg + "/attempts/" + attempt + "/grade"), f.teacher(), Map.of("score", 3))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("grade_not_supported"));
    }

    @Test
    void ordinaryAssignmentStaysSingleSubmissionAfterBankSwitchedToLocked() throws Exception {
        var f = fixture("FREE");
        var admin = withRole("ADMIN");
        var s = member(f);
        long asg = assign(f);
        submitAssigned(f, asg, s, true, true, true, true);
        playbackMode(admin, f.w().bank(), "LOCKED");

        // FR-ASG-02: bài giao thường đã nộp thì không mở lượt mới, kể cả khi bộ đổi sang Nghe ép sau đó
        start(f, asg, s).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("assignment_submitted"));
    }

    @Test
    void chapterTestScoreOfCountOnlyAttemptIsHiddenOnClassProgress() throws Exception {
        var f = fixture("LOCKED");
        jdbc.update("update topics set chapter_test_bank_id = ? where id = ?", f.w().bank(), f.w().topic());
        var s = member(f);
        submit(s, f.w(), true, true, true, false); // lượt tự học Nghe ép trên bộ kiểm tra chương

        var board = body(call(MockMvcRequestBuilders.get("/api/classes/" + f.classId() + "/progress"), f.teacher(), null)
                .andExpect(status().isOk()));
        for (JsonNode st : board.get("students")) {
            if (st.get("userId").asLong() == s.id()) {
                for (JsonNode c : st.get("chapterTests")) {
                    assertTrue(c.path("score").isNull() && c.path("maxScore").isNull() && c.path("percent").isNull(),
                            "D-142: lượt Nghe ép không có điểm, kể cả ở bảng tiến độ lớp: " + c);
                }
            }
        }
    }

    // ---- câu archived (hoàn tác lô) với danh sách câu sai nhiều và script ----

    @Test
    void archivedQuestionLeavesHardListAndScript() throws Exception {
        var w = world("LOCKED", null);
        var admin = withRole("ADMIN");
        var u = student(0);
        put(CMS + "/banks/" + w.bank() + "/forced-listening", admin, Map.of("hintEveryN", 1, "wrongThreshold", 1)).andExpect(status().isOk());
        submit(u, w, false, true, true, true); // q0 sai một lần
        jdbc.update("update questions set archived_at = CURRENT_TIMESTAMP where id = ?", w.q()[0]); // như hoàn tác lô đã có lượt làm

        long attempt = startAttempt(u, w);
        List<Map<String, Object>> ans = new ArrayList<>();
        for (int i = 1; i < 4; i++) {
            ans.add(Map.of("questionId", w.q()[i], "answer", Map.of("choiceId", w.right()[i])));
        }
        var res = body(postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers", ans)).andExpect(status().isOk()));
        assertEquals(3, res.at("/forced/totalCount").asInt());
        var hard = res.at("/forced/hardQuestions");
        assertNotNull(hard);
        assertTrue(!ids(hard, "questionId").contains(w.q()[0]), "câu đã ẩn không được nằm trong danh sách câu sai nhiều: " + hard);
        getJson("/api/attempts/" + attempt + "/scripts/" + w.q()[0], u).andExpect(status().is4xxClientError());
    }

    @Test
    void undoImportIntoLockedBankArchivesForcedHistoryAndKeepsPlayAndScriptRows() throws Exception {
        var w = world("LOCKED", null);
        var admin = withRole("ADMIN");
        var u = student(0);
        put(CMS + "/banks/" + w.bank() + "/forced-listening", admin, Map.of("hintEveryN", 1, "wrongThreshold", 1)).andExpect(status().isOk());

        // nhập hàng loạt vào bộ LOCKED
        String text = "# Trắc nghiệm\nQ: Imported one\nA) x\n*B) y\n\nQ: Imported two\nA) x\n*B) y\n";
        var preview = body(send(MockMvcRequestBuilders.multipart(CMS + "/banks/" + w.bank() + "/imports/preview").param("text", text),
                admin.accessToken()).andExpect(status().isOk()));
        List<Map<String, Object>> rows = new ArrayList<>();
        preview.get("rows").forEach(r -> rows.add(Map.of("line", r.get("line").asInt(), "question", json.convertValue(r.get("question"), Map.class))));
        long batch = body(call(MockMvcRequestBuilders.post(CMS + "/banks/" + w.bank() + "/imports"), admin,
                Map.of("source", "PASTE", "rows", rows)).andExpect(status().isCreated())).get("batchId").asLong();
        List<Long> imported = jdbc.queryForList("select id from questions where import_batch_id = ? order by id", Long.class, batch);
        assertEquals(2, imported.size());
        jdbc.update("update questions set transcript_excerpt = 'EXCERPT-IMPORTED' where id = ?", imported.get(0));

        // một lượt Nghe ép: nghe part 1, sai cả hai câu nhập, xem script câu nhập
        long attempt = startAttempt(u, w);
        play(u, attempt, w.g1()).andExpect(status().isOk());
        List<Map<String, Object>> ans = new ArrayList<>(answers(w, true, true, true, true));
        for (long q : imported) {
            long wrong = jdbc.queryForObject("select id from question_choices where question_id = ? and is_correct = false", Long.class, q);
            ans.add(Map.of("questionId", q, "answer", Map.of("choiceId", wrong)));
        }
        var res = body(postJson("/api/attempts/" + attempt + "/submit", u, Map.of("answers", ans)).andExpect(status().isOk()));
        assertEquals(6, res.at("/forced/totalCount").asInt());
        assertEquals(4, res.at("/forced/correctCount").asInt());
        assertTrue(ids(res.at("/forced/hardQuestions"), "questionId").containsAll(imported));
        getJson("/api/attempts/" + attempt + "/scripts/" + imported.get(0), u).andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("EXCERPT-IMPORTED"));

        // hoàn tác: câu đã có lượt Nghe ép chỉ bị ẩn, bảng audio_plays/script_views còn nguyên
        call(MockMvcRequestBuilders.post(CMS + "/imports/" + batch + "/undo"), admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(0)).andExpect(jsonPath("$.archived").value(2));
        assertEquals(2, count("select count(*) from questions where import_batch_id = ? and archived_at is not null", batch));
        assertEquals(1, count("select count(*) from audio_plays where attempt_id = ?", attempt));
        assertEquals(1, count("select count(*) from script_views where attempt_id = ?", attempt));

        // lịch sử lượt cũ vẫn đủ 6 câu và không lộ đáp án; lượt mới chỉ còn 4 câu
        var history = get("/api/attempts/" + attempt, u, 200);
        assertEquals(6, history.at("/forced/totalCount").asInt());
        assertTrue(history.path("results").isMissingNode() || history.path("results").isNull(), history.toString());
        assertTrue(history.path("score").isMissingNode() || history.path("score").isNull(), history.toString());
        var next = submit(u, w, true, true, true, true);
        assertEquals(4, next.at("/forced/totalCount").asInt());
    }

    // ---- quyền: giáo viên không có cms.content không đặt được luật của bộ ----

    @Test
    void teacherWithoutCmsPermissionCannotReadOrSetBankForcedRule() throws Exception {
        var w = world("LOCKED", null);
        var teacher = withRole("TEACHER");
        var s = student(0);
        String url = CMS + "/banks/" + w.bank() + "/forced-listening";
        getJson(url, teacher).andExpect(status().isForbidden());
        put(url, teacher, Map.of("playLimit", 9)).andExpect(status().isForbidden());
        put(url, s, Map.of("playLimit", 9)).andExpect(status().isForbidden());
        assertEquals(0, count("select count(*) from forced_listening_rules where bank_id = ?", w.bank()));
    }

    @Test
    void rejectedForcedOverrideLeavesNoAssignmentBehind() throws Exception {
        var f = fixture("LOCKED");
        member(f);
        String due = Instant.now().plus(48, ChronoUnit.HOURS).toString();
        call(MockMvcRequestBuilders.post(base(f)), f.teacher(), Map.of("bankId", f.w().bank(), "dueAt", due, "forcedListening",
                Map.of("passPctTotal", 70, "groupThresholds", List.of(Map.of("groupId", f.w().g1(), "passPct", 50)))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("threshold_conflict"));
        var other = world("LOCKED", null);
        call(MockMvcRequestBuilders.post(base(f)), f.teacher(), Map.of("bankId", f.w().bank(), "dueAt", due, "forcedListening",
                Map.of("groupThresholds", List.of(Map.of("groupId", other.g1(), "passPct", 50))))).andExpect(status().isBadRequest());
        assertEquals(0, count("select count(*) from assignments where class_id = ?", f.classId()), "lỗi luật phải rollback cả bài giao");
        assertEquals(0, count("select count(*) from notifications n where n.type = 'ASSIGNMENT_NEW' and n.user_id in "
                + "(select user_id from class_members where class_id = ?)", f.classId()));
    }
}
