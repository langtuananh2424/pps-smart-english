package com.pps.smartenglish.retention;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.ai.AiTestSupport;
import com.pps.smartenglish.retention.domain.Notification;
import com.pps.smartenglish.retention.push.PushSender;
import com.pps.smartenglish.retention.service.NotificationService;
import com.pps.smartenglish.retention.service.StreakReminderService;
import com.pps.smartenglish.shared.event.AiGradingFinished;
import com.pps.smartenglish.support.FakePushConfig;
import com.pps.smartenglish.support.FakePushConfig.FakePushSender;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** FR-RET-03: thông báo, đăng ký push, nhắc streak, thông báo chấm xong. */
@Import(FakePushConfig.class)
class NotificationPushTest extends AiTestSupport {

    @Autowired FakePushSender push;
    @Autowired NotificationService notifications;
    @Autowired StreakReminderService reminders;
    @Autowired ApplicationEventPublisher publisher;

    @BeforeEach
    void resetPush() {
        push.reset();
    }

    private static String fcm() {
        return "https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID();
    }

    private ResultActions subscribe(Account u, String endpoint) throws Exception {
        return postJson("/api/me/push-subscriptions", u, Map.of("endpoint", endpoint, "keys", Map.of("p256dh", "BKey", "auth", "aSecret")));
    }

    private ResultActions unsubscribe(Account u, String endpoint) throws Exception {
        var req = MockMvcRequestBuilders.delete("/api/me/push-subscriptions").contentType("application/json")
                .content(json.writeValueAsString(Map.of("endpoint", endpoint)));
        return send(req, u.accessToken());
    }

    private int subscriptions(String endpoint) {
        return count("select count(*) from push_subscriptions where endpoint = ?", endpoint);
    }

