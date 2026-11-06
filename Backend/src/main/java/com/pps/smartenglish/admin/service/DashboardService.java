package com.pps.smartenglish.admin.service;

import com.pps.smartenglish.admin.config.ReportProperties;
import com.pps.smartenglish.admin.dto.DashboardResponse;
import com.pps.smartenglish.admin.dto.DashboardResponse.AiCostSection;
import com.pps.smartenglish.admin.dto.DashboardResponse.AiKindCost;
import com.pps.smartenglish.admin.port.AiCostReportSource;
import com.pps.smartenglish.admin.port.AiCostReportSource.AiCostUsage;
import com.pps.smartenglish.admin.port.ReportRange;
import com.pps.smartenglish.admin.port.SalesReportSource;
import com.pps.smartenglish.admin.port.UserReportSource;
import com.pps.smartenglish.shared.error.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;

/** FR-ADM-03: ghép số liệu từ các port thành một báo cáo, có cache 1 phút. */
@Service
public class DashboardService {

    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 366;

    private final UserReportSource users;
    private final SalesReportSource sales;
    private final AiCostReportSource aiCost;
    private final DashboardCache cache;
    private final ReportProperties props;
    private final Clock clock;

    DashboardService(UserReportSource users, SalesReportSource sales, AiCostReportSource aiCost, DashboardCache cache,
                     ReportProperties props, Clock clock) {
        this.users = users;
        this.sales = sales;
        this.aiCost = aiCost;
        this.cache = cache;
        this.props = props;
        this.clock = clock;
    }

    /** from/to dạng YYYY-MM-DD hoặc null. Thiếu một đầu thì lấy đầu kia ± 29 ngày; thiếu cả hai thì 30 ngày tính đến hôm nay. */
    public DashboardResponse dashboard(String fromRaw, String toRaw) {
        LocalDate from = parse("from", fromRaw);
        LocalDate to = parse("to", toRaw);
        if (to == null) {
            to = from != null ? from.plusDays(DEFAULT_DAYS - 1) : LocalDate.now(clock.withZone(props.zone()));
        }
        if (from == null) {
            from = to.minusDays(DEFAULT_DAYS - 1);
        }
        if (from.isAfter(to)) {
            throw ApiException.validation("from", "from phải trước hoặc bằng to");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw ApiException.validation("to", "Khoảng thời gian tối đa " + MAX_DAYS + " ngày");
        }

        String key = "report:dashboard:" + from + ":" + to;
        var cached = cache.get(key);
        if (cached.isPresent()) {
            return cached.get();
        }
        var response = build(from, to);
        cache.put(key, response);
        return response;
    }

    private DashboardResponse build(LocalDate from, LocalDate to) {
        // nửa mở [from 00:00, to+1 00:00) theo múi giờ báo cáo, đổi sang Instant ở đây thay vì trong SQL
        var zone = props.zone();
        var range = new ReportRange(from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant(), zone);
        return new DashboardResponse(from, to, zone.getId(), Instant.now(clock), users.users(range), sales.conversion(range),
                sales.revenue(range), toSection(aiCost.aiCost(range)));
    }

    private AiCostSection toSection(AiCostUsage usage) {
        BigDecimal rate = props.usdVndRate();
        var kinds = usage.kinds().stream()
                .map(k -> new AiKindCost(k.kind(), k.jobs(), k.costUsd(), toVnd(k.costUsd(), rate)))
                .toList();
        BigDecimal total = usage.totalUsd();
        return new AiCostSection(total, toVnd(total, rate), rate, usage.inputTokens(), usage.outputTokens(),
                usage.cacheReadTokens(), usage.cacheWriteTokens(), kinds);
    }

    /** null khi chưa khai báo tỷ giá: không tự bịa tỷ giá. */
    private static Long toVnd(BigDecimal usd, BigDecimal rate) {
        return rate == null ? null : usd.multiply(rate).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static LocalDate parse(String field, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.validation(field, "Ngày phải có dạng YYYY-MM-DD");
        }
    }
}
