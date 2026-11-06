package com.pps.smartenglish.ai.worker;

import com.pps.smartenglish.ai.domain.AiJob;
import com.pps.smartenglish.ai.domain.AiJobKind;
import com.pps.smartenglish.ai.dto.ScaffoldDraft;
import com.pps.smartenglish.ai.provider.AiCall;
import com.pps.smartenglish.ai.provider.AiCallException;
import com.pps.smartenglish.ai.provider.AiGradingProvider;
import com.pps.smartenglish.ai.service.Prompts;
import com.pps.smartenglish.ai.service.WordStats;
import com.pps.smartenglish.ai.service.WritingSessionUpdater;
import org.springframework.stereotype.Component;

/** Bản nháp tiếng Anh từ các câu trả lời (không trừ lượt); kết quả lưu vào session.draft. */
@Component
class ScaffoldDraftHandler implements AiJobHandler {

    private static final int MAX_TOKENS = 1500;
    private static final int MIN_WORDS = 50;

    private final AiGradingProvider provider;
    private final Prompts prompts;
    private final PromptBuilder builder;
    private final WritingSessionUpdater updater;

    ScaffoldDraftHandler(AiGradingProvider provider, Prompts prompts, PromptBuilder builder, WritingSessionUpdater updater) {
        this.provider = provider;
        this.prompts = prompts;
        this.builder = builder;
        this.updater = updater;
    }

    @Override
    public AiJobKind kind() {
        return AiJobKind.SCAFFOLD_DRAFT;
    }

    @Override
    public Completion execute(AiJob job) throws AiCallException {
        var in = job.getInput();
        var result = provider.complete(new AiCall<>(prompts.scaffoldDraft(), builder.draft(in), ScaffoldDraft.class, MAX_TOKENS));
        String draft = result.data().draft();
        // bản nháp cụt (chỉ có mở bài) là output lỗi tạm thời: thử lại, không lưu
        if (draft == null || WordStats.count(draft) < MIN_WORDS) {
            throw new AiCallException(true, "Bản nháp quá ngắn hoặc trống", result.usage(), null);
        }
        long sessionId = in.path("sessionId").asLong();
        return new Completion(result.data(), result.usage(), j -> updater.applyDraft(sessionId, draft));
    }
}
