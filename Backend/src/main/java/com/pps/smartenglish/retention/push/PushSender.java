package com.pps.smartenglish.retention.push;

import com.pps.smartenglish.retention.domain.PushSubscription;
import java.time.Duration;

/** Strategy gửi một push tới một đầu nhận. Chạy ngoài transaction và trên executor có giới hạn của PushDispatcher. */
public interface PushSender {

    enum Result {
        SENT,
        /** 404/410: đầu nhận đã hết hiệu lực, phải xóa subscription. */
        GONE,
        FAILED
    }

    /** payloadJson chỉ gồm title, body, url (không có nội dung bài viết). */
    Result send(PushSubscription subscription, String payloadJson, Duration ttl);
}
