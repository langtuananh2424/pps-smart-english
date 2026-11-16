package com.pps.smartenglish.ai.service;

import com.pps.smartenglish.ai.domain.AiJob;
import com.pps.smartenglish.ai.domain.AiQuotaLedgerEntry;
import com.pps.smartenglish.ai.domain.AiQuotaLedgerEntry.Reason;
import com.pps.smartenglish.ai.domain.AiQuotaLedgerEntry.Source;
import com.pps.smartenglish.ai.domain.AiTask;
import com.pps.smartenglish.ai.domain.UserAiQuota;
import com.pps.smartenglish.ai.repository.AiQuotaLedgerRepository;
import com.pps.smartenglish.ai.repository.UserAiQuotaRepository;
import com.pps.smartenglish.catalog.domain.Course;
import com.pps.smartenglish.catalog.repository.CourseRepository;
import com.pps.smartenglish.commerce.repository.EnrollmentRepository;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.event.ActivationLinkRedeemed;
import com.pps.smartenglish.shared.event.OrderPaid;
import com.pps.smartenglish.shared.event.UserCreated;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lượt AI (FR-AI-04, D-47): trừ lượt định kỳ của khóa trước, thiếu thì lấy từ pool mua thêm; tác vụ ngoài khóa còn hạn
 * (học thử) chỉ trừ pool. Nguyên tử: khóa dòng user rồi UPDATE có điều kiện, thiếu thì không trừ gì.
 */
@Service
public class AiQuotaService {

    /** Nguồn đã trừ cho một tác vụ; courseId/periodStart null khi chỉ trừ pool. */
    public record Charge(Long courseId, LocalDate periodStart, int periodic, int pool) {}

    public record CourseQuota(long courseId, String courseTitle, String period, int quota, int used, int remaining, Instant resetsAt) {}

    public record Overview(int pool, List<CourseQuota> courses) {}

    private final UserAiQuotaRepository quotas;
    private final AiQuotaLedgerRepository ledger;
    private final AddonCatalog addons;
    private final EnrollmentRepository enrollments;
    private final CourseRepository courses;
    private final PeriodicQuota periodic;
    private final AiTaskWeights weights;
    private final Clock clock;

    AiQuotaService(UserAiQuotaRepository quotas, AiQuotaLedgerRepository ledger, AddonCatalog addons,
                   EnrollmentRepository enrollments, CourseRepository courses, PeriodicQuota periodic, AiTaskWeights weights, Clock clock) {
        this.quotas = quotas;
        this.ledger = ledger;
        this.addons = addons;
        this.enrollments = enrollments;
        this.courses = courses;
        this.periodic = periodic;
        this.weights = weights;
        this.clock = clock;
    }

    /** Pool mua thêm. */
    @Transactional(readOnly = true)
    public int pool(long userId) {
        return quotas.findBalance(userId).orElse(0);
    }

    /** Tổng lượt dùng được cho tác vụ trong khóa này: định kỳ còn lại + pool. */
    @Transactional(readOnly = true)
    public int available(long userId, Long courseId) {
        return activeCourse(userId, courseId).map(c -> periodic.remaining(userId, c)).orElse(0) + pool(userId);
    }

    /** Chặn trước khi mở phiên (kể cả bước không trừ lượt): phải còn đủ lượt cho trọng số tác vụ (Q-15). */
    @Transactional(readOnly = true)
    public void requireAvailable(long userId, Long courseId, AiTask task) {
        if (available(userId, courseId) < weights.of(task)) {
            throw exhausted();
        }
    }

