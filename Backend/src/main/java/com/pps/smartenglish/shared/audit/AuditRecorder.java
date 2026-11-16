package com.pps.smartenglish.shared.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.security.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi nhật ký thao tác của nhân sự (FR-ADM-05). Người thao tác lấy từ ngữ cảnh bảo mật; gọi từ luồng không có người
 * dùng thì lỗi, vì nhật ký không có actor thì vô nghĩa. Gọi trong transaction của thay đổi thì ghi cùng lúc: không có thay đổi nào thiếu dấu vết.
 */
@Component
public class AuditRecorder {

    /** Lý do thao tác do client gửi kèm (FR-ADM-05); tùy chọn. */
    public static final String REASON_HEADER = "X-Audit-Reason";
    static final int REASON_MAX = 500;

    private final AuditLogRepository logs;
    private final ObjectMapper mapper;
    private final Clock clock;

    AuditRecorder(AuditLogRepository logs, ObjectMapper mapper, Clock clock) {
        this.logs = logs;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** before null khi tạo mới; after null khi xóa. */
    @Transactional
    public void record(String action, String targetType, Object targetId, Object before, Object after) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new IllegalStateException("Thiếu người thao tác cho nhật ký: " + action);
        }
        logs.save(new AuditLog(CurrentUser.id(auth), action, targetType, String.valueOf(targetId), toJson(before),
                toJson(after), currentReason(), Instant.now(clock)));
    }

    /** Lý do của request hiện tại; ngoài HTTP request (job) hoặc không có header thì null. Quá 500 ký tự thì 400. */
    private static String currentReason() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        String reason = attrs.getRequest().getHeader(REASON_HEADER);
        if (reason == null || reason.isBlank()) {
            return null;
        }
        reason = reason.strip();
        if (reason.length() > REASON_MAX) {
            throw ApiException.validation(REASON_HEADER, "Lý do tối đa " + REASON_MAX + " ký tự");
        }
        return reason;
    }

    private JsonNode toJson(Object value) {
        return value == null ? null : mapper.valueToTree(value);
    }
}
