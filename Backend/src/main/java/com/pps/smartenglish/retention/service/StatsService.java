package com.pps.smartenglish.retention.service;

import com.pps.smartenglish.profile.domain.UserProfile;
import com.pps.smartenglish.profile.dto.StatsResponse;
import com.pps.smartenglish.profile.repository.UserProfileRepository;
import com.pps.smartenglish.retention.config.RetentionProperties;
import com.pps.smartenglish.retention.config.RetentionProperties.Title;
import com.pps.smartenglish.retention.repository.ActivityDayRepository;
import com.pps.smartenglish.shared.error.ApiException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** GET /api/me/stats: XP, danh hiệu, streak. Streak hiển thị 0 khi đã đứt, không cần job đặt lại. */
@Service
public class StatsService {

    private final UserProfileRepository profiles;
    private final ActivityDayRepository activity;
    private final Today today;
    private final List<Title> titles;

    StatsService(UserProfileRepository profiles, ActivityDayRepository activity, Today today, RetentionProperties props) {
        this.profiles = profiles;
        this.activity = activity;
        this.today = today;
        this.titles = props.titles().stream().sorted(Comparator.comparingLong(Title::minXp)).toList();
    }

    @Transactional(readOnly = true)
    public StatsResponse stats(long userId) {
        UserProfile p = profiles.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Không tìm thấy hồ sơ"));
        LocalDate d = today.date();
        LocalDate last = p.getLastActiveDate();
        LocalDate monday = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        boolean activeToday = d.equals(last);
        int streak = activeToday || d.minusDays(1).equals(last) ? p.getStreakCurrent() : 0;

        Title current = titles.get(0);
        Title next = null;
        for (Title t : titles) {
            if (p.getXp() >= t.minXp()) {
                current = t;
            } else {
                next = t;
                break;
            }
        }
        return new StatsResponse(p.getXp(), streak, p.getStreakBest(), last, current.name(),
                next == null ? null : next.name(), next == null ? null : next.minXp(), activeToday,
                activity.findDays(userId, monday, monday.plusDays(6)));
    }
}
