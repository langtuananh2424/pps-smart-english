package com.pps.smartenglish.ai.worker;

import com.pps.smartenglish.ai.domain.AiJob;
import com.pps.smartenglish.ai.domain.AiJobKind;
import com.pps.smartenglish.ai.dto.RewriteGrades;
import com.pps.smartenglish.ai.provider.AiCall;
import com.pps.smartenglish.ai.provider.AiCallException;
import com.pps.smartenglish.ai.provider.AiGradingProvider;
import com.pps.smartenglish.ai.service.Prompts;
import com.pps.smartenglish.ai.service.RewriteResultSink;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Chấm gộp mọi câu viết lại của một lần nộp bằng một lời gọi (một lượt). Output thiếu/thừa/sai id so với input
 * là output lỗi: thử lại thay vì ghi điểm lệch.
 */
@Component
class RewriteGradeHandler implements AiJobHandler {

    private static final int TOKENS_PER_ITEM = 150;
    private static final int MIN_TOKENS = 600;

    private final AiGradingProvider provider;
    private final Prompts prompts;
    private final PromptBuilder builder;
    private final RewriteResultSink sink;

    RewriteGradeHandler(AiGradingProvider provider, Prompts prompts, PromptBuilder builder, RewriteResultSink sink) {
        this.provider = provider;
        this.prompts = prompts;
        this.builder = builder;
        this.sink = sink;
    }

    @Override
    public AiJobKind kind() {
        return AiJobKind.REWRITE_GRADE;
    }

    @Override
    public Completion execute(AiJob job) throws AiCallException {
        var items = job.getInput().path("items");
        int maxTokens = Math.max(MIN_TOKENS, items.size() * TOKENS_PER_ITEM);
        var result = provider.complete(new AiCall<>(prompts.rewriteGrading(), builder.rewrite(items), RewriteGrades.class, maxTokens));
        Set<Long> expected = new HashSet<>();
        items.forEach(n -> expected.add(n.path("id").asLong()));
        var graded = result.data().items();
        Set<Long> got = new HashSet<>();
        if (graded != null) {
            graded.forEach(g -> got.add(g.id()));
        }
        if (graded == null || graded.size() != expected.size() || !got.equals(expected)) {
            throw new AiCallException(true, "Kết quả chấm thiếu hoặc sai id so với đầu vào", result.usage(), null);
        }
        long attemptId = job.getAttemptId();
        return new Completion(result.data(), result.usage(), j -> sink.apply(attemptId, graded));
    }
}
