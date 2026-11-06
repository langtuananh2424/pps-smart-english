package com.pps.smartenglish.retention.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.retention.domain.Notification;
import com.pps.smartenglish.retention.repository.NotificationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Chèn-nếu-chưa-có trong transaction riêng nên lỗi trùng của bên thua cuộc không làm hỏng transaction của nơi gọi. */
@Component
public class NotificationWriter {

    private final NotificationRepository notifications;
    private final Clock clock;

    NotificationWriter(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    /** Trả id nếu vừa chèn; empty nếu (user, dedupeKey) đã có. Đồng thời: bên thua ném DataIntegrityViolationException. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Long> insertIfAbsent(long userId, Notification.Type type, JsonNode payload, String dedupeKey) {
        if (notifications.existsByUserIdAndDedupeKey(userId, dedupeKey)) {
            return Optional.empty();
        }
        return Optional.of(notifications.saveAndFlush(new Notification(userId, type, payload, dedupeKey, Instant.now(clock))).getId());
    }
}
