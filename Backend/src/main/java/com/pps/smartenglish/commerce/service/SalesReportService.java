package com.pps.smartenglish.commerce.service;

import com.pps.smartenglish.admin.dto.DashboardResponse.ConversionSection;
import com.pps.smartenglish.admin.dto.DashboardResponse.ProductRevenue;
import com.pps.smartenglish.admin.dto.DashboardResponse.RevenueDay;
import com.pps.smartenglish.admin.dto.DashboardResponse.RevenueSection;
import com.pps.smartenglish.admin.port.ReportRange;
import com.pps.smartenglish.admin.port.ReportRows;
import com.pps.smartenglish.admin.port.SalesReportSource;
import com.pps.smartenglish.commerce.repository.OrderRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cài đặt port báo cáo bán hàng của admin (FR-ADM-03). */
@Service
class SalesReportService implements SalesReportSource {

    private final OrderRepository orders;

    SalesReportService(OrderRepository orders) {
        this.orders = orders;
    }

    @Override
    @Transactional(readOnly = true)
    public RevenueSection revenue(ReportRange range) {
        List<ProductRevenue> products = orders.revenueByProduct(range.start(), range.end()).stream()
                .map(r -> new ProductRevenue(ReportRows.number(r[0]), (String) r[1], (String) r[2], ReportRows.number(r[3]),
                        ReportRows.number(r[4])))
                .toList();
        List<RevenueDay> daily = orders.revenueByDay(range.zone().getId(), range.start(), range.end()).stream()
                .map(r -> new RevenueDay(ReportRows.date(r[0]), ReportRows.number(r[1])))
                .toList();
        return new RevenueSection(products.stream().mapToLong(ProductRevenue::revenueVnd).sum(), products, daily);
    }

    @Override
    @Transactional(readOnly = true)
    public ConversionSection conversion(ReportRange range) {
        Object[] r = orders.conversionCounts(range.start(), range.end()).get(0);
        long cohort = ReportRows.number(r[0]);
        long paid = ReportRows.number(r[1]);
        BigDecimal rate = cohort == 0 ? null
                : BigDecimal.valueOf(paid).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(cohort), 2, RoundingMode.HALF_UP);
        return new ConversionSection(cohort, paid, rate, ReportRows.number(r[2]));
    }
}
