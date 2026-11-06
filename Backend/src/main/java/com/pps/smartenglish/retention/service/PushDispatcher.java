package com.pps.smartenglish.retention.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.retention.config.RetentionProperties;
import com.pps.smartenglish.retention.domain.PushSubscription;
import com.pps.smartenglish.retention.push.PushSender;
import com.pps.smartenglish.retention.repository.NotificationRepository;
import com.pps.smartenglish.retention.repository.PushSubscriptionRepository;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Gửi push tới mọi subscription của một người trên executor có giới hạn (bulkhead), không giữ transaction nào.
 * 404/410 thì xóa subscription; sent_at ghi khi có ít nhất một lần SENT.
 * shortcut: hàng chờ 1000 rồi chạy trên luồng gọi (CallerRuns) thay vì hàng đợi bền; push mất khi tắt máy giữa chừng, chấp nhận
 * được vì thông báo trong ứng dụng vẫn còn.
 */
@Service
public class PushDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PushDispatcher.class);

    private final PushSubscriptionRepository subscriptions;
    private final NotificationRepository notifications;
    private final PushSender sender;
    private final Clock clock;
    private final ThreadPoolExecutor executor;

    PushDispatcher(PushSubscriptionRepository subscriptions, NotificationRepository notifications, PushSender sender, Clock clock,
                   RetentionProperties props) {
        this.subscriptions = subscriptions;
        this.notifications = notifications;
        this.sender = sender;
        this.clock = clock;
        int n = props.push().concurrency();
        this.executor = new ThreadPoolExecutor(n, n, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1000),
                Thread.ofPlatform().name("push-", 0).daemon().factory(), new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /** payload là {title, body, url}. Future hoàn tất khi mọi lần gửi xong (production không cần chờ). */
    public CompletableFuture<Void> dispatch(long userId, long notificationId, JsonNode payload, Duration ttl) {
        List<PushSubscription> targets = subscriptions.findByUserIdOrderByIdAsc(userId);
        if (targets.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        String body = payload.toString();
        var sends = targets.stream()
                .map(s -> CompletableFuture.supplyAsync(() -> sendOne(s, body, ttl), executor))
                .toList();
        return CompletableFuture.allOf(sends.toArray(CompletableFuture[]::new)).thenRun(() -> {
            if (sends.stream().anyMatch(f -> f.join() == PushSender.Result.SENT)) {
                notifications.markSent(notificationId, Instant.now(clock));
            }
        });
    }

    private PushSender.Result sendOne(PushSubscription s, String body, Duration ttl) {
        try {
            PushSender.Result r = sender.send(s, body, ttl);
            if (r == PushSender.Result.GONE) {
                subscriptions.deleteBySubscriptionId(s.getId());
            }
            return r;
        } catch (RuntimeException e) {
            log.warn("Gửi push lỗi: {}", e.getClass().getSimpleName());
            return PushSender.Result.FAILED;
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
