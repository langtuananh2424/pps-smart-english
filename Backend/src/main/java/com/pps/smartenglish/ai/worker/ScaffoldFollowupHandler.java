package com.pps.smartenglish.ai.worker;

import com.pps.smartenglish.ai.domain.AiJob;
import com.pps.smartenglish.ai.domain.AiJobKind;
import com.pps.smartenglish.ai.dto.ScaffoldFollowup;
import com.pps.smartenglish.ai.provider.AiCall;
import com.pps.smartenglish.ai.provider.AiCallException;
import com.pps.smartenglish.ai.provider.AiGradingProvider;
import com.pps.smartenglish.ai.service.Prompts;
import com.pps.smartenglish.ai.service.WritingSessionUpdater;
import org.springframework.stereotype.Component;

/** Câu hỏi đào sâu tiếp theo (không trừ lượt). Chưa đủ MIN câu thì bỏ qua `done` của model (WritingService.MIN_FOLLOWUPS). */
@Component
class ScaffoldFollowupHandler implements AiJobHandler {

    private static final int MIN_FOLLOWUPS = 2;
    private static final int MAX_TOKENS = 400;

    private final AiGradingProvider provider;
    private final Prompts prompts;
    private final PromptBuilder builder;
    private final WritingSessionUpdater updater;

    ScaffoldFollowupHandler(AiGradingProvider provider, Prompts prompts, PromptBuilder builder, WritingSessionUpdater updater) {
        this.provider = provider;
        this.prompts = prompts;
        this.builder = builder;
        this.updater = updater;
    }

    @Override
    public AiJobKind kind() {
        return AiJobKind.SCAFFOLD_FOLLOWUP;
    }

    @Override
    public Completion execute(AiJob job) throws AiCallException {
        var in = job.getInput();
        var result = provider.complete(new AiCall<>(prompts.scaffoldFollowup(), builder.followup(in), ScaffoldFollowup.class, MAX_TOKENS));
        boolean done = result.data().done() && in.path("asked").asInt() >= MIN_FOLLOWUPS;
        String question = result.data().question();
        if (!done && (question == null || question.isBlank())) {
            throw new AiCallException(true, "Mô hình không đưa ra câu hỏi tiếp theo", result.usage(), null);
        }
        long sessionId = in.path("sessionId").asLong();
        return new Completion(new ScaffoldFollowup(done ? null : question, done), result.usage(),
                j -> updater.applyFollowup(sessionId, done, question));
    }
}
