package com.pps.smartenglish.admin.port;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Đọc cột của truy vấn native theo vị trí. Không dùng interface projection vì tên cột bị H2 viết hoa còn
 * PostgreSQL viết thường; kiểu số/ngày cũng khác nhau giữa hai CSDL.
 */
public final class ReportRows {

    private ReportRows() {}

    public static LocalDate date(Object o) {
        return o instanceof java.sql.Date d ? d.toLocalDate() : o instanceof LocalDate l ? l : LocalDate.parse(o.toString());
    }

    /** SUM trên tập rỗng trả null, coi là 0. */
    public static long number(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }

    public static BigDecimal decimal(Object o) {
        return o == null ? BigDecimal.ZERO : o instanceof BigDecimal b ? b : new BigDecimal(o.toString());
    }
}
