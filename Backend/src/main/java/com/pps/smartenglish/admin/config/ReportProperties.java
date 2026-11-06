package com.pps.smartenglish.admin.config;

import java.math.BigDecimal;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình app.reports.*. usdVndRate không có mặc định: tỷ giá là số liệu tài chính nên chỉ dùng khi người vận hành
 * khai báo; để trống thì báo cáo không quy đổi sang VND.
 */
@ConfigurationProperties(prefix = "app.reports")
public record ReportProperties(@DefaultValue("Asia/Ho_Chi_Minh") ZoneId zone, BigDecimal usdVndRate) {

    public ReportProperties {
        if (usdVndRate != null && usdVndRate.signum() <= 0) {
            throw new IllegalArgumentException("REPORT_USD_VND_RATE phải lớn hơn 0 (để trống nếu không quy đổi)");
        }
    }
}
