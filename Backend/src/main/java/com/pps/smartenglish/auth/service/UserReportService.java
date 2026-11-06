package com.pps.smartenglish.auth.service;

import com.pps.smartenglish.admin.dto.DashboardResponse.UserDay;
import com.pps.smartenglish.admin.dto.DashboardResponse.UsersSection;
import com.pps.smartenglish.admin.port.ReportRange;
import com.pps.smartenglish.admin.port.ReportRows;
import com.pps.smartenglish.admin.port.UserReportSource;
import com.pps.smartenglish.auth.repository.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cài đặt port báo cáo người dùng của admin (FR-ADM-03). */
@Service
class UserReportService implements UserReportSource {

    private final UserRepository users;

    UserReportService(UserRepository users) {
        this.users = users;
    }

    /** Một truy vấn theo ngày, tổng cộng lại trong Java: không có truy vấn lặp theo từng ngày. */
    @Override
    @Transactional(readOnly = true)
    public UsersSection users(ReportRange range) {
        List<UserDay> daily = users.registrationsByDay(range.zone().getId(), range.start(), range.end()).stream()
                .map(r -> new UserDay(ReportRows.date(r[0]), ReportRows.number(r[1]), ReportRows.number(r[2])))
                .toList();
        return new UsersSection(daily.stream().mapToLong(UserDay::registered).sum(),
                daily.stream().mapToLong(UserDay::verified).sum(), daily);
    }
}
