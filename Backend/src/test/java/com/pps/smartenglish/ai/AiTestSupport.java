package com.pps.smartenglish.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.ai.provider.FakeAiGradingProvider;
import com.pps.smartenglish.ai.service.AiGuard;
import com.pps.smartenglish.ai.worker.AiJobRunner;
import com.pps.smartenglish.support.ApiTestBase;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Dữ liệu và thao tác chung cho test module ai: bank + câu hỏi, người dùng có lượt, chạy job đúng lúc. */
abstract class AiTestSupport extends ApiTestBase {

    protected static final String PASSWORD = "Password123!";

    @Autowired protected FakeAiGradingProvider fake;
    @Autowired protected AiJobRunner runner;
    @Autowired protected AiGuard guard;

    /** Job còn dang dở của test khác không được chen vào lượt runDueNow của test này. */
    @BeforeEach
    void cleanQueue() {
        fake.reset();
        jdbc.update("update ai_jobs set status = 'FAILED', locked_until = null where status in ('PENDING', 'RUNNING')");
    }

    protected long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    /** Bank trong topic học thử (mở cho mọi học viên) hoặc topic khóa. */
    protected long bank(boolean topicFree) {
        long c = insert("courses", Map.of("slug", "ai-" + UUID.randomUUID(), "title", "IELTS 3.0 → 5.0", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "Level A"));
        long t = insert("topics", Map.of("level_id", lv, "title", "Topic", "is_free_trial", topicFree));
        return insert("question_banks", Map.of("topic_id", t, "title", "Bộ", "is_trial", false));
    }

    /** json trên H2 phải chèn bằng FORMAT JSON, nếu không sẽ thành chuỗi JSON. */
    protected long question(long bankId, String type, int order, String prompt, String payload, String key) {
        var kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("insert into questions (bank_id, type, prompt, payload, answer_key, sort_order)"
                    + " values (?, ?, ?, ? format json, ? format json, ?)", new String[] {"id"});
            ps.setLong(1, bankId);
            ps.setString(2, type);
            ps.setString(3, prompt);
            ps.setString(4, payload);
            ps.setString(5, key);
            ps.setInt(6, order);
            return ps;
        }, kh);
        return kh.getKey().longValue();
    }

    protected long writingQuestion(long bankId) {
        return question(bankId, "WRITING", 1, "Bạn nghĩ học trực tuyến có tốt hơn học ở lớp không?",
                "{\"task\":\"Some people think online learning is better than learning in a classroom. Do you agree or disagree?\",\"requiredWords\":100}", "{}");
    }

    protected Account student(int quota) throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("update users set email_verified_at = CURRENT_TIMESTAMP where id = ?", u.id());
        jdbc.update("update user_ai_quota set balance = ? where user_id = ?", quota, u.id());
        return u;
    }

    protected void addonProduct() {
        var cols = new HashMap<String, Object>(Map.of("type", "AI_ADDON", "name", "Nạp 20 lượt " + UUID.randomUUID(),
                "price_vnd", 99000L, "status", "ACTIVE"));
        cols.put("access_days", null);
        cols.put("ai_quota", 20);
        insert("products", cols);
    }

    protected int balance(Account u) {
        return count("select balance from user_ai_quota where user_id = ?", u.id());
    }

    protected ResultActions postJson(String url, Account u, Object body, String idempotencyKey) throws Exception {
        var req = MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if (idempotencyKey != null) {
            req.header("Idempotency-Key", idempotencyKey);
        }
        return u == null ? mvc.perform(req) : send(req, u.accessToken());
    }

    protected ResultActions postJson(String url, Account u, Object body) throws Exception {
        return postJson(url, u, body, null);
    }

    protected ResultActions getJson(String url, Account u) throws Exception {
        return send(MockMvcRequestBuilders.get(url), u.accessToken());
    }

    protected long idOf(ResultActions r, String field) throws Exception {
        JsonNode n = json.readTree(r.andReturn().getResponse().getContentAsString());
        return n.get(field).asLong();
    }

    protected long newSession(Account u, long questionId) throws Exception {
        guard.resetRateLimit(u.id());
        return idOf(postJson("/api/writing/sessions", u, Map.of("questionId", questionId)), "sessionId");
    }
}
