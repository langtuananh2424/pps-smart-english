package com.pps.smartenglish.retention.push;

import com.pps.smartenglish.retention.domain.PushSubscription;
import java.time.Duration;

/** Dùng khi chưa có khóa VAPID: không gửi gì, thông báo trong ứng dụng vẫn hoạt động. */
class NoopPushSender implements PushSender {

    @Override
    public Result send(PushSubscription subscription, String payloadJson, Duration ttl) {
        return Result.FAILED;
    }
}
