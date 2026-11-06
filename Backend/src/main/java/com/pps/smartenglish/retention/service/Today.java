package com.pps.smartenglish.retention.service;

import com.pps.smartenglish.retention.config.RetentionProperties;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/** "Hôm nay" của mọi người dùng theo một múi giờ cấu hình (Q-18), tính từ Clock chung. */
@Component
public class Today {

    private final Clock clock;
    private final RetentionProperties props;

    Today(Clock clock, RetentionProperties props) {
        this.clock = clock;
        this.props = props;
    }

    public LocalDate date() {
        return LocalDate.now(clock.withZone(props.zone()));
    }
}
