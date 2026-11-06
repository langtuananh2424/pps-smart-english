package com.pps.smartenglish.retention.push;

import com.pps.smartenglish.retention.config.RetentionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Chọn PushSender theo cấu hình VAPID. Thiếu hoặc sai khóa chỉ log, không chặn khởi động. */
@Configuration
class PushConfig {

    private static final Logger log = LoggerFactory.getLogger(PushConfig.class);

    @Bean
    PushSender pushSender(RetentionProperties props) {
        var push = props.push();
        if (!push.vapidConfigured()) {
            log.info("Chưa cấu hình VAPID_PUBLIC_KEY/VAPID_PRIVATE_KEY: tắt Web Push, thông báo trong ứng dụng vẫn hoạt động");
            return new NoopPushSender();
        }
        try {
            return new WebPushSender(push);
        } catch (Exception e) {
            log.error("Khóa VAPID không dùng được ({}): tắt Web Push", e.getClass().getSimpleName());
            return new NoopPushSender();
        }
    }
}
