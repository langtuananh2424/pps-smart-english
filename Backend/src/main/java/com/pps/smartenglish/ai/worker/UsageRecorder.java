package com.pps.smartenglish.ai.worker;

import com.pps.smartenglish.ai.config.AiProperties;
import com.pps.smartenglish.ai.domain.AiJob;
import com.pps.smartenglish.ai.domain.TokenUsage;
import com.pps.smartenglish.ai.provider.AiUsage;
import com.pps.smartenglish.ai.repository.TokenUsageRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/** Ghi token cho mọi phản hồi có usage, kể cả output sai hoặc kết quả muộn bị bỏ (FR-AI-04): token đã tốn tiền thật. */
@Component
class UsageRecorder {

    private static final BigDecimal PER_MILLION = BigDecimal.valueOf(1_000_000);

    private final TokenUsageRepository usages;
    private final AiProperties.Price price;

    UsageRecorder(TokenUsageRepository usages, AiProperties props) {
        this.usages = usages;
        this.price = props.price();
    }

    void record(AiJob job, AiUsage u) {
        if (u == null) {
            return;
        }
        BigDecimal cost = price.input().multiply(BigDecimal.valueOf(u.inputTokens()))
                .add(price.output().multiply(BigDecimal.valueOf(u.outputTokens())))
                .add(price.cacheRead().multiply(BigDecimal.valueOf(u.cacheReadTokens())))
                .add(price.cacheWrite().multiply(BigDecimal.valueOf(u.cacheWriteTokens())))
                .divide(PER_MILLION, 6, RoundingMode.HALF_UP);
        usages.save(new TokenUsage(job.getId(), job.getUserId(), u.provider(), u.model(), u.inputTokens(), u.outputTokens(),
                u.cacheReadTokens(), u.cacheWriteTokens(), cost));
    }
}
