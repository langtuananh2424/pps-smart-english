package com.pps.smartenglish.ai.worker;

import com.pps.smartenglish.ai.domain.AiJob;
import com.pps.smartenglish.ai.domain.AiJobKind;
import com.pps.smartenglish.ai.dto.WritingGrade;
import com.pps.smartenglish.ai.provider.AiCall;
import com.pps.smartenglish.ai.provider.AiCallException;
import com.pps.smartenglish.ai.provider.AiGradingProvider;
import com.pps.smartenglish.ai.service.Prompts;
import com.pps.smartenglish.ai.service.WritingPostCheck;
import org.springframework.stereotype.Component;

/** Chấm bài viết theo rubric PPS (FR-AI-02). Số liệu đếm từ có sẵn trong input, điểm tổng do code tính. */
@Component
class WritingGradeHandler implements AiJobHandler {

    private static final int MIN_TOKENS = 2000;

    private final AiGradingProvider provider;
    private final Prompts prompts;
    private final PromptBuilder builder;
    private final WritingPostCheck postCheck;

    WritingGradeHandler(AiGradingProvider provider, Prompts prompts, PromptBuilder builder, WritingPostCheck postCheck) {
        this.provider = provider;
        this.prompts = prompts;
        this.builder = builder;
        this.postCheck = postCheck;
    }

    @Override
    public AiJobKind kind() {
        return AiJobKind.WRITING_GRADE;
    }

    @Override
    public Completion execute(AiJob job) throws AiCallException {
        var in = job.getInput();
        String text = in.path("text").asText();
        int words = in.path("wordCount").asInt();
        // đầu ra gồm bài đánh dấu + bản sửa (mỗi thứ cỡ bài) cộng phần phân tích
        int maxTokens = Math.max(MIN_TOKENS, words * 3 + 1500);
        var result = provider.complete(new AiCall<>(prompts.writingRubric(), builder.grading(in), WritingGrade.class, maxTokens));
        if (result.data().connectives() == null) {
            throw new AiCallException(true, "Kết quả chấm thiếu phần từ nối", result.usage(), null);
        }
        return Completion.of(postCheck.apply(result.data(), text, words, in.path("copiedWords").asInt()), result.usage());
    }
}
