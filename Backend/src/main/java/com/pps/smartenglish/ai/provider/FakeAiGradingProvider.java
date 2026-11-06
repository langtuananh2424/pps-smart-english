package com.pps.smartenglish.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pps.smartenglish.ai.dto.RewriteGrades;
import com.pps.smartenglish.ai.dto.ScaffoldDraft;
import com.pps.smartenglish.ai.dto.ScaffoldFollowup;
import com.pps.smartenglish.ai.dto.WritingGrade;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Nhà cung cấp giả cho test và chạy local: kết quả cố định, tất định, token giả bằng 0.
 * Có hai móc cho test: failNext (giả lỗi) và override (ép output), cùng lastUserPrompt để kiểm prompt.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "fake")
public class FakeAiGradingProvider implements AiGradingProvider {

    public static final String PROVIDER = "fake";
    /** Bản nháp giả đủ dài (trên 50 từ) để qua kiểm tra của ScaffoldDraftHandler. */
    public static final String DRAFT = "Online learning has become very popular in recent years, and many students now prefer it "
            + "to traditional classrooms. First, it is flexible, so learners can study at any time and in any place. "
            + "Second, it often costs less than attending a school in person. However, students need strong discipline "
            + "to finish their lessons without a teacher watching them. In conclusion, I believe online learning is a "
            + "useful choice when learners manage their time well.";
    private static final AiUsage ZERO = new AiUsage(PROVIDER, "fake", 0, 0, 0, 0);

    private final ObjectMapper mapper;
    private final Map<Class<?>, Object> overrides = new ConcurrentHashMap<>();
    private final AtomicInteger failures = new AtomicInteger();
    private final AtomicInteger calls = new AtomicInteger();
    private volatile AiCallException failure;
    private volatile String lastUserPrompt;

    FakeAiGradingProvider(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** Hai lời gọi kế tiếp (times) ném lỗi này. */
    public void failNext(int times, AiCallException e) {
        failure = e;
        failures.set(times);
    }

    /** Ép output cho kiểu này tới khi reset(). */
    public <T> void override(Class<T> type, T value) {
        overrides.put(type, value);
    }

    public void reset() {
        overrides.clear();
        failures.set(0);
        failure = null;
        calls.set(0);
        lastUserPrompt = null;
    }

    public String lastUserPrompt() { return lastUserPrompt; }

    public int calls() { return calls.get(); }

    @Override
    @SuppressWarnings("unchecked")
    public <T> AiResult<T> complete(AiCall<T> call) throws AiCallException {
        calls.incrementAndGet();
        lastUserPrompt = call.userPrompt();
        if (failures.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
            throw failure;
        }
        Object forced = overrides.get(call.resultType());
        if (forced != null) {
            return new AiResult<>((T) forced, ZERO);
        }
        return new AiResult<>((T) defaultFor(call), ZERO);
    }

    private Object defaultFor(AiCall<?> call) {
        Class<?> type = call.resultType();
        if (type == ScaffoldFollowup.class) {
            return new ScaffoldFollowup("Bạn có thể cho một ví dụ cụ thể không?", false);
        }
        if (type == ScaffoldDraft.class) {
            return new ScaffoldDraft(DRAFT);
        }
        String delimited = between(call.userPrompt());
        if (type == RewriteGrades.class) {
            return rewriteGrades(delimited);
        }
        if (type == WritingGrade.class) {
            return new WritingGrade(83, 77, 71, 64, List.of(), delimited,
                    new WritingGrade.Connectives(0, 0, 0, List.of(), "Chưa có từ nối."), List.of(), null, "Fake feedback.");
        }
        throw new IllegalArgumentException("Fake chưa hỗ trợ " + type);
    }

    /** Mục nào có câu trả lời chứa "wrong" thì chấm sai, còn lại đúng. */
    private RewriteGrades rewriteGrades(String json) {
        try {
            List<RewriteGrades.Item> out = new ArrayList<>();
            for (JsonNode n : mapper.readTree(json)) {
                String answer = n.path("answer").asText();
                boolean ok = !answer.toLowerCase().contains("wrong");
                out.add(new RewriteGrades.Item(n.path("id").asLong(), ok, ok ? "Tốt." : "Chưa đúng.", answer));
            }
            return new RewriteGrades(out);
        } catch (Exception e) {
            throw new IllegalArgumentException("Prompt chấm viết lại không phải JSON", e);
        }
    }

    /** Nội dung giữa cặp dấu === đầu tiên (đúng quy ước dữ liệu của mọi prompt). */
    private static String between(String prompt) {
        int a = prompt.indexOf("===");
        int b = prompt.indexOf("===", a + 3);
        return a < 0 || b < 0 ? "" : prompt.substring(a + 3, b).strip();
    }
}
