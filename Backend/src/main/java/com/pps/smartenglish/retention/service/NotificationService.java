package com.pps.smartenglish.retention.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pps.smartenglish.retention.domain.Notification;
import com.pps.smartenglish.retention.dto.NotificationView;
import com.pps.smartenglish.retention.repository.NotificationRepository;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.web.CursorPage;
import com.pps.smartenglish.shared.web.Cursors;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-RET-03: thông báo trong ứng dụng (nguồn sự thật) và giao thêm qua push. */
@Service
public class NotificationService {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;
    static final Duration WRITING_TTL = Duration.ofDays(1);

    private final NotificationRepository notifications;
    private final NotificationWriter writer;
    private final PushDispatcher push;
    private final ObjectMapper mapper;
    private final Clock clock;

    NotificationService(NotificationRepository notifications, NotificationWriter writer, PushDispatcher push, ObjectMapper mapper,
                        Clock clock) {
        this.notifications = notifications;
        this.writer = writer;
        this.push = push;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Tạo thông báo đúng một lần theo (user, dedupeKey) rồi push. Chỉ bên chèn được dòng mới gửi push nên nhiều instance
     * hay nhiều vòng job không gửi hai lần. Trả id nếu vừa tạo.
     */
    public Optional<Long> notifyOnce(long userId, Notification.Type type, String title, String body, String url, String dedupeKey,
                                     Duration pushTtl) {
        JsonNode payload = mapper.createObjectNode().put("title", title).put("body", body).put("url", url);
        Optional<Long> id;
        try {
            id = writer.insertIfAbsent(userId, type, payload, dedupeKey);
        } catch (DataIntegrityViolationException e) {
            return Optional.empty(); // request đồng thời đã tạo trước
        }
        id.ifPresent(nid -> push.dispatch(userId, nid, payload, pushTtl));
        return id;
    }

    /** Bài viết chấm xong (dedupe theo job). Nội dung bài viết không bao giờ nằm trong thông báo. */
    public void writingGraded(long userId, long jobId) {
        // đường dẫn web tính một chỗ này; push và danh sách dùng chung payload.url (D-92)
        String url = notifications.findWritingQuestionId(jobId).map(q -> "/viet/" + q).orElse("/bai-tap");
        notifyOnce(userId, Notification.Type.WRITING_GRADED, "Bài viết đã chấm xong", "Mở để xem điểm và nhận xét.",
                url, "WRITING_GRADED:" + jobId, WRITING_TTL);
    }

    /** Nhân viên trả lời ticket (FR-SUP-02), dedupe theo tin nhắn. Nội dung tin nhắn không nằm trong thông báo. */
    public void ticketReply(long userId, long ticketId, long messageId) {
        notifyOnce(userId, Notification.Type.TICKET_REPLY, "Nhân viên đã trả lời", "Mở hỗ trợ để xem tin nhắn.",
                "/ho-tro/" + ticketId, "TICKET_REPLY:" + messageId, WRITING_TTL);
    }

    @Transactional(readOnly = true)
    public CursorPage<NotificationView> list(long userId, String limit, String cursor) {
        int size = Cursors.size(limit, DEFAULT_LIMIT, MAX_LIMIT);
        List<Notification> rows = notifications.findPage(userId, Cursors.decode(cursor), PageRequest.of(0, size + 1));
        boolean more = rows.size() > size;
        List<Notification> page = more ? rows.subList(0, size) : rows;
        return new CursorPage<>(page.stream().map(NotificationService::view).toList(),
                more ? Cursors.encode(page.get(size - 1).getId()) : null);
    }

    @Transactional(readOnly = true)
    public long unreadCount(long userId) {
        return notifications.countByUserIdAndReadAtIsNull(userId);
    }

    /** Chỉ của chính mình, người khác 404; đọc lại giữ thời điểm đọc đầu tiên. */
    @Transactional
    public NotificationView markRead(long userId, long id) {
        notifications.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "notification_not_found", "Không tìm thấy thông báo"));
        notifications.markRead(id, userId, Instant.now(clock));
        return view(notifications.findByIdAndUserId(id, userId).orElseThrow());
    }

    /** Chỉ dòng của chính mình; trả số thông báo vừa đổi. */
    @Transactional
    public int markAllRead(long userId) {
        return notifications.markAllRead(userId, Instant.now(clock));
    }

    private static NotificationView view(Notification n) {
        return new NotificationView(n.getId(), n.getType().name(), n.getPayload(), n.getCreatedAt(), n.getReadAt());
    }
}
