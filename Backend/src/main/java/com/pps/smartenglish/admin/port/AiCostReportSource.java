package com.pps.smartenglish.admin.port;

import java.math.BigDecimal;
import java.util.List;

/** ai cài đặt: token và chi phí USD thật của các lời gọi LLM trong kỳ (FR-AI-04). */
public interface AiCostReportSource {

    AiCostUsage aiCost(ReportRange range);

    /** Chi phí giữ USD; việc quy đổi VND thuộc về nơi dựng báo cáo vì tỷ giá là cấu hình của admin. */
    record AiCostUsage(long inputTokens, long outputTokens, long cacheReadTokens, long cacheWriteTokens, List<KindUsage> kinds) {

        public BigDecimal totalUsd() {
            return kinds.stream().map(KindUsage::costUsd).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    /** jobs = số job khác nhau có ghi token trong kỳ. */
    record KindUsage(String kind, long jobs, BigDecimal costUsd) {}
}
