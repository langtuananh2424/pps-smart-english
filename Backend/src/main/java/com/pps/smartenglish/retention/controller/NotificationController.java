package com.pps.smartenglish.retention.controller;

import com.pps.smartenglish.retention.dto.NotificationView;
import com.pps.smartenglish.retention.service.NotificationService;
import com.pps.smartenglish.shared.security.CurrentUser;
import com.pps.smartenglish.shared.web.CursorPage;
import com.pps.smartenglish.shared.web.PathId;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/notifications")
class NotificationController {

    private final NotificationService notifications;

    NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    CursorPage<NotificationView> list(Authentication auth, @RequestParam(required = false) String limit,
                                      @RequestParam(required = false) String cursor) {
        return notifications.list(CurrentUser.id(auth), limit, cursor);
    }

    @PostMapping("/{id}/read")
    NotificationView read(Authentication auth, @PathVariable String id) {
        return notifications.markRead(CurrentUser.id(auth),
                PathId.parse(id, "notification_not_found", "Không tìm thấy thông báo"));
    }
}
