package com.pps.smartenglish.exercise;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.ai.AiTestSupport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Dữ liệu chung cho test #69: bộ Listening hai part (mỗi part 2 câu MCQ, có audio và script), chấm theo cờ đúng/sai. */
abstract class ListeningTestSupport extends AiTestSupport {

    static final String SECRET_EXPLANATION = "GIAI-THICH-BI-MAT";

    /** q: bốn câu theo thứ tự (g1: q0,q1; g2: q2,q3). */
    record World(long course, long topic, long bank, long g1, long g2, long[] q, long[] right, long[] wrong) {}

    protected long anyUser() {
        Long id = jdbc.queryForObject("select min(id) from users", Long.class);
        if (id != null) {
            return id;
        }
        try {
            return register(newEmail(), PASSWORD).id();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Audio READY dài durationMs; storage_key duy nhất. */
    protected long audioAsset(Integer durationMs) {
        var cols = new HashMap<String, Object>(Map.of("kind", "AUDIO", "status", "READY", "storage_key", "media/audio/" + UUID.randomUUID(),
                "mime", "audio/mpeg", "size_bytes", 1000L, "uploaded_by", anyUser()));
        cols.put("duration_ms", durationMs);
        return insert("media_assets", cols);
    }

    /** playbackMode FREE/EXAM/LOCKED; playLimit chỉ có nghĩa với EXAM. Topic học thử nên mọi học viên đều vào được. */
    protected World world(String playbackMode, Integer playLimit) {
        long c = insert("courses", Map.of("slug", "ls-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "A", "status", "PUBLISHED"));
        long t = insert("topics", Map.of("level_id", lv, "title", "Unit nghe", "is_free_trial", true, "status", "PUBLISHED"));
        var bankCols = new HashMap<String, Object>(Map.of("topic_id", t, "title", "Listening", "is_trial", false, "status", "PUBLISHED",
                "playback_mode", playbackMode));
        bankCols.put("play_limit", playLimit);
        long b = insert("question_banks", bankCols);
        long g1 = group(b, 1, "Part 1");
        long g2 = group(b, 2, "Part 2");
        long[] q = new long[4];
        long[] right = new long[4];
        long[] wrong = new long[4];
        for (int i = 0; i < 4; i++) {
            q[i] = question(b, "CONNECTIVE_MCQ", i + 1, "Câu " + i, "{}", "{}");
            jdbc.update("update questions set group_id = ?, explanation = ? where id = ?", i < 2 ? g1 : g2, SECRET_EXPLANATION + i, q[i]);
            right[i] = insert("question_choices", Map.of("question_id", q[i], "label", "B", "content", "đúng", "is_correct", true));
            wrong[i] = insert("question_choices", Map.of("question_id", q[i], "label", "A", "content", "sai", "is_correct", false));
        }
        return new World(c, t, b, g1, g2, q, right, wrong);
    }

    private long group(long bank, int order, String title) {
        var cols = new HashMap<String, Object>(Map.of("bank_id", bank, "sort_order", order, "title", title));
        cols.put("audio_asset_id", audioAsset(1000));
        cols.put("transcript_html", "<p>SCRIPT-PART-" + order + "</p>");
        return insert("question_groups", cols);
    }

    protected long startAttempt(Account u, World w) throws Exception {
        return idOf(postJson("/api/banks/" + w.bank() + "/attempts", u, Map.of()).andExpect(status().isCreated()), "id");
    }

    protected List<Map<String, Object>> answers(World w, boolean... correct) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < correct.length; i++) {
            out.add(Map.of("questionId", w.q()[i], "answer", Map.of("choiceId", correct[i] ? w.right()[i] : w.wrong()[i])));
        }
        return out;
    }

    /** Bắt đầu và nộp một lượt; trả nội dung phản hồi nộp bài. */
    protected JsonNode submit(Account u, World w, boolean... correct) throws Exception {
        return submitWithId(u, w, correct).body();
    }

    record Submitted(long id, JsonNode body) {}

    protected Submitted submitWithId(Account u, World w, boolean... correct) throws Exception {
        long id = startAttempt(u, w);
        String body = postJson("/api/attempts/" + id + "/submit", u, Map.of("answers", answers(w, correct)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Submitted(id, json.readTree(body));
    }

    protected JsonNode get(String url, Account u, int expected) throws Exception {
        return json.readTree(getJson(url, u).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    protected ResultActions play(Account u, long attempt, long group) throws Exception {
        return postJson("/api/attempts/" + attempt + "/groups/" + group + "/play", u, Map.of());
    }

    protected ResultActions playDone(Account u, long attempt, long group) throws Exception {
        return postJson("/api/attempts/" + attempt + "/groups/" + group + "/play/complete", u, Map.of());
    }

    protected ResultActions put(String url, Account u, Object body) throws Exception {
        return send(MockMvcRequestBuilders.put(url).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)), u.accessToken());
    }

    protected Account withRole(String role) throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = ?", u.id(), role);
        return login(u.email(), PASSWORD);
    }
}