    /**
     * Trừ lượt cho job rồi ghi sổ cái GRADING; empty khi không đủ (không trừ gì, nơi gọi tự quyết). Gọi trong transaction tạo job.
     * Khóa dòng user để hai request đồng thời không cùng đọc "còn đủ" rồi cùng trừ; UPDATE có điều kiện là lớp chặn thứ hai.
     */
    @Transactional
    public Optional<Charge> charge(long userId, long jobId, Long courseId, AiTask task) {
        int weight = weights.of(task);
        enrollments.lockUser(userId);
        Optional<Course> course = activeCourse(userId, courseId);
        int fromPeriodic = course.map(c -> Math.min(periodic.remaining(userId, c), weight)).orElse(0);
        int fromPool = weight - fromPeriodic;
        if (fromPool > pool(userId)) {
            return Optional.empty();
        }
        LocalDate start = course.map(periodic::currentStart).orElse(null);
        if (fromPeriodic > 0) {
            if (!periodic.take(userId, course.get(), start, fromPeriodic)) {
                throw new IllegalStateException("Lượt định kỳ đổi giữa chừng dù đã khóa user");
            }
            ledger.save(new AiQuotaLedgerEntry(userId, -fromPeriodic, Reason.GRADING, jobId, Source.PERIODIC, courseId));
        }
        if (fromPool > 0) {
            if (quotas.debit(userId, fromPool) == 0) {
                throw new IllegalStateException("Pool đổi giữa chừng dù đã khóa user");
            }
            ledger.save(new AiQuotaLedgerEntry(userId, -fromPool, Reason.GRADING, jobId, Source.POOL, courseId));
        }
        return Optional.of(new Charge(course.map(Course::getId).orElse(null), start, fromPeriodic, fromPool));
    }

    /**
     * Hoàn lượt của job lỗi hẳn về đúng nguồn đã trừ. Nơi gọi chỉ gọi khi vừa chuyển job sang FAILED có điều kiện nên không hoàn hai lần.
     * Job tạo trước V28 (đã trừ 1 từ số dư, hai cột nguồn đều 0) hoàn 1 lượt vào pool.
     */
    @Transactional
    public void refund(AiJob job) {
        int fromPeriodic = job.getChargedPeriodic();
        int fromPool = job.getChargedPool();
        if (fromPeriodic + fromPool == 0) {
            fromPool = 1;
        }
        long userId = job.getUserId();
        if (fromPeriodic > 0) {
            periodic.give(userId, job.getCourseId(), job.getPeriodStart(), fromPeriodic);
            ledger.save(new AiQuotaLedgerEntry(userId, fromPeriodic, Reason.REFUND, job.getId(), Source.PERIODIC, job.getCourseId()));
        }
        if (fromPool > 0) {
            credit(userId, fromPool, Reason.REFUND, job.getId(), job.getCourseId());
        }
    }

    /** /api/me/quota: pool và lượt định kỳ của từng khóa còn hạn. */
    @Transactional(readOnly = true)
    public Overview overview(long userId) {
        var rows = courses.findActiveCourses(userId, Instant.now(clock)).stream()
                .map(c -> new CourseQuota(c.getId(), c.getTitle(), c.getAiPeriod(), c.getAiPeriodQuota(), periodic.used(userId, c),
                        periodic.remaining(userId, c), periodic.resetsAt(c)))
                .toList();
        return new Overview(pool(userId), rows);
    }

    /** 402 kèm các gói AI_ADDON mở được (system-design 6.1). */
    public ApiException exhausted() {
        return new ApiException(HttpStatus.PAYMENT_REQUIRED, "quota_exhausted", "Bạn đã hết lượt chấm AI",
                Map.of("products", addons.addons()), null);
    }

    /** Tài khoản mới có 0 lượt trong pool; lượt định kỳ có khi mua khóa. Chạy trong transaction tạo tài khoản. */
    @EventListener
    public void onUserCreated(UserCreated e) {
        quotas.save(new UserAiQuota(e.userId(), 0));
    }

    /** Chạy trong transaction kích hoạt đơn: cộng pool (chỉ gói AI_ADDON, xem EntitlementGranter) và ghi sổ cái cùng lúc, hoặc cùng không. */
    @EventListener
    public void onOrderPaid(OrderPaid e) {
        credit(e.userId(), e.aiQuota(), Reason.PURCHASE, e.orderId(), null);
    }

    /** Chạy trong transaction dùng link kích hoạt; ref là id dòng activation_redemptions. */
    @EventListener
    public void onActivationLinkRedeemed(ActivationLinkRedeemed e) {
        credit(e.userId(), e.aiQuota(), Reason.ACTIVATION_LINK, e.redemptionId(), null);
    }

    private void credit(long userId, int amount, Reason reason, long refId, Long courseId) {
        if (amount <= 0) {
            return;
        }
        if (quotas.addBalance(userId, amount) == 0) {
            quotas.save(new UserAiQuota(userId, amount));
        }
        ledger.save(new AiQuotaLedgerEntry(userId, amount, reason, refId, Source.POOL, courseId));
    }

    private Optional<Course> activeCourse(long userId, Long courseId) {
        return courseId == null ? Optional.empty() : courses.findActive(userId, courseId, Instant.now(clock));
    }
}
