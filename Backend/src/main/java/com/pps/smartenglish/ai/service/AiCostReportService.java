package com.pps.smartenglish.ai.service;

import com.pps.smartenglish.admin.port.AiCostReportSource;
import com.pps.smartenglish.admin.port.ReportRange;
import com.pps.smartenglish.admin.port.ReportRows;
import com.pps.smartenglish.ai.repository.TokenUsageRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cài đặt port chi phí AI của admin (FR-ADM-03, FR-AI-04). */
@Service
class AiCostReportService implements AiCostReportSource {

    private final TokenUsageRepository usages;

    AiCostReportService(TokenUsageRepository usages) {
        this.usages = usages;
    }

    @Override
    @Transactional(readOnly = true)
    public AiCostUsage aiCost(ReportRange range) {
        List<Object[]> rows = usages.usageByKind(range.start(), range.end());
        List<KindUsage> kinds = rows.stream()
                .map(r -> new KindUsage((String) r[0], ReportRows.number(r[1]), ReportRows.decimal(r[2])))
                .toList();
        return new AiCostUsage(sum(rows, 3), sum(rows, 4), sum(rows, 5), sum(rows, 6), kinds);
    }

    private static long sum(List<Object[]> rows, int column) {
        return rows.stream().mapToLong(r -> ReportRows.number(r[column])).sum();
    }
}
