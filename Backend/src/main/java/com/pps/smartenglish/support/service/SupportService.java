package com.pps.smartenglish.support.service;

import com.pps.smartenglish.ai.service.AiQuotaService;
import com.pps.smartenglish.auth.domain.User;
import com.pps.smartenglish.auth.repository.UserRepository;
import com.pps.smartenglish.auth.service.AccountRecoveryService;
import com.pps.smartenglish.commerce.service.ManualActivationService;
import com.pps.smartenglish.shared.audit.AuditRecorder;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.support.dto.SupportDtos.ActivateResult;
import com.pps.smartenglish.support.dto.SupportDtos.BonusResult;
import com.pps.smartenglish.support.dto.SupportDtos.EnrollmentRow;
import com.pps.smartenglish.support.dto.SupportDtos.OrderRow;
import com.pps.smartenglish.support.dto.SupportDtos.StudentDetail;
import com.pps.smartenglish.support.dto.SupportDtos.StudentRow;
import com.pps.smartenglish.support.repository.SupportStudentRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-SUP-03: tra cứu học viên và thao tác hỗ trợ, mỗi thao tác ghi nhật ký (FR-ADM-05) kèm lý do. */
@Service
public class SupportService {

    public static final int MAX_RESULTS = 20;
    private static final int RECENT_ORDERS = 10;

    private final SupportStudentRepository students;
    private final UserRepository users;
    private final AiQuotaService quota;
    private final ManualActivationService activation;
    private final AccountRecoveryService recovery;
    private final AuditRecorder audit;
    private final Clock clock;

    SupportService(SupportStudentRepository students, UserRepository users, AiQuotaService quota,
                   ManualActivationService activation, AccountRecoveryService recovery, AuditRecorder audit, Clock clock) {
        this.students = students;
        this.users = users;
        this.quota = quota;
        this.activation = activation;
        this.recovery = recovery;
        this.audit = audit;
        this.clock = clock;
    }

    /** q khớp email hoặc tên (chứa), hoặc mã đơn (đúng). Tối thiểu 3 ký tự để không kéo cả danh sách. */
    @Transactional(readOnly = true)
    public List<StudentRow> search(String q) {
        String term = q == null ? "" : q.strip();
        if (term.length() < 3) {
            throw ApiException.validation("q", "Nhập ít nhất 3 ký tự");
        }
        String like = "%" + term.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        var found = students.search(like, term, MAX_RESULTS);
        if (found.isEmpty()) {
            return List.of();
        }
        Map<Long, List<String>> courses = new java.util.HashMap<>();
        for (Object[] c : students.courseTitles(found.stream().map(r -> ((Number) r[0]).longValue()).toList())) {
            courses.computeIfAbsent(((Number) c[0]).longValue(), k -> new java.util.ArrayList<>()).add((String) c[1]);
        }
        return found.stream().map(r -> row(r, courses.getOrDefault(((Number) r[0]).longValue(), List.of()))).toList();
    }

    @Transactional(readOnly = true)
    public StudentDetail detail(long id) {
        Object[] r = students.findStudent(id).stream().findFirst().orElseThrow(SupportService::notFound);
        var enrollments = students.enrollments(id).stream()
                .map(e -> new EnrollmentRow(((Number) e[0]).longValue(), (String) e[1], (String) e[2], instant(e[3]), instant(e[4]))).toList();
        var orders = students.recentOrders(id, RECENT_ORDERS, clock.instant()).stream()
                .map(o -> new OrderRow((String) o[0], (String) o[1], ((Number) o[2]).longValue(), instant(o[3]), instant(o[4]))).toList();
        return new StudentDetail(row(r, enrollments.stream().filter(e -> "ACTIVE".equals(e.status())).map(EnrollmentRow::courseTitle).toList()), instant(r[5]), enrollments, orders, quota.overview(id));
    }

    @Transactional
    public void unlock(long id, String reason) {
        User user = requireStudent(id);
        if (!user.unlock()) {
            throw new ApiException(HttpStatus.CONFLICT, "not_locked", "Tài khoản không bị khóa");
        }
        audit.record("UNLOCK_ACCOUNT", "USER", id, Map.of("status", "LOCKED"), Map.of("status", "ACTIVE", "reason", reason));
    }

    @Transactional
    public BonusResult bonus(long id, int amount, String reason) {
        requireStudent(id);
        int pool = quota.grantBonus(id, amount);
        audit.record("BONUS_QUOTA", "USER", id, null, Map.of("amount", amount, "pool", pool, "reason", reason));
        return new BonusResult(amount, pool);
    }

    /** Gửi lại thư xác thực: không đổi dữ liệu nên không cần lý do; giới hạn 3 lần mỗi giờ theo người dùng. */
    public void resendVerification(long id) {
        User user = requireStudent(id);
        if (user.getEmailVerifiedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "already_verified", "Email đã được xác thực");
        }
        recovery.resendVerification(id);
        audit.record("RESEND_VERIFICATION", "USER", id, null, null);
    }

    @Transactional
    public ActivateResult activateOrder(String orderCode, String reason) {
        return new ActivateResult(orderCode, activation.activate(orderCode, reason));
    }

    private User requireStudent(long id) {
        if (students.findStudent(id).isEmpty()) {
            throw notFound();
        }
        return users.findById(id).orElseThrow(SupportService::notFound);
    }

    private static StudentRow row(Object[] r, List<String> courses) {
        return new StudentRow(((Number) r[0]).longValue(), (String) r[1], (String) r[2], String.valueOf(r[3]), r[4] != null, courses);
    }

    private static Instant instant(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Instant i) {
            return i;
        }
        if (o instanceof OffsetDateTime odt) {
            return odt.toInstant();
        }
        return ((Timestamp) o).toInstant();
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "student_not_found", "Không tìm thấy học viên");
    }
}
