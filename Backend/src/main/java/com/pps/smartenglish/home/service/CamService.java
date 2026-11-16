package com.pps.smartenglish.home.service;

import com.pps.smartenglish.exercise.repository.QuestionBankRepository;
import com.pps.smartenglish.exercise.repository.StudentAttemptRepository;
import com.pps.smartenglish.home.config.CamProperties;
import com.pps.smartenglish.home.dto.CourseRow;
import com.pps.smartenglish.home.dto.HomeDtos.CamMessage;
import com.pps.smartenglish.home.dto.HomeDtos.CamMessages;
import com.pps.smartenglish.home.repository.HomeReadRepository;
import com.pps.smartenglish.home.service.cam.CamContext;
import com.pps.smartenglish.home.service.cam.CamContext.ExpiringCourse;
import com.pps.smartenglish.home.service.cam.CamContext.OpenAttempt;
import com.pps.smartenglish.home.service.cam.CamRule;
import com.pps.smartenglish.profile.repository.UserProfileRepository;
import com.pps.smartenglish.retention.service.NotificationService;
import com.pps.smartenglish.retention.service.StatsService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GET /api/me/cam-messages (D-53): lời nhắn theo quy tắc, không gọi AI, không trừ lượt. Lời chào luôn đứng đầu;
 * phần còn lại theo thứ tự ưu tiên của các CamRule. Tối đa MAX tin gồm cả lời chào.
 */
@Service
public class CamService {

    static final int MAX = 3;

    private final List<CamRule> rules;
    private final UserProfileRepository profiles;
    private final StatsService stats;
    private final NotificationService notifications;
    private final HomeReadRepository reads;
    private final StudentAttemptRepository attempts;
    private final QuestionBankRepository banks;
    private final CamProperties props;
    private final VnDay day;

    CamService(List<CamRule> rules, UserProfileRepository profiles, StatsService stats, NotificationService notifications,
               HomeReadRepository reads, StudentAttemptRepository attempts, QuestionBankRepository banks, CamProperties props,
               VnDay day) {
        this.rules = rules;
        this.profiles = profiles;
        this.stats = stats;
        this.notifications = notifications;
        this.reads = reads;
        this.attempts = attempts;
        this.banks = banks;
        this.props = props;
        this.day = day;
    }

    @Transactional(readOnly = true)
    public CamMessages messages(long userId) {
        var now = day.now();
        var ctx = new CamContext(userId,
                profiles.findById(userId).map(p -> p.getDisplayName()).orElse("bạn"), now, stats.stats(userId),
                notifications.unreadCount(userId), expiring(userId, now.toInstant()), openAttempt(userId, now.toInstant()));
        List<CamMessage> out = rules.stream().map(r -> r.evaluate(ctx)).flatMap(Optional::stream).limit(MAX).toList();
        return new CamMessages(out);
    }

    private Optional<ExpiringCourse> expiring(long userId, Instant now) {
        List<CourseRow> rows = reads.expiring(userId, now, now.plus(Duration.ofDays(props.expiringDays())));
        return rows.stream().findFirst().map(c -> {
            long days = Math.max(1, (Duration.between(now, c.expiresAt()).toSeconds() + 86_399) / 86_400); // làm tròn lên
            return new ExpiringCourse(c.slug(), c.title(), days);
        });
    }

    /** Lượt dở mới nhất còn hạn; lượt đã hết giờ chờ job tự nộp nên không nhắc. */
    private Optional<OpenAttempt> openAttempt(long userId, Instant now) {
        return attempts.findFirstByUserIdAndSubmittedAtIsNullOrderByIdDesc(userId)
                .filter(a -> !a.expired(now))
                .flatMap(a -> banks.findById(a.getBankId())
                        .map(b -> new OpenAttempt(b.getId(), b.getTitle(), a.remainingSec(now))));
    }
}
