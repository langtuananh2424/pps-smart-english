package com.pps.smartenglish.commerce.service;

import com.pps.smartenglish.catalog.domain.CourseLevel;
import com.pps.smartenglish.catalog.repository.CourseLevelRepository;
import com.pps.smartenglish.commerce.domain.Enrollment;
import com.pps.smartenglish.commerce.domain.Product;
import com.pps.smartenglish.commerce.repository.EnrollmentRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Cấp quyền theo sản phẩm, dùng chung cho mua hàng (FR-COM-03) và link kích hoạt (FR-COM-04): tạo hoặc gia hạn
 * enrollment theo khóa (D-25) rồi báo lại tổng lượt AI để nguồn gọi phát event với đúng lý do. Phải gọi trong transaction của nguồn gọi.
 */
@Component
class EntitlementGranter {

    /** aiQuota: tổng lượt AI của các gói AI_ADDON (sản phẩm khóa không cộng pool, lượt của khóa theo cấu hình khóa); accessUntil: hạn muộn nhất của các khóa vừa cấp, null nếu không có khóa hoặc không giới hạn. */
    record Granted(int aiQuota, Instant accessUntil) {}

    private final EnrollmentRepository enrollments;
    private final CourseLevelRepository courseLevels;

    EntitlementGranter(EnrollmentRepository enrollments, CourseLevelRepository courseLevels) {
        this.enrollments = enrollments;
        this.courseLevels = courseLevels;
    }

    /** orderId null khi cấp bằng link kích hoạt (không có đơn). */
    Granted grant(long userId, Long orderId, Collection<Product> products, Instant now) {
        enrollments.lockUser(userId);
        int aiQuota = 0;
        Instant latest = null;
        boolean unlimited = false;
        for (Product p : products) {
            if ("AI_ADDON".equals(p.getType())) {
                aiQuota += p.getAiQuota() == null ? 0 : p.getAiQuota(); // chỉ gói nạp lượt cộng pool (D-22, D-47)
                continue; // gói nạp lượt không mở nội dung
            }
            for (Long courseId : courseIdsOf(p.getLevelIds())) {
                Instant expiresAt = grantCourse(userId, courseId, orderId, p.getAccessDays(), now);
                if (expiresAt == null) {
                    unlimited = true;
                } else if (latest == null || expiresAt.isAfter(latest)) {
                    latest = expiresAt;
                }
            }
        }
        return new Granted(aiQuota, unlimited ? null : latest);
    }

    /** Nhiều level cùng khóa chỉ cấp một lần, vì một (user, khóa) chỉ có một dòng enrollment. */
    private Set<Long> courseIdsOf(Collection<Long> levelIds) {
        return courseLevels.findAllById(levelIds).stream().map(CourseLevel::getCourseId)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /** expires_at = max(now, hạn cũ) + access_days; access_days null thì không giới hạn. Trả hạn mới. */
    private Instant grantCourse(long userId, long courseId, Long orderId, Integer accessDays, Instant now) {
        var existing = enrollments.findByUserIdAndCourseId(userId, courseId);
        if (existing.isEmpty()) {
            Instant expiresAt = accessDays == null ? null : now.plus(Duration.ofDays(accessDays));
            enrollments.save(new Enrollment(userId, courseId, orderId, now, expiresAt));
            return expiresAt;
        }
        Instant old = existing.get().getExpiresAt();
        Instant expiresAt;
        if (accessDays == null || old == null) {
            expiresAt = null; // gói vô thời hạn, hoặc đã vô thời hạn từ trước: không bao giờ bị thu hẹp
        } else {
            expiresAt = (old.isAfter(now) ? old : now).plus(Duration.ofDays(accessDays));
        }
        existing.get().renew(orderId, expiresAt);
        return expiresAt;
    }
}
