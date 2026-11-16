package com.pps.smartenglish.admin.controller;

import com.pps.smartenglish.admin.dto.AuditLogView;
import com.pps.smartenglish.admin.service.AuditLogService;
import com.pps.smartenglish.shared.web.CursorPage;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Nhật ký thao tác nội bộ (FR-ADM-05). Chỉ đọc. */
@RestController
@RequestMapping("/api/admin/audit-logs")
class AuditLogController {

    private final AuditLogService audit;

    AuditLogController(AuditLogService audit) {
        this.audit = audit;
    }

    @GetMapping
    CursorPage<AuditLogView> search(@RequestParam(required = false) Long actorId,
                                    @RequestParam(required = false) String targetType,
                                    @RequestParam(required = false) String targetId,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                    @RequestParam(required = false) String limit,
                                    @RequestParam(required = false) String cursor) {
        return audit.search(actorId, targetType, targetId, from, to, limit, cursor);
    }
}
