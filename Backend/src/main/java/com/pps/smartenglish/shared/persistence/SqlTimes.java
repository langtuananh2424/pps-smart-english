package com.pps.smartenglish.shared.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;

/** Đổi giá trị cột thời gian của truy vấn native sang Instant: driver H2 và PostgreSQL trả kiểu khác nhau. Null giữ nguyên null. */
public final class SqlTimes {

    private SqlTimes() {}

    public static Instant instant(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Instant i) {
            return i;
        }
        if (o instanceof OffsetDateTime odt) {
            return odt.toInstant();
        }
        return ((Timestamp) o).toInstant();
    }
}
