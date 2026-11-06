package com.pps.smartenglish.retention.push;

import com.pps.smartenglish.retention.config.RetentionProperties;
import com.pps.smartenglish.retention.domain.PushSubscription;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Web Push chuẩn VAPID. Timeout ngắn để một endpoint chậm không giữ chân luồng gửi. */
class WebPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(WebPushSender.class);
    private static final long TIMEOUT_SECONDS = 10;

    private final PushService service;

    WebPushSender(RetentionProperties.Push push) throws GeneralSecurityException {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        this.service = new PushService(push.vapidPublicKey(), push.vapidPrivateKey(), push.vapidSubject());
    }

    @Override
    public Result send(PushSubscription s, String payloadJson, Duration ttl) {
        try {
            var keys = s.getKeys();
            var notification = new Notification(s.getEndpoint(), keys.path("p256dh").asText(), keys.path("auth").asText(),
                    payloadJson.getBytes(StandardCharsets.UTF_8), (int) ttl.toSeconds());
            int code = service.sendAsync(notification).get(TIMEOUT_SECONDS, TimeUnit.SECONDS).getStatusLine().getStatusCode();
            if (code >= 200 && code < 300) {
                return Result.SENT;
            }
            return code == 404 || code == 410 ? Result.GONE : Result.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.FAILED;
        } catch (Exception e) { // gồm timeout, lỗi kết nối, khóa người nhận sai định dạng
            log.warn("Gửi push lỗi: {}", e.getClass().getSimpleName()); // endpoint là bí mật của người dùng nên không log
            return Result.FAILED;
        }
    }
}
