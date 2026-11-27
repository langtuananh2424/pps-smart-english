package com.pps.smartenglish.placement;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.placement.service.PlacementTimeoutJob;
import com.pps.smartenglish.support.ApiTestBase;
import com.pps.smartenglish.support.FakeStorageConfig.FakeStorage;
import com.pps.smartenglish.support.MutableClockConfig;
import com.pps.smartenglish.support.MutableClockConfig.MutableClock;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** FR-TEST-01/02, FR-GOAL-03, D-23, D-39: đề đầu vào, lượt của khách và học viên, band, gợi ý khóa. */
@Import(MutableClockConfig.class)
class PlacementTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";
    private static final String CMS = "/api/admin/cms";
    private static final String PL = "/api/placement/attempts";

    @Autowired MutableClock clock;
    @Autowired PlacementTimeoutJob timeoutJob;
    @Autowired FakeStorage storage;

    private Account admin;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("update courses set band_from = null, band_to = null");
        jdbc.update("update placement_config set max_retakes = null");
        admin = withRole("ADMIN");
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    // ---- helpers ----

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

    private JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private static Map<String, Object> mcq(String skill) {
        var m = new java.util.HashMap<String, Object>(Map.of("type", "CONNECTIVE_MCQ", "prompt", "Đề " + skill,
                "choices", List.of(Map.of("label", "A", "content", "Sai", "isCorrect", false),
                        Map.of("label", "B", "content", "Đúng", "isCorrect", true, "explanation", "BÍ MẬT"))));
        if (skill != null) {
            m.put("skill", skill);
        }
        return m;
    }

    private void rules() throws Exception {
        var all = new ArrayList<Map<String, Object>>();
        for (String s : List.of("LISTENING", "READING", "GRAMMAR")) {
            all.add(Map.of("skill", s, "minPercent", 0, "band", 3.0));
            all.add(Map.of("skill", s, "minPercent", 50, "band", 5.0));
            all.add(Map.of("skill", s, "minPercent", 100, "band", 7.0));
        }
        call(put(CMS + "/placement/band-rules"), admin, Map.of("rules", all)).andExpect(status().isOk());
    }

    /** Đề đã xuất bản: LISTENING 2 câu, READING 1 câu, GRAMMAR 1 câu; giới hạn 30 phút. */
    private long publishedBank() throws Exception {
        rules();
        long id = body(call(MockMvcRequestBuilders.post(CMS + "/placement/banks"), admin, Map.of("title", "Đầu vào", "timeLimitSec", 1800))
                .andExpect(status().isCreated())).get("id").asLong();
        for (String skill : List.of("LISTENING", "LISTENING", "READING", "GRAMMAR")) {
            call(MockMvcRequestBuilders.post(CMS + "/banks/" + id + "/questions"), admin, mcq(skill)).andExpect(status().isCreated());
        }
        call(MockMvcRequestBuilders.post(CMS + "/placement/banks/" + id + "/publish"), admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        return id;
    }

    private ResultActions startAsGuest(String ip) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(PL).header("X-Real-IP", ip));
    }

    private ResultActions startAs(Account u) throws Exception {
        return send(MockMvcRequestBuilders.post(PL).header("X-Real-IP", newIp()), u.accessToken());
    }

    /** Chọn B (đúng) cho câu thuộc kỹ năng trong correct, A cho phần còn lại. */
    private Map<String, Object> answers(JsonNode started, Set<String> correct) {
        var list = new ArrayList<Map<String, Object>>();
        for (JsonNode q : started.get("questions")) {
            String label = correct.contains(q.get("skill").asText()) ? "B" : "A";
            for (JsonNode c : q.get("choices")) {
                if (c.get("label").asText().equals(label)) {
                    list.add(Map.of("questionId", q.get("id").asLong(), "answer", Map.of("choiceId", c.get("id").asLong())));
                }
            }
        }
        return Map.of("answers", list);
    }

    private ResultActions draft(String id, String token, Account u, Object body) throws Exception {
        return act(put(PL + "/" + id + "/draft"), token, u, body);
    }

    private ResultActions submit(String id, String token, Account u, Object body) throws Exception {
        return act(MockMvcRequestBuilders.post(PL + "/" + id + "/submit"), token, u, body);
    }

    private ResultActions claim(String id, String token, Account u) throws Exception {
        return act(MockMvcRequestBuilders.post(PL + "/" + id + "/claim"), token, u, null);
    }

    private ResultActions act(MockHttpServletRequestBuilder req, String token, Account u, Object body) throws Exception {
        if (token != null) {
            req.header("X-Placement-Token", token);
        }
        return call(req, u, body);
    }

    private long course(String status, String from, String to) {
        long c = insert("courses", Map.of("slug", "pl-" + UUID.randomUUID(), "title", "Khóa " + from, "status", status));
        jdbc.update("update courses set band_from = ?, band_to = ? where id = ?", new java.math.BigDecimal(from),
                new java.math.BigDecimal(to), c);
        return c;
    }

    // ---- CMS ----

    @Test
    void cmsBuildsPlacementBankWithoutTopicAndRejectsBadQuestions() throws Exception {
        var res = body(call(MockMvcRequestBuilders.post(CMS + "/placement/banks"), admin, Map.of("title", "Đề", "timeLimitSec", 1800))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.topicId").doesNotExist())
                .andExpect(jsonPath("$.mode").value("EXAM")).andExpect(jsonPath("$.purpose").value("PLACEMENT")));
        long bank = res.get("id").asLong();
        var add = MockMvcRequestBuilders.post(CMS + "/banks/" + bank + "/questions");

        call(add, admin, mcq(null)).andExpect(status().isBadRequest()); // thiếu skill
        var writing = Map.<String, Object>of("type", "WRITING", "prompt", "Viết", "skill", "READING");
        call(MockMvcRequestBuilders.post(CMS + "/banks/" + bank + "/questions"), admin, writing).andExpect(status().isBadRequest());
        var rewrite = Map.<String, Object>of("type", "CONNECTIVE_REWRITE", "prompt", "Viết lại", "skill", "GRAMMAR");
        call(MockMvcRequestBuilders.post(CMS + "/banks/" + bank + "/questions"), admin, rewrite).andExpect(status().isBadRequest());
        var badSkill = mcq("SPEAKING");
        call(MockMvcRequestBuilders.post(CMS + "/banks/" + bank + "/questions"), admin, badSkill).andExpect(status().isBadRequest());
        call(MockMvcRequestBuilders.post(CMS + "/banks/" + bank + "/questions"), admin, mcq("READING")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.skill").value("READING"));

        // thiếu giờ, đổi mode hoặc gắn bài học đều bị từ chối
        call(MockMvcRequestBuilders.post(CMS + "/placement/banks"), admin, Map.of("title", "Đề")).andExpect(status().isBadRequest());
        call(patch(CMS + "/banks/" + bank), admin, Map.of("mode", "PRACTICE")).andExpect(status().isBadRequest());
        call(patch(CMS + "/banks/" + bank), admin, Map.of("timeLimitSec", 2400)).andExpect(status().isOk());

        // câu của bộ khóa học không được có skill
        long topic = insert("topics", Map.of("level_id", insert("course_levels", Map.of("course_id",
                insert("courses", Map.of("slug", "pl-" + UUID.randomUUID(), "title", "K", "status", "PUBLISHED")), "code", "A", "title", "L",
                "status", "PUBLISHED")), "title", "T", "status", "PUBLISHED"));
        long courseBank = body(call(MockMvcRequestBuilders.post(CMS + "/topics/" + topic + "/banks"), admin, Map.of("title", "B")))
                .get("id").asLong();
        call(MockMvcRequestBuilders.post(CMS + "/banks/" + courseBank + "/questions"), admin, mcq("READING"))
                .andExpect(status().isBadRequest());
        call(MockMvcRequestBuilders.post(CMS + "/banks/" + courseBank + "/questions"), admin, mcq(null)).andExpect(status().isCreated());
        // bộ khóa học không xuất bản qua đường placement và ngược lại
        call(MockMvcRequestBuilders.post(CMS + "/placement/banks/" + courseBank + "/publish"), admin, null).andExpect(status().isNotFound());
        call(MockMvcRequestBuilders.post(CMS + "/banks/" + bank + "/publish"), admin, null).andExpect(status().isBadRequest());

        // học viên thường không vào được CMS
        call(get(CMS + "/placement/banks"), register(newEmail(), PASSWORD), null).andExpect(status().isForbidden());
    }

    @Test
    void publishNeedsBandRulesAndKeepsOnlyOneActiveBank() throws Exception {
        jdbc.update("delete from placement_band_rules");
        long first = body(call(MockMvcRequestBuilders.post(CMS + "/placement/banks"), admin, Map.of("title", "1", "timeLimitSec", 600)))
                .get("id").asLong();
        var publish = MockMvcRequestBuilders.post(CMS + "/placement/banks/" + first + "/publish");
        call(publish, admin, null).andExpect(status().isBadRequest()); // chưa có câu hỏi
        call(MockMvcRequestBuilders.post(CMS + "/banks/" + first + "/questions"), admin, mcq("READING")).andExpect(status().isCreated());
        call(MockMvcRequestBuilders.post(CMS + "/placement/banks/" + first + "/publish"), admin, null)
                .andExpect(status().isBadRequest()); // chưa có bảng quy đổi cho READING

        call(put(CMS + "/placement/band-rules"), admin, Map.of("rules", List.of(Map.of("skill", "READING", "minPercent", 0, "band", 5.25))))
                .andExpect(status().isBadRequest());
        var dup = Map.<String, Object>of("skill", "READING", "minPercent", 0, "band", 4.0);
        call(put(CMS + "/placement/band-rules"), admin, Map.of("rules", List.of(dup, dup))).andExpect(status().isBadRequest());
        call(put(CMS + "/placement/config"), admin, Map.of("maxRetakes", -1)).andExpect(status().isBadRequest());
        call(put(CMS + "/placement/band-rules"), admin, Map.of("rules", List.of(dup))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].band").value(4.0));
        call(MockMvcRequestBuilders.post(CMS + "/placement/banks/" + first + "/publish"), admin, null).andExpect(status().isOk());

        long second = body(call(MockMvcRequestBuilders.post(CMS + "/placement/banks"), admin, Map.of("title", "2", "timeLimitSec", 600)))
                .get("id").asLong();
        call(MockMvcRequestBuilders.post(CMS + "/banks/" + second + "/questions"), admin, mcq("READING")).andExpect(status().isCreated());
        call(MockMvcRequestBuilders.post(CMS + "/placement/banks/" + second + "/publish"), admin, null).andExpect(status().isOk());
        assertEquals("DRAFT", jdbc.queryForObject("select status from question_banks where id = ?", String.class, first));
        assertEquals(1, count("select count(*) from question_banks where purpose = 'PLACEMENT' and status = 'PUBLISHED'"));
        org.junit.jupiter.api.Assertions.assertTrue(count("select count(*) from audit_logs where target_type = 'PLACEMENT_BAND_RULES'") >= 1);
    }

    @Test
    void placementBankNeverLeaksIntoStudentCourseFlows() throws Exception {
        long bank = publishedBank();
        var u = register(newEmail(), PASSWORD);
        send(get("/api/banks/" + bank), u.accessToken()).andExpect(status().isNotFound());
        send(MockMvcRequestBuilders.post("/api/banks/" + bank + "/attempts").contentType(MediaType.APPLICATION_JSON).content("{}"),
                u.accessToken()).andExpect(status().isNotFound());
    }

    // ---- luồng khách ----

    /** D-134/D-135: phản hồi bắt đầu bài mang promptHtml (đã làm sạch) và ảnh của câu. */
    @Test
    void startReturnsPromptHtmlAndMedia() throws Exception {
        long bank = publishedBank();
        long q = jdbc.queryForObject("select id from questions where bank_id = ? order by sort_order, id limit 1", Long.class, bank);
        call(patch(CMS + "/questions/" + q), admin, Map.of("promptHtml", "<p>Xem <b>hình</b><script>evil()</script></p>")).andExpect(status().isOk());
        var png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB), "png", png);
        var permit = body(call(MockMvcRequestBuilders.post(CMS + "/media/images"), admin, Map.of("contentType", "image/png")).andExpect(status().isCreated()));
        storage.put(permit.get("fields").get("key").asText(), png.toByteArray());
        long asset = permit.get("assetId").asLong();
        call(MockMvcRequestBuilders.post(CMS + "/media/" + asset + "/complete"), admin, null).andExpect(status().isOk());
        call(put(CMS + "/questions/" + q + "/media"), admin, Map.of("items", List.of(
                Map.of("assetId", asset, "role", "DIAGRAM", "displaySize", "MD", "altText", "Hình minh họa")))).andExpect(status().isOk());

        startAsGuest(newIp()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.questions[0].promptHtml").value("<p>Xem <b>hình</b></p>"))
                .andExpect(jsonPath("$.questions[0].media[0].assetId").value(asset))
                .andExpect(jsonPath("$.questions[0].media[0].url").value(containsString("http://fake-storage/")))
                .andExpect(jsonPath("$.questions[0].media[0].displaySize").value("MD"))
                .andExpect(jsonPath("$.questions[1].promptHtml").doesNotExist());
    }

    @Test
    void guestSubmitsWithoutSeeingBandThenClaimsAfterLogin() throws Exception {
        long bank = publishedBank();
        long matching = course("PUBLISHED", "5.0", "6.0");
        course("PUBLISHED", "6.0", "7.0");
        course("DRAFT", "5.0", "6.0");

        var raw = startAsGuest(newIp()).andExpect(status().isCreated()).andExpect(jsonPath("$.timeLimitSec").value(1800))
                .andExpect(jsonPath("$.questions.length()").value(4));
        String text = raw.andReturn().getResponse().getContentAsString();
        // không lộ đáp án, giải thích hay token dạng hash
        org.hamcrest.MatcherAssert.assertThat(text, not(containsString("isCorrect")));
        org.hamcrest.MatcherAssert.assertThat(text, not(containsString("answerKey")));
        org.hamcrest.MatcherAssert.assertThat(text, not(containsString("BÍ MẬT")));
        var started = json.readTree(text);
        String id = started.get("attemptId").asText();
        String token = started.get("guestToken").asText();
        assertEquals(bank, jdbc.queryForObject("select bank_id from placement_attempts where id = ?", Long.class, Long.valueOf(id)));
        assertEquals(64, jdbc.queryForObject("select guest_token_hash from placement_attempts where id = ?", String.class,
                Long.valueOf(id)).length());

        // sai/thiếu token, id lạ
        draft(id, null, null, Map.of("answers", List.of())).andExpect(status().isForbidden());
        draft(id, "sai", null, Map.of("answers", List.of())).andExpect(status().isForbidden());
        submit(id, "sai", null, Map.of("answers", List.of())).andExpect(status().isForbidden());
        draft("999999", token, null, Map.of("answers", List.of())).andExpect(status().isNotFound());
        draft(id, token, null, Map.of("answers", List.of(Map.of("questionId", 1, "answer", Map.of())))).andExpect(status().isBadRequest());

        var all = answers(started, Set.of("LISTENING", "READING")); // GRAMMAR sai
        draft(id, token, null, all).andExpect(status().isOk());
        submit(id, token, null, all).andExpect(status().isOk()).andExpect(jsonPath("$.submitted").value(true))
                .andExpect(jsonPath("$.requiresLogin").value(true)).andExpect(jsonPath("$.result").doesNotExist())
                .andExpect(content().string(not(containsString("band"))));
        submit(id, token, null, all).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("attempt_already_submitted"));

        claim(id, token, null).andExpect(status().isUnauthorized());
        var u = register(newEmail(), PASSWORD);
        claim(id, "sai", u).andExpect(status().isForbidden());
        // LISTENING 100% = 7.0, READING 100% = 7.0, GRAMMAR 0% = 3.0 -> 5.67 -> 5.5
        claim(id, token, u).andExpect(status().isOk()).andExpect(jsonPath("$.overallBand").value(5.5))
                .andExpect(jsonPath("$.skills.LISTENING.band").value(7.0)).andExpect(jsonPath("$.skills.GRAMMAR.band").value(3.0))
                .andExpect(jsonPath("$.skills.LISTENING.total").value(2))
                .andExpect(jsonPath("$.recommendedCourse.courseId").value(matching));
        claim(id, token, u).andExpect(status().isOk()); // lặp lại: idempotent
        claim(id, token, register(newEmail(), PASSWORD)).andExpect(status().isNotFound()); // đã gắn user khác

        send(get("/api/me/placement"), u.accessToken()).andExpect(status().isOk()).andExpect(jsonPath("$.result.overallBand").value(5.5));
        send(get("/api/me/recommendation"), u.accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.course.courseId").value(matching)).andExpect(jsonPath("$.reason").doesNotExist());
    }

    @Test
    void meEndpointsWhenNoPlacementYet() throws Exception {
        var u = register(newEmail(), PASSWORD);
        send(get("/api/me/placement"), u.accessToken()).andExpect(status().isOk()).andExpect(jsonPath("$.result").value((Object) null));
        send(get("/api/me/recommendation"), u.accessToken()).andExpect(status().isOk()).andExpect(jsonPath("$.course").value((Object) null))
                .andExpect(jsonPath("$.reason").value("NO_PLACEMENT"));
        mvc.perform(get("/api/me/placement")).andExpect(status().isUnauthorized());
    }

    // ---- học viên đăng nhập, làm lại ----

    @Test
    void loggedInUserSeesResultAtOnceAndRetakesFollowConfig() throws Exception {
        publishedBank();
        var u = register(newEmail(), PASSWORD);
        var first = body(startAs(u).andExpect(status().isCreated()).andExpect(jsonPath("$.guestToken").doesNotExist()));
        // đang làm dở: bắt đầu lại trả đúng lượt đó
        startAs(u).andExpect(status().isCreated()).andExpect(jsonPath("$.attemptId").value(first.get("attemptId").asLong()));
        String id = first.get("attemptId").asText();
        draft(id, null, null, Map.of("answers", List.of())).andExpect(status().isNotFound()); // chưa đăng nhập: lượt của user
        draft(id, null, register(newEmail(), PASSWORD), Map.of("answers", List.of())).andExpect(status().isNotFound()); // user khác

        submit(id, null, u, answers(first, Set.of("LISTENING", "READING", "GRAMMAR"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresLogin").value(false)).andExpect(jsonPath("$.result.overallBand").value(7.0));

        // max_retakes null: không làm lại
        startAs(u).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("placement_retakes_exhausted"))
                .andExpect(jsonPath("$.error.details.allowed").value(1));
        call(put(CMS + "/placement/config"), admin, Map.of("maxRetakes", 1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.maxRetakes").value(1));
        var second = body(startAs(u).andExpect(status().isCreated()));
        submit(second.get("attemptId").asText(), null, u, answers(second, Set.of())).andExpect(status().isOk());
        startAs(u).andExpect(status().isConflict()).andExpect(jsonPath("$.error.details.allowed").value(2));
        // lượt gần nhất là kết quả hiện hành
        send(get("/api/me/placement"), u.accessToken()).andExpect(jsonPath("$.result.overallBand").value(3.0));
    }

    @Test
    void claimBeyondRetakeLimitIsRejectedAndLeavesAttemptWithGuest() throws Exception {
        publishedBank();
        var u = register(newEmail(), PASSWORD);
        var mine = body(startAs(u));
        submit(mine.get("attemptId").asText(), null, u, answers(mine, Set.of("LISTENING"))).andExpect(status().isOk());

        var guest = body(startAsGuest(newIp()));
        String gid = guest.get("attemptId").asText();
        String token = guest.get("guestToken").asText();
        claim(gid, token, u).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("attempt_not_submitted"));
        submit(gid, token, null, answers(guest, Set.of("READING"))).andExpect(status().isOk());
        claim(gid, token, u).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("placement_retakes_exhausted"));
        assertEquals(0, count("select count(*) from placement_attempts where id = ? and user_id is not null", Long.valueOf(gid)));

        call(put(CMS + "/placement/config"), admin, Map.of("maxRetakes", 1)).andExpect(status().isOk());
        claim(gid, token, u).andExpect(status().isOk());
        assertEquals(2, count("select count(*) from placement_attempts where user_id = ?", u.id()));
    }

    // ---- giờ máy chủ ----

    @Test
    void afterDeadlinePlusGraceTheDraftIsScoredNotTheSubmittedAnswers() throws Exception {
        publishedBank();
        var started = body(startAsGuest(newIp()));
        String id = started.get("attemptId").asText();
        String token = started.get("guestToken").asText();
        draft(id, token, null, answers(started, Set.of("LISTENING"))).andExpect(status().isOk()); // nháp: chỉ Listening đúng

        clock.set(Instant.now().plusSeconds(1800 + 31));
        draft(id, token, null, answers(started, Set.of("LISTENING", "READING"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("attempt_time_up"));
        submit(id, token, null, answers(started, Set.of("LISTENING", "READING", "GRAMMAR"))).andExpect(status().isOk());

        var u = register(newEmail(), PASSWORD);
        // LISTENING 7.0, READING 0% = 3.0, GRAMMAR 0% = 3.0 -> 4.33 -> 4.5
        claim(id, token, u).andExpect(jsonPath("$.overallBand").value(4.5)).andExpect(jsonPath("$.skills.READING.band").value(3.0));
    }

    @Test
    void submitInsideGraceUsesSubmittedAnswersAndJobAutoSubmitsOverdueAttempts() throws Exception {
        publishedBank();
        var started = body(startAsGuest(newIp()));
        String token = started.get("guestToken").asText();
        clock.set(Instant.now().plusSeconds(1800 + 10)); // trong ân hạn
        submit(started.get("attemptId").asText(), token, null, answers(started, Set.of("LISTENING", "READING", "GRAMMAR")))
                .andExpect(status().isOk());
        var u = register(newEmail(), PASSWORD);
        claim(started.get("attemptId").asText(), token, u).andExpect(jsonPath("$.overallBand").value(7.0));

        clock.reset();
        var late = body(startAsGuest(newIp()));
        String lateId = late.get("attemptId").asText();
        draft(lateId, late.get("guestToken").asText(), null, answers(late, Set.of("READING", "GRAMMAR"))).andExpect(status().isOk());
        timeoutJob.tick(); // chưa quá hạn: không động vào lượt này
        assertEquals(0, count("select count(*) from placement_attempts where id = ? and submitted_at is not null", Long.valueOf(lateId)));
        clock.set(Instant.now().plusSeconds(1800 + 31));
        org.junit.jupiter.api.Assertions.assertTrue(timeoutJob.tick() >= 1);
        assertEquals(1, count("select count(*) from placement_attempts where id = ? and submitted_at is not null", Long.valueOf(lateId)));
        assertEquals(0, timeoutJob.tick()); // đã nộp hết: không nộp lại
        var u2 = register(newEmail(), PASSWORD);
        // READING 7.0, GRAMMAR 7.0, LISTENING 0% = 3.0 -> 5.67 -> 5.5
        claim(lateId, late.get("guestToken").asText(), u2).andExpect(jsonPath("$.overallBand").value(5.5));
    }

    // ---- đồng thời, chống lạm dụng ----

    @Test
    void parallelSubmitsCountOnce() throws Exception {
        publishedBank();
        var started = body(startAsGuest(newIp()));
        String id = started.get("attemptId").asText();
        String token = started.get("guestToken").asText();
        var all = answers(started, Set.of("LISTENING"));
        var pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                calls.add(() -> submit(id, token, null, all).andReturn().getResponse().getStatus());
            }
            var codes = new ArrayList<Integer>();
            for (var f : pool.invokeAll(calls)) {
                codes.add(f.get());
            }
            assertEquals(1, codes.stream().filter(c -> c == 200).count(), codes.toString());
            assertEquals(3, codes.stream().filter(c -> c == 409).count(), codes.toString());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void guestStartsAreRateLimitedPerIp() throws Exception {
        publishedBank();
        String ip = newIp();
        for (int i = 0; i < 5; i++) {
            startAsGuest(ip).andExpect(status().isCreated());
        }
        startAsGuest(ip).andExpect(status().isTooManyRequests());
        startAsGuest(newIp()).andExpect(status().isCreated());
    }

    @Test
    void noActiveBankMeansNotFound() throws Exception {
        jdbc.update("update question_banks set status = 'DRAFT' where purpose = 'PLACEMENT'");
        startAsGuest(newIp()).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("placement_unavailable"));
    }

    // ---- dải band của khóa (FR-GOAL-03) ----

    @Test
    void courseBandRangeIsValidatedAndDrivesTheRecommendation() throws Exception {
        long c = insert("courses", Map.of("slug", "pl-" + UUID.randomUUID(), "title", "K", "status", "PUBLISHED"));
        var url = patch(CMS + "/courses/" + c);
        call(url, admin, Map.of("bandFrom", 5.0, "bandTo", 5.0)).andExpect(status().isBadRequest());
        call(patch(CMS + "/courses/" + c), admin, Map.of("bandFrom", 6.0, "bandTo", 5.0)).andExpect(status().isBadRequest());
        call(patch(CMS + "/courses/" + c), admin, Map.of("bandFrom", 4.25, "bandTo", 5.0)).andExpect(status().isBadRequest());
        call(patch(CMS + "/courses/" + c), admin, Map.of("bandFrom", 4.0)).andExpect(status().isBadRequest()); // thiếu một đầu
        call(patch(CMS + "/courses/" + c), admin, Map.of("bandFrom", 3.5, "bandTo", 5)).andExpect(status().isOk())
                .andExpect(jsonPath("$.bandFrom").value(3.5)).andExpect(jsonPath("$.bandTo").value(5.0));
        call(patch(CMS + "/courses/" + c), admin, Map.of("bandTo", 4.0)).andExpect(status().isOk()); // đổi một đầu, đầu kia giữ
        call(patch(CMS + "/courses/" + c), admin, Map.of("bandTo", 3.0)).andExpect(status().isBadRequest());
        call(patch(CMS + "/courses/" + c), admin, Map.of("clearBand", true)).andExpect(status().isOk())
                .andExpect(jsonPath("$.bandFrom").doesNotExist());
    }

    @Autowired com.pps.smartenglish.placement.service.CourseRecommender recommender;

    @Test
    void recommenderPicksRangeContainingBandOtherwiseTopOrNothing() {
        long low = course("PUBLISHED", "3.0", "5.0");
        long mid = course("PUBLISHED", "5.0", "6.5");
        long top = course("PUBLISHED", "6.5", "8.0");
        course("DRAFT", "0.0", "3.0");
        var band = (java.util.function.Function<String, Long>) v -> recommender.recommend(new java.math.BigDecimal(v))
                .map(r -> r.courseId()).orElse(null);
        assertEquals(low, band.apply("3.0"));
        assertEquals(low, band.apply("4.5"));
        assertEquals(mid, band.apply("5.0")); // biên: band_from <= band < band_to
        assertEquals(top, band.apply("7.5"));
        assertEquals(top, band.apply("8.0")); // bằng band_to lớn nhất: lấy khóa cao nhất
        assertEquals(top, band.apply("9.0"));
        assertEquals(null, band.apply("2.5")); // dưới mọi dải (khóa DRAFT không tính)
    }
}
