package com.pps.smartenglish.admin.service;

import com.pps.smartenglish.admin.config.ReportProperties;
import com.pps.smartenglish.admin.dto.AuditLogView;
import com.pps.smartenglish.shared.audit.AuditLog;
import com.pps.smartenglish.shared.audit.AuditLogRepository;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.web.CursorPage;
import com.pps.smartenglish.shared.web.Cursors;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tra cứu nhật ký thao tác (FR-ADM-05): lọc theo người, đối tượng, ngày (theo múi giờ báo cáo). */
@Service
public class AuditLogService {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;

    private final AuditLogRepository logs;
    private final ReportProperties props;

    AuditLogService(AuditLogRepository logs, ReportProperties props) {
        this.logs = logs;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public CursorPage<AuditLogView> search(Long actorId, String targetType, String targetId, LocalDate from, LocalDate to,
                                           String limit, String cursor) {
        int size = Cursors.size(limit, DEFAULT_LIMIT, MAX_LIMIT);
        var zone = props.zone();
        LocalDate fromDay = from == null ? LocalDate.of(2000, 1, 1) : from;
        LocalDate toDay = to == null ? LocalDate.now(zone).plusDays(1) : to.plusDays(1);
        if (!fromDay.isBefore(toDay)) {
            throw ApiException.validation("to", "Ngày kết thúc phải sau hoặc bằng ngày bắt đầu");
        }
        Instant start = fromDay.atStartOfDay(zone).toInstant();
        Instant end = toDay.atStartOfDay(zone).toInstant();
        List<AuditLog> rows = logs.search(Cursors.decode(cursor), actorId, blankToNull(targetType), blankToNull(targetId),
                start, end, PageRequest.of(0, size + 1));
        boolean more = rows.size() > size;
        List<AuditLog> page = more ? rows.subList(0, size) : rows;
        var data = page.stream().map(AuditLogService::view).toList();
        return new CursorPage<>(data, more ? Cursors.encode(page.get(size - 1).getId()) : null);
    }

    private static AuditLogView view(AuditLog a) {
        return new AuditLogView(a.getId(), a.getActorId(), a.getAction(), a.getTargetType(), a.getTargetId(),
                a.getBeforeValue(), a.getAfterValue(), a.getReason(), a.getCreatedAt());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
