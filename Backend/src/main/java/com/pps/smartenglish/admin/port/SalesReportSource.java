package com.pps.smartenglish.admin.port;

import com.pps.smartenglish.admin.dto.DashboardResponse.ConversionSection;
import com.pps.smartenglish.admin.dto.DashboardResponse.RevenueSection;

/** commerce cài đặt: doanh thu và tỷ lệ chuyển đổi. */
public interface SalesReportSource {

    /** Cộng giá lúc mua (order_items.price_vnd) của đơn PAID có paid_at trong kỳ. Chưa trừ phí/VAT, chưa có hoàn tiền. */
    RevenueSection revenue(ReportRange range);

    /** Cohort là học viên đăng ký trong kỳ; paidUsers tính đến thời điểm truy vấn, không giới hạn trong kỳ. */
    ConversionSection conversion(ReportRange range);
}
