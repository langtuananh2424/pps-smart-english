package com.pps.smartenglish.auth.service;

import com.pps.smartenglish.auth.domain.User;
import com.pps.smartenglish.auth.repository.UserRepository;
import com.pps.smartenglish.shared.event.UserCreated;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Một chỗ duy nhất tạo tài khoản học viên mới, dùng chung cho đăng ký email và Google. Gọi trong transaction. */
@Component
class AccountFactory {

    private final UserRepository users;
    private final ApplicationEventPublisher events;

    AccountFactory(UserRepository users, ApplicationEventPublisher events) {
        this.users = users;
        this.events = events;
    }

    /** passwordHash null cho tài khoản chỉ đăng nhập bằng nhà cung cấp ngoài; verifiedAt null nếu chưa xác thực. */
    User createStudent(String email, String passwordHash, Instant verifiedAt, String displayName) {
        User user = users.saveAndFlush(new User(email, passwordHash, verifiedAt));
        events.publishEvent(new UserCreated(user.getId(), displayName));
        return user;
    }
}
