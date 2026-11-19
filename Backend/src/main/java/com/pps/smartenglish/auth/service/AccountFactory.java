package com.pps.smartenglish.auth.service;

import com.pps.smartenglish.auth.domain.User;
import com.pps.smartenglish.auth.repository.AccessRepository;
import com.pps.smartenglish.auth.repository.UserRepository;
import com.pps.smartenglish.shared.event.UserCreated;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Một chỗ duy nhất tạo tài khoản học viên mới, dùng chung cho đăng ký email và Google. Gọi trong transaction. */
@Component
class AccountFactory {

    private final UserRepository users;
    private final AccessRepository access;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    AccountFactory(UserRepository users, AccessRepository access, ApplicationEventPublisher events, Clock clock) {
        this.users = users;
        this.access = access;
        this.events = events;
        this.clock = clock;
    }

    /** passwordHash null cho tài khoản chỉ đăng nhập bằng nhà cung cấp ngoài; verifiedAt null nếu chưa xác thực. */
    User createStudent(String email, String passwordHash, Instant verifiedAt, String displayName, ConsentPolicy.Consent consent) {
        User user = new User(email, passwordHash, verifiedAt);
        user.recordConsent(consent.dateOfBirth(), consent.termsVersion(), clock.instant());
        user = users.saveAndFlush(user);
        access.grantRole(user.getId(), "STUDENT", null);
        events.publishEvent(new UserCreated(user.getId(), displayName));
        return user;
    }
}
