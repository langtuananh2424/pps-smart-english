package com.pps.smartenglish.home.service.cam;

import com.pps.smartenglish.profile.dto.StatsResponse;
import java.time.ZonedDateTime;
import java.util.Optional;

/** Dữ kiện đã tải sẵn một lần cho mọi CamRule của một lượt gọi. now theo giờ Việt Nam. */
public record CamContext(long userId, String displayName, ZonedDateTime now, StatsResponse stats, long unread,
                         Optional<ExpiringCourse> expiring, Optional<OpenAttempt> openAttempt) {

    /** Khóa còn hạn gần nhất sẽ hết trong ngưỡng nhắc; daysLeft làm tròn lên, tối thiểu 1. */
    public record ExpiringCourse(String slug, String title, long daysLeft) {}

    /** Lượt làm dở; remainingSec null khi bộ không giới hạn thời gian. */
    public record OpenAttempt(long bankId, String bankTitle, Long remainingSec) {}
}
