package com.pps.smartenglish.support;

import com.pps.smartenglish.retention.domain.PushSubscription;
import com.pps.smartenglish.retention.push.PushSender;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** PushSender giả ghi lại mọi lần gửi; kết quả theo endpoint đặt được (mặc định SENT). */
@TestConfiguration
public class FakePushConfig {

    public static class FakePushSender implements PushSender {

        public record Sent(String endpoint, String payload, Duration ttl) {}

        private final List<Sent> sent = new CopyOnWriteArrayList<>();
        private final Map<String, Result> results = new ConcurrentHashMap<>();

        @Override
        public Result send(PushSubscription s, String payloadJson, Duration ttl) {
            sent.add(new Sent(s.getEndpoint(), payloadJson, ttl));
            return results.getOrDefault(s.getEndpoint(), Result.SENT);
        }

        public void resultFor(String endpoint, Result r) {
            results.put(endpoint, r);
        }

        public List<Sent> sentTo(String endpoint) {
            return sent.stream().filter(s -> s.endpoint().equals(endpoint)).toList();
        }

        public void reset() {
            sent.clear();
            results.clear();
        }
    }

    @Bean
    @Primary
    FakePushSender fakePushSender() {
        return new FakePushSender();
    }
}
