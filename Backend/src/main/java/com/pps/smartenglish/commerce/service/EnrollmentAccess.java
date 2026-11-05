package com.pps.smartenglish.commerce.service;

import com.pps.smartenglish.commerce.repository.EnrollmentRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cổng công khai để lms và exercise hỏi quyền học theo Level mà không biết bảng enrollment. */
@Service
public class EnrollmentAccess {

    private final EnrollmentRepository enrollments;
    private final Clock clock;

    EnrollmentAccess(EnrollmentRepository enrollments, Clock clock) {
        this.enrollments = enrollments;
        this.clock = clock;
    }

    /** Có enrollment ACTIVE còn hạn (expires_at null hoặc sau bây giờ); khách (userId null) thì không. */
    @Transactional(readOnly = true)
    public boolean hasAccess(Long userId, Long levelId) {
        return userId != null && levelId != null && enrollments.existsActive(userId, levelId, Instant.now(clock));
    }
}
