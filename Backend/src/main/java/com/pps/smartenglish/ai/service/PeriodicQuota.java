package com.pps.smartenglish.ai.service;

import com.pps.smartenglish.ai.domain.AiPeriod;
import com.pps.smartenglish.ai.domain.AiPeriodUsage;
import com.pps.smartenglish.ai.repository.AiPeriodUsageRepository;
import com.pps.smartenglish.catalog.domain.Course;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Lượt định kỳ theo khóa (D-31, D-47): không có job nạp lại, kỳ mới là dòng usage mới; còn lại = quota hiện tại - used. */
@Component
class PeriodicQuota {

    private final AiPeriodUsageRepository usage;
    private final Clock clock;
    private final ZoneId zone;

    PeriodicQuota(AiPeriodUsageRepository usage, Clock clock, @Value("${app.ai.quota-zone:Asia/Ho_Chi_Minh}") String zone) {
        this.usage = usage;
        this.clock = clock;
        this.zone = ZoneId.of(zone);
    }

    static AiPeriod periodOf(Course c) {
        return AiPeriod.valueOf(c.getAiPeriod());
    }

    /** Ngày đầu kỳ hiện tại của khóa, giờ Việt Nam. */
    LocalDate currentStart(Course c) {
        return periodOf(c).start(LocalDate.ofInstant(Instant.now(clock), zone));
    }

    Instant resetsAt(Course c) {
        return periodOf(c).next(currentStart(c)).atStartOfDay(zone).toInstant();
    }

    int used(long userId, Course c) {
        return usage.findUsed(userId, c.getId(), currentStart(c)).orElse(0);
    }

    /** Đổi quota giữa kỳ áp dụng ngay: max(0, quota mới - used). */
    int remaining(long userId, Course c) {
        return Math.max(0, c.getAiPeriodQuota() - used(userId, c));
    }

    /** Trừ n lượt của kỳ start nếu còn đủ. Gọi khi đã khóa dòng user nên tạo dòng usage không bị trùng. */
    boolean take(long userId, Course c, LocalDate start, int n) {
        if (usage.findUsed(userId, c.getId(), start).isEmpty()) {
            usage.save(new AiPeriodUsage(userId, c.getId(), start));
        }
        return usage.take(userId, c.getId(), start, n, c.getAiPeriodQuota()) == 1;
    }

    /** Trả về đúng kỳ đã trừ; kỳ đã qua vẫn ghi giảm used của kỳ cũ. */
    void give(long userId, long courseId, LocalDate start, int n) {
        usage.give(userId, courseId, start, n);
    }
}