    private void awaitSent(String endpoint, int times) {
        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> push.sentTo(endpoint).size() == times);
    }

    @Test
    void subscribeOnlyAcceptsHttpsToKnownPushHosts() throws Exception {
        var u = student(0);
        for (String bad : new String[] {"http://fcm.googleapis.com/x", "https://evil.example.com/x", "https://fcm.googleapis.com@evil.com/x",
                "https://push.apple.com.evil.com/x", "https://fcm.googleapis.com:8443/x", "https://push.apple.com/x",
                "https://169.254.169.254/latest/meta-data", "ftp://fcm.googleapis.com/x", "not a url"}) {
            subscribe(u, bad).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("validation_failed"));
        }
        subscribe(u, "https://fcm.googleapis.com/" + "a".repeat(1000)).andExpect(status().isBadRequest()); // dài quá 1000 ký tự
        postJson("/api/me/push-subscriptions", u, Map.of("endpoint", fcm())).andExpect(status().isBadRequest()); // thiếu keys
        postJson("/api/me/push-subscriptions", u, Map.of("endpoint", fcm(), "keys", Map.of("p256dh", "", "auth", "x")))
                .andExpect(status().isBadRequest());
        assertEquals(0, count("select count(*) from push_subscriptions where user_id = ?", u.id()));

        subscribe(u, fcm()).andExpect(status().isCreated());
        subscribe(u, "https://updates.push.services.mozilla.com/wpush/v2/" + UUID.randomUUID()).andExpect(status().isCreated());
        subscribe(u, "https://web.push.apple.com/" + UUID.randomUUID()).andExpect(status().isCreated());
        subscribe(u, "https://wns2-par02p.notify.windows.com/w/?token=" + UUID.randomUUID()).andExpect(status().isCreated());
        postJson("/api/me/push-subscriptions", null, Map.of()).andExpect(status().isUnauthorized());
    }

    @Test
    void subscriptionMovesToTheLatestUserAndOnlyOwnerCanDelete() throws Exception {
        var a = student(0);
        var b = student(0);
        String endpoint = fcm();
        subscribe(a, endpoint).andExpect(status().isCreated());
        unsubscribe(b, endpoint).andExpect(status().isNoContent());
        assertEquals(1, subscriptions(endpoint)); // người khác không xóa được

        subscribe(b, endpoint).andExpect(status().isCreated()); // upsert theo endpoint, chuyển chủ
        assertEquals(1, subscriptions(endpoint));
        assertEquals(b.id(), jdbc.queryForObject("select user_id from push_subscriptions where endpoint = ?", Long.class, endpoint));
        unsubscribe(a, endpoint).andExpect(status().isNoContent());
        assertEquals(1, subscriptions(endpoint));
        unsubscribe(b, endpoint).andExpect(status().isNoContent());
        assertEquals(0, subscriptions(endpoint));
    }

    @Test
    void atMostTenSubscriptionsPerUserOldestDropped() throws Exception {
        var u = student(0);
        String first = fcm();
        String second = fcm();
        subscribe(u, first);
        subscribe(u, second);
        for (int i = 0; i < 10; i++) {
            subscribe(u, fcm()).andExpect(status().isCreated());
        }
        assertEquals(10, count("select count(*) from push_subscriptions where user_id = ?", u.id()));
        assertEquals(0, subscriptions(first));
        assertEquals(0, subscriptions(second));
    }

    @Test
    void vapidKeyIsNullUntilConfiguredAndNeedsLogin() throws Exception {
        var u = student(0);
        getJson("/api/push/vapid-key", u).andExpect(status().isOk()).andExpect(jsonPath("$.publicKey").value(nullValue()));
        mvc.perform(MockMvcRequestBuilders.get("/api/push/vapid-key")).andExpect(status().isUnauthorized());
    }

    @Test
    void finishedWritingGradeCreatesOneNotificationAndPushesWithoutEssayText() throws Exception {
        var u = student(1);
        String endpoint = fcm();
        subscribe(u, endpoint).andExpect(status().isCreated());
        guard.resetRateLimit(u.id());
        long session = idOf(postJson("/api/writing/sessions", u, Map.of("questionId", writingQuestion(bank(true)))), "sessionId");
        String essay = "I strongly agree with online learning because it saves time.";
        long jobId = idOf(postJson("/api/writing/sessions/" + session + "/submit", u, Map.of("finalText", essay),
                UUID.randomUUID().toString()), "jobId");

        runner.runDueNow();

        assertEquals(1, count("select count(*) from notifications where user_id = ? and dedupe_key = ? and type = 'WRITING_GRADED'",
                u.id(), "WRITING_GRADED:" + jobId));
        awaitSent(endpoint, 1);
        var sent = push.sentTo(endpoint).get(0);
        JsonNode payload = json.readTree(sent.payload());
        assertEquals("Bài viết đã chấm xong", payload.get("title").asText());
        assertEquals("Mở để xem điểm và nhận xét.", payload.get("body").asText());
        assertEquals("/ai/jobs/" + jobId, payload.get("url").asText());
        assertFalse(sent.payload().contains("online learning"), sent.payload());
        assertEquals(Duration.ofDays(1), sent.ttl());
        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> count(
                "select count(*) from notifications where user_id = ? and sent_at is not null", u.id()) == 1);

        // cùng job báo lại: không tạo thêm, không push thêm
        publisher.publishEvent(new AiGradingFinished(jobId, u.id(), "WRITING_GRADE"));
        assertEquals(1, count("select count(*) from notifications where user_id = ?", u.id()));
        Awaitility.await().during(Duration.ofMillis(300)).atMost(Duration.ofSeconds(3)).until(() -> push.sentTo(endpoint).size() == 1);
    }

    @Test
    void otherKindsOfFinishedJobsCreateNoNotification() throws Exception {
        var u = student(0);
        for (String kind : new String[] {"REWRITE_GRADE", "SCAFFOLD_FOLLOWUP", "SCAFFOLD_DRAFT"}) {
            publisher.publishEvent(new AiGradingFinished(500, u.id(), kind));
        }
        assertEquals(0, count("select count(*) from notifications where user_id = ?", u.id()));
    }

    @Test
    void goneEndpointIsDeletedAndSentAtNeedsAtLeastOneSuccess() throws Exception {
        var u = student(0);
        String dead = fcm();
        String alive = fcm();
        subscribe(u, dead);
        subscribe(u, alive);
        push.resultFor(dead, PushSender.Result.GONE);

        publisher.publishEvent(new AiGradingFinished(900, u.id(), "WRITING_GRADE"));

        awaitSent(dead, 1);
        awaitSent(alive, 1);
        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> subscriptions(dead) == 0);
        assertEquals(1, subscriptions(alive));
        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> count(
                "select count(*) from notifications where user_id = ? and sent_at is not null", u.id()) == 1);

        // mọi lần gửi đều hỏng thì sent_at vẫn trống
        var w = student(0);
        String failing = fcm();
        subscribe(w, failing);
        push.resultFor(failing, PushSender.Result.FAILED);
        publisher.publishEvent(new AiGradingFinished(901, w.id(), "WRITING_GRADE"));
        awaitSent(failing, 1);
        Awaitility.await().during(Duration.ofMillis(300)).atMost(Duration.ofSeconds(3)).until(() -> count(
                "select count(*) from notifications where user_id = ? and sent_at is null", w.id()) == 1);
        assertEquals(1, subscriptions(failing)); // chỉ 404/410 mới xóa
    }

    @Test
    void streakReminderTargetsOnlyThoseWhoMayLoseTheirStreakAndNeverRepeats() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        var target = student(0);
        var activeToday = student(0);
        var noStreak = student(0);
        var broken = student(0);
        setStreak(target, 3, today.minusDays(1));
        setStreak(activeToday, 3, today);
        setStreak(noStreak, 0, today.minusDays(1));
        setStreak(broken, 2, today.minusDays(3));
        String endpoint = fcm();
        subscribe(target, endpoint);

        reminders.remindAll();

        assertEquals(1, count("select count(*) from notifications where user_id = ? and type = 'STREAK_REMINDER' and dedupe_key = ?",
                target.id(), "STREAK:" + today));
        for (var other : new Account[] {activeToday, noStreak, broken}) {
            assertEquals(0, count("select count(*) from notifications where user_id = ?", other.id()));
        }
        awaitSent(endpoint, 1);
        var sent = push.sentTo(endpoint).get(0);
        JsonNode payload = json.readTree(sent.payload());
        assertEquals("Giữ chuỗi 3 ngày nhé!", payload.get("title").asText());
        assertEquals("Học một bài hôm nay để không mất chuỗi.", payload.get("body").asText());
        assertEquals("/", payload.get("url").asText());
        assertEquals(Duration.ofHours(4), sent.ttl());

        // nhiều vòng trong khung 20:00 hoặc nhiều instance: không tạo thêm, không gửi thêm
        reminders.remindAll();
        reminders.remindAll();
        assertEquals(1, count("select count(*) from notifications where user_id = ?", target.id()));
        Awaitility.await().during(Duration.ofMillis(300)).atMost(Duration.ofSeconds(3)).until(() -> push.sentTo(endpoint).size() == 1);
    }

    @Test
    void notificationsListNewestFirstWithCursorAndMarkRead() throws Exception {
        var u = student(0);
        var other = student(0);
        long[] ids = new long[3];
        for (int i = 0; i < 3; i++) {
            ids[i] = notifications.notifyOnce(u.id(), Notification.Type.WRITING_GRADED, "Tiêu đề " + i, "Nội dung", "/x/" + i,
                    "TEST:" + UUID.randomUUID(), Duration.ofHours(1)).orElseThrow();
        }
        // cùng dedupe key thì không tạo thêm
        assertTrue(notifications.notifyOnce(u.id(), Notification.Type.WRITING_GRADED, "t", "b", "/", "DUP", Duration.ofHours(1)).isPresent());
        assertTrue(notifications.notifyOnce(u.id(), Notification.Type.WRITING_GRADED, "t", "b", "/", "DUP", Duration.ofHours(1)).isEmpty());

        JsonNode page1 = json.readTree(getJson("/api/me/notifications?limit=2", u).andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2))).andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andReturn().getResponse().getContentAsString());
        assertTrue(page1.at("/data/0/id").asLong() > page1.at("/data/1/id").asLong());
        assertEquals("WRITING_GRADED", page1.at("/data/0/type").asText());
        getJson("/api/me/notifications?limit=2&cursor=" + page1.get("nextCursor").asText(), u).andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2))).andExpect(jsonPath("$.nextCursor").value(nullValue()));
        getJson("/api/me/notifications?limit=0", u).andExpect(status().isBadRequest());
        getJson("/api/me/notifications", other).andExpect(jsonPath("$.data", hasSize(0)));

        long target = ids[0];
        String firstRead = json.readTree(postJson("/api/me/notifications/" + target + "/read", u, Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.readAt").isNotEmpty()).andReturn().getResponse().getContentAsString()).get("readAt").asText();
        String again = json.readTree(postJson("/api/me/notifications/" + target + "/read", u, Map.of()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("readAt").asText();
        assertEquals(firstRead, again); // đọc lại giữ thời điểm đầu

        postJson("/api/me/notifications/" + target + "/read", other, Map.of()).andExpect(status().isNotFound());
        postJson("/api/me/notifications/999999999/read", u, Map.of()).andExpect(status().isNotFound());
        postJson("/api/me/notifications/" + target + "/read", null, Map.of()).andExpect(status().isUnauthorized());
        mvc.perform(MockMvcRequestBuilders.get("/api/me/notifications")).andExpect(status().isUnauthorized());
    }

    private void setStreak(Account u, int current, LocalDate last) {
        jdbc.update("update user_profiles set streak_current = ?, streak_best = ?, last_active_date = ? where user_id = ?",
                current, current, last, u.id());
    }
}
