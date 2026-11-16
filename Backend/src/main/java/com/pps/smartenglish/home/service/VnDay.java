package com.pps.smartenglish.home.service;

import com.pps.smartenglish.retention.config.RetentionProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.stereotype.Component;

/** Ngày học theo múi giờ cấu hình của retention (Asia/Ho_Chi_Minh), tính từ Clock chung. */
@Component
class VnDay {

    private final Clock clock;
    private final ZoneId zone;

    VnDay(Clock clock, RetentionProperties props) {
        this.clock = clock;
        this.zone = props.zone();
    }

    ZonedDateTime now() {
        return ZonedDateTime.now(clock.withZone(zone));
    }

    /** Đầu ngày hôm nay (bao gồm). */
    Instant start() {
        return now().toLocalDate().atStartOfDay(zone).toInstant();
    }

    /** Đầu ngày mai (loại trừ). */
    Instant end() {
        return now().toLocalDate().plusDays(1).atStartOfDay(zone).toInstant();
    }

    boolean isToday(Instant at) {
        return at != null && !at.isBefore(start()) && at.isBefore(end());
    }
}
