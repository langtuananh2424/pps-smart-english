package com.pps.smartenglish.commerce.service;

import com.pps.smartenglish.commerce.domain.Enrollment;
import com.pps.smartenglish.commerce.domain.Product;
import com.pps.smartenglish.commerce.repository.EnrollmentRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import org.springframework.stereotype.Component;

/**
 * Cấp quyền theo sản phẩm, dùng chung cho mua hàng (FR-COM-03) và link kích hoạt (FR-COM-04): tạo hoặc gia hạn
 * enrollment rồi báo lại tổng lượt AI để nguồn gọi phát event với đúng lý do. Phải gọi trong transaction của nguồn gọi.
 */
@Component
class EntitlementGranter {

    /** aiQuota: tổng lượt AI; accessUntil: hạn muộn nhất của các Level vừa cấp, null nếu không có Level hoặc không giới hạn. */
    record Granted(int aiQuota, Instant accessUntil) {}

    private final EnrollmentRepository enrollments;

    EntitlementGranter(EnrollmentRepository enrollments) {
        this.enrollments = enrollments;
    }

    /** orderId null khi cấp bằng link kích hoạt (không có đơn). */
    Granted grant(long userId, Long orderId, Collection<Product> products, Instant now) {
        enrollments.lockUser(userId);
        int aiQuota = 0;
        Instant latest = null;
        boolean unlimited = false;
        for (Product p : products) {
            aiQuota += p.getAiQuota() == null ? 0 : p.getAiQuota();
            if ("AI_ADDON".equals(p.getType())) {
                continue; // gói nạp lượt không mở nội dung
            }
            for (Long levelId : p.getLevelIds()) {
                Instant expiresAt = grantLevel(userId, levelId, orderId, p.getAccessDays(), now);
                if (expiresAt == null) {
                    unlimited = true;
                } else if (latest == null || expiresAt.isAfter(latest)) {
                    latest = expiresAt;
                }
            }
        }
        return new Granted(aiQuota, unlimited ? null : latest);
    }

    /** expires_at = max(now, hạn cũ) + access_days; access_days null thì không giới hạn. Trả hạn mới. */
    private Instant grantLevel(long userId, long levelId, Long orderId, Integer accessDays, Instant now) {
        var existing = enrollments.findByUserIdAndLevelId(userId, levelId);
        if (existing.isEmpty()) {
            Instant expiresAt = accessDays == null ? null : now.plus(Duration.ofDays(accessDays));
            enrollments.save(new Enrollment(userId, levelId, orderId, now, expiresAt));
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
