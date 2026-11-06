package com.pps.smartenglish.ai.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Báo cho API (mọi instance) biết job vừa xong qua Redis Pub/Sub, sau khi transaction đã commit. Mất tin hoặc Redis chết
 * không làm hỏng job: luồng SSE tự đọc lại DB theo nhịp keep-alive.
 * shortcut: worker poll DB mỗi 1 s thay vì LISTEN/NOTIFY của PostgreSQL; chuyển sang NOTIFY khi job > ~200/s.
 */
@Component
public class ResultNotifier {

    public static final String CHANNEL_PREFIX = "ai:result:";
    private static final Logger log = LoggerFactory.getLogger(ResultNotifier.class);

    private final StringRedisTemplate redis;

    ResultNotifier(StringRedisTemplate redis) {
        this.redis = redis;
    }

    void publish(long jobId) {
        try {
            redis.convertAndSend(CHANNEL_PREFIX + jobId, "done");
        } catch (RuntimeException e) {
            log.warn("Không publish được kết quả job {} lên Redis: {}", jobId, e.toString());
        }
    }
}
