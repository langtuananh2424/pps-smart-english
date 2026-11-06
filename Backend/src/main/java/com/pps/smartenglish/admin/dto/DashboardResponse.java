package com.pps.smartenglish.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Báo cáo tổng quan cho Admin (FR-ADM-03). Ngày theo múi giờ timezone. Tiền VNĐ là số nguyên đồng; chi phí AI giữ USD,
 * các trường VNĐ của aiCost là null khi chưa khai báo tỷ giá.
 */
public record DashboardResponse(LocalDate from, LocalDate to, String timezone, Instant generatedAt, UsersSection users,
                                ConversionSection conversion, RevenueSection revenue, AiCostSection aiCost) {

    public record UsersSection(long registered, long verified, List<UserDay> daily) {}

    public record UserDay(LocalDate date, long registered, long verified) {}

    /** paidRatePercent null khi cohort rỗng. */
    public record ConversionSection(long cohortSize, long paidUsers, BigDecimal paidRatePercent, long activationLinkUsers) {}

    public record RevenueSection(long totalVnd, List<ProductRevenue> products, List<RevenueDay> daily) {}

    public record ProductRevenue(long productId, String name, String type, long unitsSold, long revenueVnd) {}

    public record RevenueDay(LocalDate date, long revenueVnd) {}

    public record AiCostSection(BigDecimal totalUsd, Long totalVnd, BigDecimal usdVndRate, long inputTokens, long outputTokens,
                                long cacheReadTokens, long cacheWriteTokens, List<AiKindCost> kinds) {}

    public record AiKindCost(String kind, long jobs, BigDecimal costUsd, Long costVnd) {}
}
