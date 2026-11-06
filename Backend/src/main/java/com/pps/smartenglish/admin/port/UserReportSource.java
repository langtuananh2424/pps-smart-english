package com.pps.smartenglish.admin.port;

import com.pps.smartenglish.admin.dto.DashboardResponse.UsersSection;

/**
 * admin cần số liệu của auth nhưng không gọi repository của module khác (system-design 3): auth cài đặt port này.
 */
public interface UserReportSource {

    /** Học viên (role STUDENT, mọi status) đăng ký trong kỳ, kèm số đã xác thực email, theo ngày đăng ký. */
    UsersSection users(ReportRange range);
}
